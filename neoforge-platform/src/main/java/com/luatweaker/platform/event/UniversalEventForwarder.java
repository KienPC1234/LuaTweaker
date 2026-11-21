package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.entities.EntitiesLuaBinding;
import com.luatweaker.events.EventServiceImpl;
import com.luatweaker.interaction.InteractionLuaBinding;
import com.luatweaker.platform.LuaTweakerMod;
import com.luatweaker.platform.entity.NeoForgeEntityWrapper;
import com.luatweaker.platform.entity.NeoForgePlayerWrapper;
import com.luatweaker.platform.interaction.NeoForgeInteractableBlock;
import com.luatweaker.spawn.SpawnRuleServiceImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Replaces hardcoded @SubscribeEvent methods.
 * Dynamically hooks into the NeoForge event bus based on the EventAliasRegistry
 * and forwards each event as an engine-neutral dynamically wrapped Lua value.
 */
public final class UniversalEventForwarder {

    private static final String SPAWN_RULE_SERVICE_KEY = "SpawnRuleServiceImpl";
    private static final String EVENT_SERVICE_KEY = "Events";

    private UniversalEventForwarder() {
    }

    public static void registerAll() {
        for (Map.Entry<String, Class<? extends Event>> entry : EventAliasRegistry.getAllAliases().entrySet()) {
            // Register a dynamic listener on the NeoForge event bus
            registerListener(entry.getValue(), entry.getKey());
        }
    }

    private static <T extends Event> void registerListener(@NotNull Class<T> eventClass, @NotNull String alias) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, eventClass,
                (T event) -> handleEvent(alias, event));
    }

    private static void handleEvent(@NotNull String alias, @NotNull Event event) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) {
            return;
        }
        // Rule 5.4: Never forward client-side gameplay events to Lua!
        // Client-side execution of world/block/player gameplay logic causes deadlocks
        // and desyncs (e.g. calling ServerLevel methods from Render thread).
        if (isClientSideGameplayEvent(event)) {
            return;
        }
        applySpawnRuleHook(engine, event);
        dispatchToLuaListeners(engine, alias, event);
    }

    /**
     * Whether the event happens on a client level and therefore must stay out of Lua.
     *
     * <p>The three families below cover every gameplay event the alias registry exposes:
     * {@code PlayerInteractEvent}, {@code ItemEntityPickupEvent} and
     * {@code ProjectileImpactEvent} are all {@link EntityEvent} subtypes, so the entity's
     * level is the same level the more specific accessors return.</p>
     */
    private static boolean isClientSideGameplayEvent(@NotNull Event event) {
        return switch (event) {
            case BlockEvent blockEvent -> blockEvent.getLevel().isClientSide();
            case ExplosionEvent explosionEvent -> explosionEvent.getLevel().isClientSide();
            case EntityEvent entityEvent -> entityEvent.getEntity().level().isClientSide();
            default -> false;
        };
    }

    /** Global {@code SpawnRules:OnSpawnCheck} hook: returning {@code false} cancels the spawn. */
    private static void applySpawnRuleHook(@NotNull ILuaEngine engine, @NotNull Event event) {
        if (!(event instanceof FinalizeSpawnEvent finalizeSpawn)) {
            return;
        }
        if (!(LuaServiceRegistry.get(SPAWN_RULE_SERVICE_KEY) instanceof SpawnRuleServiceImpl spawnService)) {
            return;
        }
        ILuaValue hook = spawnService.getSpawnCheck();
        if (hook == null || !hook.isFunction()) {
            return;
        }
        ILuaValue result = engine.callFunction(hook,
                EntitiesLuaBinding.createEntityLuaTable(engine,
                        new NeoForgeEntityWrapper(finalizeSpawn.getEntity())),
                engine.wrapString(dimensionOf(finalizeSpawn.getEntity().level())),
                engine.wrapNumber(finalizeSpawn.getX()),
                engine.wrapNumber(finalizeSpawn.getY()),
                engine.wrapNumber(finalizeSpawn.getZ()));
        if (result != null && result.toJavaObject() instanceof Boolean allowed && !allowed) {
            finalizeSpawn.setSpawnCancelled(true);
        }
    }

    private static void dispatchToLuaListeners(@NotNull ILuaEngine engine, @NotNull String alias,
                                               @NotNull Event event) {
        if (!(LuaServiceRegistry.get(EVENT_SERVICE_KEY) instanceof EventServiceImpl eventService)) {
            return;
        }
        // Hot-path guard: skip DynamicJavaProxy wrapping + table building for
        // high-frequency events nobody listens to (same "no listeners" result
        // as the fire calls below, which return allowed=true when empty).
        if (!eventService.hasListeners(alias)) {
            return;
        }
        if (event instanceof PlayerInteractEvent.RightClickBlock rightClick) {
            dispatchRightClickBlock(engine, alias, eventService, rightClick);
            return;
        }
        if (event instanceof net.neoforged.neoforge.event.tick.PlayerTickEvent.Post playerTick) {
            dispatchPlayerTick(engine, alias, eventService, playerTick);
            return;
        }
        ILuaValue payload = EventPayloadBuilder.buildPayload(engine, event);
        if (payload == null) {
            payload = engine.wrapDynamicJavaObject(event);
        }
        if (!eventService.fireRawEvent(alias, payload)) {
            applyVeto(event);
        }
    }

    /**
     * Cancels the event, or — for events where cancellation means something different to vanilla —
     * applies the documented denial value instead ({@code BreakSpeed} -> 0 dig speed).
     */
    private static void applyVeto(@NotNull Event event) {
        if (event instanceof PlayerEvent.BreakSpeed breakSpeed) {
            breakSpeed.setNewSpeed(0.0F);
            return;
        }
        cancelEvent(event);
    }

    private static void dispatchRightClickBlock(@NotNull ILuaEngine engine, @NotNull String alias,
                                                @NotNull EventServiceImpl eventService,
                                                @NotNull PlayerInteractEvent.RightClickBlock rightClick) {
        ILuaTable table = buildRightClickBlockTable(engine, rightClick);
        if (!eventService.fireRawEvent(alias, table)) {
            rightClick.setCanceled(true);
        }
    }

    private static void dispatchPlayerTick(@NotNull ILuaEngine engine, @NotNull String alias,
                                           @NotNull EventServiceImpl eventService,
                                           @NotNull net.neoforged.neoforge.event.tick.PlayerTickEvent.Post tick) {
        ILuaTable table = engine.createTable();
        table.rawset("rawEvent", engine.wrapDynamicJavaObject(tick));
        NeoForgePlayerWrapper wrapper = new NeoForgePlayerWrapper(tick.getEntity());
        ILuaTable playerTable = EntitiesLuaBinding.createPlayerLuaTable(engine, wrapper);
        table.rawset("player", playerTable);
        table.rawset("Player", playerTable);
        table.rawset("entity", playerTable);
        table.rawset("Entity", playerTable);
        table.rawset("entityId", engine.wrapString(tick.getEntity().getUUID().toString()));
        table.rawset("EntityId", engine.wrapString(tick.getEntity().getUUID().toString()));
        ILuaValue movement = InteractionLuaBinding.wrapValue(engine, wrapper.getMovementData());
        table.rawset("movement", movement);
        table.rawset("Movement", movement);
        eventService.fireRawEvent(alias, table);
    }

    /**
     * Builds the {@code RightClickBlock} payload: {@code rawEvent}, optional {@code player},
     * optional {@code block}/{@code pos}/{@code level}/{@code face} and both the camelCase and
     * PascalCase spellings of every field.
     */
    private static @NotNull ILuaTable buildRightClickBlockTable(@NotNull ILuaEngine engine,
                                                                 @NotNull PlayerInteractEvent.RightClickBlock event) {
        ILuaTable table = engine.createTable();
        table.rawset("rawEvent", engine.wrapDynamicJavaObject(event));
        addPlayerFields(engine, table, event);
        if (event.getLevel() != null && event.getPos() != null) {
            addBlockFields(engine, table, event);
        }
        return table;
    }

    private static void addPlayerFields(@NotNull ILuaEngine engine, @NotNull ILuaTable table,
                                        @NotNull PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() == null) {
            return;
        }
        ILuaTable playerTable = EntitiesLuaBinding.createPlayerLuaTable(engine,
                new NeoForgePlayerWrapper(event.getEntity()));
        table.rawset("player", playerTable);
        table.rawset("Player", playerTable);
    }

    private static void addBlockFields(@NotNull ILuaEngine engine, @NotNull ILuaTable table,
                                       @NotNull PlayerInteractEvent.RightClickBlock event) {
        BlockPos pos = event.getPos();
        String dimension = dimensionOf(event.getLevel());
        ILuaTable blockTable = InteractionLuaBinding.wrapBlock(engine,
                new NeoForgeInteractableBlock(dimension, pos.getX(), pos.getY(), pos.getZ()));
        table.rawset("block", blockTable);
        table.rawset("Block", blockTable);
        table.rawset("pos", buildPosTable(engine, pos));
        table.rawset("Pos", buildPosTable(engine, pos));
        table.rawset("level", engine.wrapString(dimension));
        table.rawset("dimension", engine.wrapString(dimension));
        table.rawset("Dimension", engine.wrapString(dimension));
        if (event.getHitVec() == null || event.getHitVec().getDirection() == null) {
            return;
        }
        String sideName = event.getHitVec().getDirection().getName();
        table.rawset("face", engine.wrapString(sideName));
        table.rawset("Face", engine.wrapString(sideName));
        table.rawset("side", engine.wrapString(sideName));
        table.rawset("Side", engine.wrapString(sideName));
    }

    private static @NotNull ILuaTable buildPosTable(@NotNull ILuaEngine engine, @NotNull BlockPos pos) {
        ILuaTable posTable = engine.createTable();
        posTable.rawset("X", engine.wrapNumber(pos.getX()));
        posTable.rawset("Y", engine.wrapNumber(pos.getY()));
        posTable.rawset("Z", engine.wrapNumber(pos.getZ()));
        return posTable;
    }

    private static @NotNull String dimensionOf(@NotNull Level level) {
        return level.dimension().location().toString();
    }

    /** Applies the Lua {@code false} result to whichever cancel channel the event exposes. */
    private static void cancelEvent(@NotNull Event event) {
        if (event instanceof ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
            return;
        }
        if (event instanceof FinalizeSpawnEvent finalizeSpawn) {
            finalizeSpawn.setSpawnCancelled(true);
            return;
        }
        if (event instanceof ItemEntityPickupEvent.Pre pickupPre) {
            pickupPre.setCanPickup(TriState.FALSE);
        }
    }
}

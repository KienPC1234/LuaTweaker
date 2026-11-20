package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.platform.LuaTweakerMod;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Replaces hardcoded @SubscribeEvent methods.
 * Dynamically hooks into the NeoForge event bus based on the EventAliasRegistry
 * and forwards each event as an engine-neutral dynamically wrapped Lua value.
 */
public class UniversalEventForwarder {

    public static void registerAll() {
        Map<String, Class<? extends Event>> aliases = EventAliasRegistry.getAllAliases();

        for (Map.Entry<String, Class<? extends Event>> entry : aliases.entrySet()) {
            String alias = entry.getKey();
            Class<? extends Event> eventClass = entry.getValue();

            // Register a dynamic listener on the NeoForge event bus
            registerListener(eventClass, alias);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerListener(Class<? extends Event> eventClass, String alias) {
        Class rawClass = eventClass;
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, rawClass, (Consumer<Event>) event -> {
            handleEvent(alias, event);
        });
    }

    private static void handleEvent(String alias, Event event) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) return;

        // Rule 5.4: Never forward client-side gameplay events to Lua!
        // Client-side execution of world/block/player gameplay logic causes deadlocks
        // and desyncs (e.g. calling ServerLevel methods from Render thread).
        if (event instanceof net.neoforged.neoforge.event.entity.player.PlayerInteractEvent pie
                && pie.getLevel().isClientSide()) {
            return;
        }
        if (event instanceof net.neoforged.neoforge.event.level.BlockEvent be
                && be.getLevel().isClientSide()) {
            return;
        }
        if (event instanceof net.neoforged.neoforge.event.entity.EntityEvent ee
                && ee.getEntity().level().isClientSide()) {
            return;
        }

        if (LuaServiceRegistry.get("Events") instanceof com.luatweaker.events.EventServiceImpl eventService) {
            // Hot-path guard: skip DynamicJavaProxy wrapping + table building for
            // high-frequency events nobody listens to (same "no listeners" result
            // as the fire calls below, which return allowed=true when empty).
            if (!eventService.hasListeners(alias)) {
                return;
            }
            if (event instanceof net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock rc) {
                com.luatweaker.api.vm.ILuaTable table = engine.createTable();
                ILuaValue rawEvent = engine.wrapDynamicJavaObject(rc);
                table.rawset("rawEvent", rawEvent);
                if (rc.getEntity() != null) {
                    var playerWrapper = new com.luatweaker.platform.entity.NeoForgePlayerWrapper(rc.getEntity());
                    var pTable = com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, playerWrapper);
                    table.rawset("player", pTable);
                    table.rawset("Player", pTable);
                }
                if (rc.getLevel() != null && rc.getPos() != null) {
                    var pos = rc.getPos();
                    String dim = rc.getLevel().dimension().location().toString();
                    var blockWrapper = new com.luatweaker.platform.interaction.NeoForgeInteractableBlock(
                            dim, pos.getX(), pos.getY(), pos.getZ());
                    var bTable = com.luatweaker.interaction.InteractionLuaBinding.wrapBlock(engine, blockWrapper);
                    table.rawset("block", bTable);
                    table.rawset("Block", bTable);

                    com.luatweaker.api.vm.ILuaTable posTable = engine.createTable();
                    posTable.rawset("X", engine.wrapNumber(pos.getX()));
                    posTable.rawset("Y", engine.wrapNumber(pos.getY()));
                    posTable.rawset("Z", engine.wrapNumber(pos.getZ()));
                    table.rawset("pos", posTable);
                    table.rawset("Pos", posTable);

                    table.rawset("level", engine.wrapString(dim));
                    table.rawset("dimension", engine.wrapString(dim));
                    table.rawset("Dimension", engine.wrapString(dim));

                    if (rc.getHitVec() != null && rc.getHitVec().getDirection() != null) {
                        String sideName = rc.getHitVec().getDirection().getName();
                        table.rawset("face", engine.wrapString(sideName));
                        table.rawset("Face", engine.wrapString(sideName));
                        table.rawset("side", engine.wrapString(sideName));
                        table.rawset("Side", engine.wrapString(sideName));
                    }
                }
                boolean allowed = eventService.fireRawEvent(alias, table);
                if (!allowed) {
                    rc.setCanceled(true);
                }
                return;
            }

            ILuaValue wrapped = engine.wrapDynamicJavaObject(event);
            boolean allowed = eventService.fireRawEvent(alias, wrapped);
            if (!allowed && event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
                cancellable.setCanceled(true);
            }
        }
    }
}

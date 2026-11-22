package com.luatweaker.common.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.content.registrar.ItemHooks;
import com.luatweaker.common.event.payload.CombatPayloads;
import com.luatweaker.common.event.payload.EntityPayloads;
import com.luatweaker.common.event.payload.ExplosionPayloads;
import com.luatweaker.common.event.payload.LevelPayloads;
import com.luatweaker.common.event.payload.MiningPayloads;
import com.luatweaker.common.event.payload.PayloadEntities;
import com.luatweaker.common.event.payload.PlayerPayloads;
import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.ExplosionEvent;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Universal game bus event forwarder using Architectury API.
 * Bridges Minecraft game events into engine-neutral Lua tables via domain payload builders.
 */
public final class UniversalEventForwarder {

    private UniversalEventForwarder() {
    }

    public static void registerAll() {
        registerBlockEvents();
        registerEntityEvents();
        registerPlayerConnectionEvents();
        registerPlayerActionEvents();
        registerInteractionEvents();
        registerExplosionEvents();
        registerLifecycleEvents();
    }

    private static void registerBlockEvents() {
        BlockEvent.BREAK.register((level, pos, state, player, exp) -> {
            BlockExpDropper.dropExpIfNeeded(level, pos, state, player);
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null && pos != null) {
                int xp = exp != null ? exp.get() : 0;
                EventDispatcher.post("BlockBreak",
                        MiningPayloads.buildBreak(engine, level, pos, state, player, xp));
            }
            return EventResult.pass();
        });

        BlockEvent.PLACE.register((level, pos, state, entity) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null && pos != null) {
                EventDispatcher.post("BlockPlace",
                        MiningPayloads.buildPlace(engine, level, pos, state, entity));
            }
            return EventResult.pass();
        });
    }

    private static void registerEntityEvents() {
        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            if (source.getDirectEntity() instanceof Projectile projectile) {
                ProjectileHitHandler.handleProjectileHit(projectile, entity);
            }
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && entity instanceof LivingEntity living) {
                EventDispatcher.post("LivingHurt",
                        CombatPayloads.buildHurt(engine, living, source, amount));
            }
            return EventResult.pass();
        });

        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && entity instanceof LivingEntity living) {
                EventDispatcher.post("LivingDeath",
                        CombatPayloads.buildDeath(engine, living, source));
            }
            return EventResult.pass();
        });

        EntityEvent.ADD.register((entity, level) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && entity != null && level != null) {
                EventDispatcher.post("EntitySpawn",
                        EntityPayloads.buildSpawn(engine, entity, level));
            }
            return EventResult.pass();
        });
    }

    private static void registerPlayerConnectionEvents() {
        registerPlayerJoinQuit();
        registerPlayerLifecycleTransitions();
    }

    private static void registerPlayerJoinQuit() {
        PlayerEvent.PLAYER_JOIN.register(player -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null) {
                EventDispatcher.post("PlayerJoin", PlayerPayloads.buildJoin(engine, player));
            }
            if (player != null) {
                var loadErrors = com.luatweaker.core.mod.LuaModManager.getLoadErrors();
                for (String line : LuaTweakerCommon.buildLoadWarningLines(loadErrors, 5)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(line));
                }
            }
        });

        PlayerEvent.PLAYER_QUIT.register(player -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null) {
                EventDispatcher.post("PlayerQuit", PlayerPayloads.buildLeave(engine, player));
            }
        });
    }

    private static void registerPlayerLifecycleTransitions() {
        PlayerEvent.PLAYER_RESPAWN.register((player, conqueredEnd, reason) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null) {
                EventDispatcher.post("PlayerRespawn",
                        PlayerPayloads.buildRespawn(engine, player, conqueredEnd));
            }
        });

        PlayerEvent.CHANGE_DIMENSION.register((player, oldDim, newDim) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null) {
                EventDispatcher.post("PlayerChangedDimension",
                        PlayerPayloads.buildChangeDimension(engine, player, oldDim.location(), newDim.location()));
            }
        });

        PlayerEvent.PLAYER_ADVANCEMENT.register((player, advancement) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && advancement != null) {
                EventDispatcher.post("PlayerAdvancement",
                        PlayerPayloads.buildAdvancement(engine, player, advancement.id()));
            }
        });
    }

    private static void registerPlayerActionEvents() {
        registerPlayerCombatActions();
        registerPlayerItemActions();
        registerPlayerMenuActions();
    }

    private static void registerPlayerCombatActions() {
        PlayerEvent.ATTACK_ENTITY.register((player, level, target, hand, hitResult) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && target != null) {
                EventDispatcher.post("AttackEntity",
                        EntityPayloads.buildAttackEntity(engine, player, target));
            }
            return EventResult.pass();
        });
    }

    private static void registerPlayerItemActions() {
        PlayerEvent.PICKUP_ITEM_POST.register((player, itemEntity, stack) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && itemEntity != null) {
                EventDispatcher.post("ItemPickup",
                        EntityPayloads.buildPickup(engine, player, itemEntity, stack));
            }
        });

        PlayerEvent.DROP_ITEM.register((player, itemEntity) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && itemEntity != null) {
                EventDispatcher.post("ItemToss",
                        EntityPayloads.buildToss(engine, player, itemEntity));
            }
            return EventResult.pass();
        });
    }

    private static void registerPlayerMenuActions() {
        PlayerEvent.OPEN_MENU.register((player, menu) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && menu != null) {
                EventDispatcher.post("ContainerOpen",
                        PlayerPayloads.buildContainer(engine, player, menu, "open"));
            }
        });

        PlayerEvent.CLOSE_MENU.register((player, menu) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && menu != null) {
                EventDispatcher.post("ContainerClose",
                        PlayerPayloads.buildContainer(engine, player, menu, "close"));
            }
        });
    }

    private static void registerInteractionEvents() {
        InteractionEvent.LEFT_CLICK_BLOCK.register((player, hand, pos, direction) -> {
            if (player instanceof ServerPlayer sp) {
                ItemHooks.handleSwing(sp, sp.getMainHandItem());
            }
            return EventResult.pass();
        });

        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, direction) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && hand == InteractionHand.MAIN_HAND && pos != null) {
                EventDispatcher.post("RightClickBlock",
                        MiningPayloads.buildInteractBlock(engine, player, hand, player.level(), pos, direction));
            }
            return EventResult.pass();
        });

        InteractionEvent.RIGHT_CLICK_ITEM.register((player, hand) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && hand == InteractionHand.MAIN_HAND) {
                EventDispatcher.post("RightClickItem",
                        MiningPayloads.buildInteractItem(engine, player, hand, player.getItemInHand(hand)));
            }
            return CompoundEventResult.pass();
        });

        InteractionEvent.INTERACT_ENTITY.register((player, entity, hand) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && entity != null && hand == InteractionHand.MAIN_HAND) {
                EventDispatcher.post("InteractEntity",
                        EntityPayloads.buildInteractEntity(engine, player, entity, hand));
            }
            return EventResult.pass();
        });
    }

    private static void registerExplosionEvents() {
        ExplosionEvent.PRE.register((level, explosion) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null && explosion != null) {
                EventDispatcher.post("Explosion",
                        ExplosionPayloads.buildStart(engine, level, explosion));
            }
            return EventResult.pass();
        });

        ExplosionEvent.DETONATE.register((level, explosion, affectedEntities) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null && explosion != null) {
                EventDispatcher.post("ExplosionDetonate",
                        ExplosionPayloads.buildDetonate(engine, level, explosion, affectedEntities));
            }
        });
    }

    private static void registerLifecycleEvents() {
        TickEvent.SERVER_POST.register(server -> EventDispatcher.post("ServerTick", null));

        TickEvent.PLAYER_POST.register(player -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null) {
                var payload = engine.createTable();
                PayloadEntities.putPlayer(engine, payload, player);
                EventDispatcher.post("PlayerTick", payload);
            }
        });

        LifecycleEvent.SERVER_LEVEL_LOAD.register(level -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null) {
                EventDispatcher.post("LevelLoad", LevelPayloads.buildLoad(engine, level));
            }
        });

        LifecycleEvent.SERVER_LEVEL_UNLOAD.register(level -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null) {
                EventDispatcher.post("LevelUnload", LevelPayloads.buildUnload(engine, level));
            }
        });
    }
}

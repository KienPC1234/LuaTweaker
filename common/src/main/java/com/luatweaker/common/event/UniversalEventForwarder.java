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
import com.luatweaker.common.modsync.ServerModSync;
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
 * Bridges Minecraft game events into engine-neutral Lua tables and honors Lua cancellation.
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
        CommonExtraEventForwarder.registerAll();
    }

    private static void registerBlockEvents() {
        registerBlockBreakEvent();
        registerBlockPlaceEvent();
    }

    private static void registerBlockBreakEvent() {
        BlockEvent.BREAK.register((level, pos, state, player, exp) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null && pos != null) {
                int xp = exp != null ? exp.get() : 0;
                boolean allowed = EventDispatcher.postCancellable("BlockBreak",
                        () -> MiningPayloads.buildBreak(engine, level, pos, state, player, xp));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            BlockExpDropper.dropExpIfNeeded(level, pos, state, player);
            return EventResult.pass();
        });
    }

    private static void registerBlockPlaceEvent() {
        BlockEvent.PLACE.register((level, pos, state, entity) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null && pos != null) {
                boolean allowed = EventDispatcher.postCancellable("BlockPlace",
                        () -> MiningPayloads.buildPlace(engine, level, pos, state, entity));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });
    }

    private static void registerEntityEvents() {
        registerEntityCombatEvents();
        registerEntitySpawnEvent();
    }

    private static void registerEntityCombatEvents() {
        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            if (source.getDirectEntity() instanceof Projectile projectile) {
                ProjectileHitHandler.handleProjectileHit(projectile, entity);
            }
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && entity instanceof LivingEntity living) {
                boolean allowed = EventDispatcher.postCancellable("LivingHurt",
                        () -> CombatPayloads.buildHurt(engine, living, source, amount));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });

        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && entity instanceof LivingEntity living) {
                boolean allowed = EventDispatcher.postCancellable("LivingDeath",
                        () -> CombatPayloads.buildDeath(engine, living, source));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });
    }

    private static void registerEntitySpawnEvent() {
        EntityEvent.ADD.register((entity, level) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && entity != null && level != null) {
                boolean allowed = EventDispatcher.postCancellable("EntitySpawn",
                        () -> EntityPayloads.buildSpawn(engine, entity, level));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
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
                EventDispatcher.postLazy("PlayerJoin", () -> PlayerPayloads.buildJoin(engine, player));
            }
            if (player != null) {
                ServerModSync.onPlayerJoin(player);
                var loadErrors = com.luatweaker.core.mod.LuaModManager.getLoadErrors();
                for (String line : LuaTweakerCommon.buildLoadWarningLines(loadErrors, 5)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(line));
                }
            }
        });

        PlayerEvent.PLAYER_QUIT.register(player -> {
            if (player != null) {
                ServerModSync.onPlayerQuit(player);
            }
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null) {
                EventDispatcher.postLazy("PlayerQuit", () -> PlayerPayloads.buildLeave(engine, player));
            }
        });
    }

    private static void registerPlayerLifecycleTransitions() {
        PlayerEvent.PLAYER_RESPAWN.register((player, conqueredEnd, reason) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null) {
                EventDispatcher.postLazy("PlayerRespawn",
                        () -> PlayerPayloads.buildRespawn(engine, player, conqueredEnd));
            }
        });

        PlayerEvent.CHANGE_DIMENSION.register((player, oldDim, newDim) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null) {
                EventDispatcher.postLazy("PlayerChangedDimension",
                        () -> PlayerPayloads.buildChangeDimension(engine, player, oldDim.location(), newDim.location()));
            }
        });

        PlayerEvent.PLAYER_ADVANCEMENT.register((player, advancement) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && advancement != null) {
                EventDispatcher.postLazy("PlayerAdvancement",
                        () -> PlayerPayloads.buildAdvancement(engine, player, advancement.id()));
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
                boolean allowed = EventDispatcher.postCancellable("AttackEntity",
                        () -> EntityPayloads.buildAttackEntity(engine, player, target));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });
    }

    private static void registerPlayerItemActions() {
        PlayerEvent.PICKUP_ITEM_POST.register((player, itemEntity, stack) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && itemEntity != null) {
                EventDispatcher.postLazy("ItemPickup",
                        () -> EntityPayloads.buildPickup(engine, player, itemEntity, stack));
            }
        });

        PlayerEvent.DROP_ITEM.register((player, itemEntity) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && itemEntity != null) {
                boolean allowed = EventDispatcher.postCancellable("ItemToss",
                        () -> EntityPayloads.buildToss(engine, player, itemEntity));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });
    }

    private static void registerPlayerMenuActions() {
        PlayerEvent.OPEN_MENU.register((player, menu) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && menu != null) {
                EventDispatcher.postLazy("ContainerOpen",
                        () -> PlayerPayloads.buildContainer(engine, player, menu, "open"));
            }
        });

        PlayerEvent.CLOSE_MENU.register((player, menu) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && menu != null) {
                EventDispatcher.postLazy("ContainerClose",
                        () -> PlayerPayloads.buildContainer(engine, player, menu, "close"));
            }
        });
    }

    private static void registerInteractionEvents() {
        registerBlockInteractions();
        registerItemAndEntityInteractions();
    }

    private static void registerBlockInteractions() {
        InteractionEvent.LEFT_CLICK_BLOCK.register((player, hand, pos, direction) -> {
            if (player instanceof ServerPlayer sp) {
                ItemHooks.handleSwing(sp, sp.getMainHandItem());
            }
            return EventResult.pass();
        });

        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, direction) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && hand == InteractionHand.MAIN_HAND && pos != null) {
                boolean allowed = EventDispatcher.postCancellable("RightClickBlock",
                        () -> MiningPayloads.buildInteractBlock(engine, player, hand, player.level(), pos, direction));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });
    }

    private static void registerItemAndEntityInteractions() {
        InteractionEvent.RIGHT_CLICK_ITEM.register((player, hand) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && hand == InteractionHand.MAIN_HAND) {
                boolean allowed = EventDispatcher.postCancellable("RightClickItem",
                        () -> MiningPayloads.buildInteractItem(engine, player, hand, player.getItemInHand(hand)));
                if (!allowed) {
                    return CompoundEventResult.interruptFalse(player.getItemInHand(hand));
                }
            }
            return CompoundEventResult.pass();
        });

        InteractionEvent.INTERACT_ENTITY.register((player, entity, hand) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && entity != null && hand == InteractionHand.MAIN_HAND) {
                boolean allowed = EventDispatcher.postCancellable("InteractEntity",
                        () -> EntityPayloads.buildInteractEntity(engine, player, entity, hand));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });
    }

    private static void registerExplosionEvents() {
        ExplosionEvent.PRE.register((level, explosion) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null && explosion != null) {
                boolean allowed = EventDispatcher.postCancellable("Explosion",
                        () -> ExplosionPayloads.buildStart(engine, level, explosion));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });

        ExplosionEvent.DETONATE.register((level, explosion, affectedEntities) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null && explosion != null) {
                EventDispatcher.postLazy("ExplosionDetonate",
                        () -> ExplosionPayloads.buildDetonate(engine, level, explosion, affectedEntities));
            }
        });
    }

    private static void registerLifecycleEvents() {
        TickEvent.SERVER_POST.register(server -> EventDispatcher.post("ServerTick", null));

        TickEvent.PLAYER_POST.register(player -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null) {
                EventDispatcher.postLazy("PlayerTick", () -> {
                    var payload = engine.createTable();
                    PayloadEntities.putPlayer(engine, payload, player);
                    return payload;
                });
            }
        });

        LifecycleEvent.SERVER_LEVEL_LOAD.register(level -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null) {
                EventDispatcher.postLazy("LevelLoad", () -> LevelPayloads.buildLoad(engine, level));
            }
        });

        LifecycleEvent.SERVER_LEVEL_UNLOAD.register(level -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null) {
                EventDispatcher.postLazy("LevelUnload", () -> LevelPayloads.buildUnload(engine, level));
            }
        });
    }
}

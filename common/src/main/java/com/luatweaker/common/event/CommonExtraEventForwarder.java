package com.luatweaker.common.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.event.payload.ExtraEventPayloads;
import com.luatweaker.common.event.payload.LevelPayloads;
import com.luatweaker.common.event.payload.PlayerActionPayloads;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.ChatEvent;
import dev.architectury.event.events.common.ChunkEvent;
import dev.architectury.event.events.common.CommandPerformEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.LightningEvent;
import dev.architectury.event.events.common.LootEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Registers the remaining cross-loader Architectury common events and reload listeners.
 */
final class CommonExtraEventForwarder {

    private static final String CHAT_DECORATE = "ChatDecorate";
    private static final String CHUNK_LOAD = "ChunkLoad";
    private static final String CHUNK_SAVE = "ChunkSave";
    private static final String ENTITY_ENTER_SECTION = "EntityEnterSection";

    private CommonExtraEventForwarder() {
    }

    static void registerAll() {
        registerChatAndCommandEvents();
        registerChunkAndLootEvents();
        registerLightningAndBlockEvents();
        registerEntityEvents();
        registerEntitySectionEvent();
        registerPlayerCraftSmeltEvents();
        registerPlayerPickupBucketEvents();
        registerLifecycleAndReloadEvents();
    }

    private static void registerChatAndCommandEvents() {
        ChatEvent.DECORATE.register((player, component) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && EventDispatcher.hasListenersFor(CHAT_DECORATE)) {
                EventDispatcher.postLazy(CHAT_DECORATE,
                        () -> ExtraEventPayloads.buildChat(engine, player, component.get(), component));
            }
        });

        ChatEvent.RECEIVED.register((player, component) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null) {
                boolean allowed = EventDispatcher.postCancellable("ChatReceived",
                        () -> ExtraEventPayloads.buildChat(engine, player, component, null));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });

        CommandPerformEvent.EVENT.register(event -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && event.getResults() != null) {
                boolean allowed = EventDispatcher.postCancellable("CommandPerform",
                        () -> ExtraEventPayloads.buildCommandPerform(engine, event.getResults()));
                if (!allowed) {
                    event.setThrowable(new SimpleCommandExceptionType(
                            Component.literal("Command cancelled")).create());
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });
    }

    private static void registerChunkAndLootEvents() {
        ChunkEvent.LOAD_DATA.register((chunk, level, nbt) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && chunk != null && EventDispatcher.hasListenersFor(CHUNK_LOAD)) {
                var pos = chunk.getPos();
                EventDispatcher.postLazy(CHUNK_LOAD,
                        () -> ExtraEventPayloads.buildChunk(engine, level, pos.x, pos.z));
            }
        });

        ChunkEvent.SAVE_DATA.register((chunk, level, nbt) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && chunk != null && EventDispatcher.hasListenersFor(CHUNK_SAVE)) {
                var pos = chunk.getPos();
                EventDispatcher.postLazy(CHUNK_SAVE,
                        () -> ExtraEventPayloads.buildChunk(engine, level, pos.x, pos.z));
            }
        });

        LootEvent.MODIFY_LOOT_TABLE.register((key, context, builtin) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && key != null && context != null) {
                EventDispatcher.postLazy("LootTableModify",
                        () -> PlayerActionPayloads.buildLootModify(engine, key.location(), context, builtin));
            }
        });
    }

    private static void registerLightningAndBlockEvents() {
        LightningEvent.STRIKE.register((bolt, level, pos, toStrike) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null && pos != null) {
                EventDispatcher.postLazy("LightningStrike",
                        () -> ExtraEventPayloads.buildLightning(engine, level, pos, toStrike));
            }
        });

        BlockEvent.FALLING_LAND.register((level, pos, fallState, landOn, entity) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null && pos != null) {
                EventDispatcher.postLazy("BlockFallingLand",
                        () -> ExtraEventPayloads.buildFallingLand(engine, level, pos, fallState, landOn, entity));
            }
        });

        InteractionEvent.FARMLAND_TRAMPLE.register((world, pos, state, distance, entity) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && world != null && pos != null) {
                boolean allowed = EventDispatcher.postCancellable("FarmlandTrample",
                        () -> ExtraEventPayloads.buildFarmlandTrample(engine, world, pos, distance, entity));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });
    }

    private static void registerEntityEvents() {
        EntityEvent.LIVING_CHECK_SPAWN.register((entity, world, x, y, z, type, spawner) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && entity != null && world != null) {
                boolean allowed = EventDispatcher.postCancellable("EntitySpawnCheck",
                        () -> ExtraEventPayloads.buildSpawnCheck(engine, entity, world, x, y, z, type));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });

        EntityEvent.ANIMAL_TAME.register((animal, player) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && animal != null && player != null) {
                boolean allowed = EventDispatcher.postCancellable("AnimalTame",
                        () -> ExtraEventPayloads.buildAnimalTame(engine, animal, player));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });
    }

    private static void registerEntitySectionEvent() {
        EntityEvent.ENTER_SECTION.register((entity, sx, sy, sz, px, py, pz) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && entity != null && EventDispatcher.hasListenersFor(ENTITY_ENTER_SECTION)) {
                EventDispatcher.postLazy(ENTITY_ENTER_SECTION,
                        () -> ExtraEventPayloads.buildEnterSection(engine, entity, sx, sy, sz, px, py, pz));
            }
        });
    }

    private static void registerPlayerCraftSmeltEvents() {
        PlayerEvent.CRAFT_ITEM.register((player, constructed, inventory) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && constructed != null) {
                EventDispatcher.postLazy("ItemCrafted",
                        () -> PlayerActionPayloads.buildItemAction(engine, player, constructed));
            }
        });

        PlayerEvent.SMELT_ITEM.register((player, smelted) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && smelted != null) {
                EventDispatcher.postLazy("ItemSmelted",
                        () -> PlayerActionPayloads.buildItemAction(engine, player, smelted));
            }
        });

        PlayerEvent.PLAYER_CLONE.register((oldPlayer, newPlayer, wonGame) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && oldPlayer != null && newPlayer != null) {
                EventDispatcher.postLazy("PlayerClone",
                        () -> PlayerActionPayloads.buildClone(engine, oldPlayer, newPlayer, wonGame));
            }
        });
    }

    private static void registerPlayerPickupBucketEvents() {
        PlayerEvent.PICKUP_ITEM_PRE.register((player, entity, stack) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && stack != null) {
                boolean allowed = EventDispatcher.postCancellable("ItemPickupPre",
                        () -> PlayerActionPayloads.buildItemAction(engine, player, stack));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });

        PlayerEvent.FILL_BUCKET.register((player, level, stack, target) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && stack != null) {
                boolean allowed = EventDispatcher.postCancellable("FillBucket",
                        () -> PlayerActionPayloads.buildItemAction(engine, player, stack));
                if (!allowed) {
                    return CompoundEventResult.interruptFalse(stack);
                }
            }
            return CompoundEventResult.pass();
        });
    }

    private static void registerLifecycleAndReloadEvents() {
        LifecycleEvent.SERVER_BEFORE_START.register(server -> EventDispatcher.post("ServerBeforeStart", null));
        LifecycleEvent.SERVER_STARTING.register(server -> EventDispatcher.post("ServerStarting", null));
        LifecycleEvent.SERVER_STARTED.register(server -> EventDispatcher.post("ServerStarted", null));
        LifecycleEvent.SERVER_STOPPING.register(server -> EventDispatcher.post("ServerStopping", null));
        LifecycleEvent.SERVER_STOPPED.register(server -> EventDispatcher.post("ServerStopped", null));
        LifecycleEvent.SERVER_LEVEL_SAVE.register(level -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null) {
                EventDispatcher.postLazy("LevelSave", () -> LevelPayloads.buildLoad(engine, level));
            }
        });
        ReloadListenerRegistry.register(PackType.SERVER_DATA,
                (ResourceManagerReloadListener) rm -> EventDispatcher.post("DataPackReload", null),
                ResourceLocation.fromNamespaceAndPath(LuaTweakerCommon.MOD_ID, "server_reload_events"));
    }
}

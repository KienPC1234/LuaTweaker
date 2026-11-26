package com.luatweaker.common.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.event.payload.ExtraEventPayloads;
import com.luatweaker.common.event.payload.PlayerActionPayloads;
import com.luatweaker.common.support.MinecraftBootstrapExtension;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.events.EventLuaBinding;
import com.luatweaker.events.EventServiceImpl;
import dev.architectury.event.events.common.ChatEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies payload construction, loot pool additions, chat component mutation, and event alias dispatch
 * for extended Architectury event bridges.
 */
@ExtendWith(MinecraftBootstrapExtension.class)
class ExtraEventPayloadsTest {

    private ILuaEngine engine;

    @AfterEach
    void tearDown() {
        LuaTweakerCommon.setActiveEngine(null);
        LuaServiceRegistry.clear();
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    void chatMutatorChunkPayloadsAndAliasDispatchWorkInLua() {
        engine = LuaEngineFactory.create(false);
        LuaTweakerCommon.setActiveEngine(engine);
        EventServiceImpl events = new EventServiceImpl(engine);
        LuaServiceRegistry.register("Events", events);
        EventLuaBinding.registerBindings(engine);

        AtomicReference<Component> updatedComponent = new AtomicReference<>(Component.literal("hello"));
        ChatEvent.ChatComponent mutator = new ChatEvent.ChatComponent() {
            @Override
            public Component get() {
                return updatedComponent.get();
            }

            @Override
            public void set(Component component) {
                updatedComponent.set(component);
            }
        };

        ILuaTable chatPayload = ExtraEventPayloads.buildChat(engine, null, updatedComponent.get(), mutator);
        ILuaTable chunkPayload = ExtraEventPayloads.buildChunk(engine, null, 3, -4);
        engine.getGlobalEnvironment().rawset("testChatPayload", chatPayload);
        engine.getGlobalEnvironment().rawset("testChunkPayload", chunkPayload);

        engine.executeStringOrThrow("""
                Events:Listen("CommandExecute", function(ev)
                    return ev.Command ~= "ban"
                end)
                assert(testChatPayload.Message == "hello")
                testChatPayload:SetMessage("decorated")
                assert(testChunkPayload.ChunkX == 3 and testChunkPayload.ChunkZ == -4)
                """, "extra_event_payloads_test");

        boolean allowedHelp = EventDispatcher.postCancellable("CommandPerform", () -> {
            ILuaTable tbl = engine.createTable();
            tbl.rawset("Command", engine.wrapString("help"));
            return tbl;
        });
        boolean allowedBan = EventDispatcher.postCancellable("CommandPerform", () -> {
            ILuaTable tbl = engine.createTable();
            tbl.rawset("Command", engine.wrapString("ban"));
            return tbl;
        });

        assertAll("chat decoration and alias cancellation",
                () -> assertEquals("decorated", updatedComponent.get().getString(),
                        "chat mutator must update component text"),
                () -> assertTrue(EventDispatcher.hasListenersFor("CommandPerform"),
                        "alias listener must activate hasListenersFor guard"),
                () -> assertTrue(allowedHelp, "non-blocked command must be allowed"),
                () -> assertFalse(allowedBan, "alias listener returning false must cancel CommandPerform"));
    }

    @Test
    void lootModifyPayloadAddsValidPoolsAndRejectsInvalidItems() {
        engine = LuaEngineFactory.create(false);
        List<LootPool> addedPools = new ArrayList<>();
        ILuaTable lootPayload = PlayerActionPayloads.buildLootModify(
                engine,
                ResourceLocation.fromNamespaceAndPath("minecraft", "chests/simple_dungeon"),
                pool -> addedPools.add(pool.build()),
                true);
        engine.getGlobalEnvironment().rawset("testLootPayload", lootPayload);

        engine.executeStringOrThrow("""
                assert(testLootPayload.Id == "minecraft:chests/simple_dungeon")
                assert(testLootPayload.Builtin == true)
                addedValid = testLootPayload:AddPool("minecraft:diamond", 2, 5, 1, 2)
                addedInvalid = testLootPayload:AddPool("minecraft:does_not_exist", 1, 1)
                addedBlank = testLootPayload:AddPool("", 1, 1)
                """, "loot_modify_payload_test");

        assertAll("loot table pool modifier",
                () -> assertTrue(engine.getGlobalEnvironment().rawget("addedValid").asBoolean(),
                        "valid item must add loot pool"),
                () -> assertFalse(engine.getGlobalEnvironment().rawget("addedInvalid").asBoolean(),
                        "unknown item must fail safely"),
                () -> assertFalse(engine.getGlobalEnvironment().rawget("addedBlank").asBoolean(),
                        "blank item id must fail safely"),
                () -> assertEquals(1, addedPools.size(), "only valid call should enqueue one LootPool"));
    }
}

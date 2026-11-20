package com.luatweaker.interaction;

import com.luatweaker.api.pal.IPlatformInteraction;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NewPrimitivesBindingTest {

    private static final class MockInteraction extends BaseMockPlatformInteraction {
        final List<String> calls = new ArrayList<>();

        @Override
        public @NotNull List<Map<String, Object>> findBlocks(
                @NotNull String dimension, int x, int y, int z, int radius, @NotNull String filter, int maxResults) {
            calls.add("findBlocks " + x + "," + y + "," + z + " r=" + radius + " filter=" + filter + " max=" + maxResults);
            return List.of(Map.of("X", 10, "Y", 64, "Z", 20, "Id", "minecraft:diamond_ore", "Distance", 5.5));
        }

        @Override
        public boolean setEnergy(@NotNull String dimension, int x, int y, int z, int amount) {
            calls.add("setEnergy " + x + "," + y + "," + z + " amount=" + amount);
            return true;
        }

        @Override
        public int produceEnergy(@NotNull String dimension, int x, int y, int z, int amount) {
            calls.add("produceEnergy " + x + "," + y + "," + z + " amount=" + amount);
            return amount;
        }

        @Override
        public boolean setProgress(@NotNull String dimension, int x, int y, int z, float progress) {
            calls.add("setProgress " + x + "," + y + "," + z + " prog=" + progress);
            return true;
        }

        @Override
        public float getProgress(@NotNull String dimension, int x, int y, int z) {
            calls.add("getProgress " + x + "," + y + "," + z);
            return 0.75f;
        }
    }

    private MockInteraction interaction;
    private IPlatformInteraction previousInteraction;

    @BeforeEach
    public void setup() {
        interaction = new MockInteraction();
        previousInteraction = Platform.isInitialized() ? Platform.getInteraction() : null;
        Platform.setInteraction(interaction);
    }

    @AfterEach
    public void teardown() {
        if (previousInteraction != null) {
            Platform.setInteraction(previousInteraction);
        }
    }

    @Test
    public void testFindBlocks_WithCoordinatesAndPosTable() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        try {
            InteractionLuaBinding.registerBindings(engine);
            engine.executeStringOrThrow("""
                local res1 = World:FindBlocks(100, 60, 200, 16, "#c:ores", 10)
                assert(#res1 == 1, "must find 1 block")
            """, "test_find_1");
            assertEquals(1, interaction.calls.size());
            assertEquals("findBlocks 100,60,200 r=16 filter=#c:ores max=10", interaction.calls.get(0));

            engine.executeStringOrThrow("""
                local res2 = World:FindBlocks({ X = 50, Y = 70, Z = 90 }, 24, "crystal_ore")
                assert(#res2 == 1, "must find 1 block")
            """, "test_find_2");
            assertEquals(2, interaction.calls.size());
            assertEquals("findBlocks 50,70,90 r=24 filter=crystal_ore max=64", interaction.calls.get(1));
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void testSetEnergy_AndProduceEnergy() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        try {
            InteractionLuaBinding.registerBindings(engine);
            engine.executeStringOrThrow("""
                local ok = World:SetEnergy(10, 20, 30, 5000)
                assert(ok == true, "setEnergy should return true")
            """, "test_set_energy");
            assertEquals("setEnergy 10,20,30 amount=5000", interaction.calls.get(0));

            engine.executeStringOrThrow("""
                local actual = World:ProduceEnergy({ X = 10, Y = 20, Z = 30 }, 250)
                assert(actual == 250, "produceEnergy should return amount")
            """, "test_produce_energy");
            assertEquals("produceEnergy 10,20,30 amount=250", interaction.calls.get(1));
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void testProgress_GetterAndSetter() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        try {
            InteractionLuaBinding.registerBindings(engine);
            engine.executeStringOrThrow("""
                local ok = World:SetProgress(1, 2, 3, 0.45)
                assert(ok == true, "setProgress should return true")
                local p = World:GetProgress({ X = 1, Y = 2, Z = 3 })
                assert(p > 0.74 and p < 0.76, "getProgress should return 0.75")
            """, "test_progress");
            assertEquals("setProgress 1,2,3 prog=0.45", interaction.calls.get(0));
            assertEquals("getProgress 1,2,3", interaction.calls.get(1));
        } finally {
            engine.shutdown();
        }
    }
}

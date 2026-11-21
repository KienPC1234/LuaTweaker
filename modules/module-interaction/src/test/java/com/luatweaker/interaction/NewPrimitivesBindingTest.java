package com.luatweaker.interaction;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the multiblock-era World primitives added after the first interaction binding: FindBlocks
 * (raw coordinates and position table, max default), SetEnergy/ProduceEnergy, and the
 * progress getter/setter.
 */
class NewPrimitivesBindingTest {

    private static final String ORE_FILTER = "#c:ores";

    private MockInteraction interaction;
    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);
        interaction = new MockInteraction();
        Platform.setInteraction(interaction);
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
        Platform.setInteraction(null);
    }

    @Test
    void findBlocksAcceptsRawCoordinatesThenAPositionTable() {
        engine.executeStringOrThrow("""
                local ore = World:FindBlocks(100, 60, 200, 16, "#c:ores", 10)
                assert(#ore == 1, "must find exactly one ore block")
                local crystal = World:FindBlocks({ X = 50, Y = 70, Z = 90 }, 24, "crystal_ore")
                assert(#crystal == 1, "must find exactly one crystal ore block")
                """, "test_find_blocks");

        assertEquals(List.of(
                        "findBlocks 100,60,200 r=16 filter=" + ORE_FILTER + " max=10",
                        "findBlocks 50,70,90 r=24 filter=crystal_ore max=64"),
                interaction.calls,
                "FindBlocks must forward explicit coordinates and a position table, defaulting max to 64");
    }

    @Test
    void setEnergyForwardsTheRequestedAmount() {
        engine.executeStringOrThrow("""
                local ok = World:SetEnergy(10, 20, 30, 5000)
                assert(ok == true, "setEnergy should return true")
                """, "test_set_energy");

        assertEquals("setEnergy 10,20,30 amount=5000", interaction.calls.get(0),
                "SetEnergy must forward the energy amount to the platform");
    }

    @Test
    void produceEnergyReturnsTheProducedAmount() {
        engine.executeStringOrThrow("""
                local actual = World:ProduceEnergy({ X = 10, Y = 20, Z = 30 }, 250)
                assert(actual == 250, "produceEnergy should return the produced amount")
                """, "test_produce_energy");

        assertEquals("produceEnergy 10,20,30 amount=250", interaction.calls.get(0),
                "ProduceEnergy must forward the amount and return the platform result");
    }

    @Test
    void setProgressForwardsTheFraction() {
        engine.executeStringOrThrow("""
                local ok = World:SetProgress(1, 2, 3, 0.45)
                assert(ok == true, "setProgress should return true")
                """, "test_set_progress");

        assertEquals("setProgress 1,2,3 prog=0.45", interaction.calls.get(0),
                "SetProgress must forward the progress fraction to the platform");
    }

    @Test
    void progressReadsThePositionTable() {
        engine.executeStringOrThrow("""
                local progress = World:GetProgress({ X = 1, Y = 2, Z = 3 })
                assert(progress > 0.74 and progress < 0.76, "getProgress should return 0.75")
                """, "test_get_progress");

        assertEquals("getProgress 1,2,3", interaction.calls.get(0),
                "GetProgress must resolve the position table before querying the platform");
    }

    /** Records every call as {@code name x,y,z details} so assertions stay exact. */
    private static final class MockInteraction implements MockPlatformInteraction {
        private final List<String> calls = new ArrayList<>();

        @NotNull
        @Override
        public List<Map<String, Object>> findBlocks(@NotNull String dimension, int x, int y, int z,
                                                    int radius, @NotNull String filter,
                                                    int maxResults) {
            calls.add("findBlocks " + x + "," + y + "," + z + " r=" + radius
                    + " filter=" + filter + " max=" + maxResults);
            return List.of(Map.of("X", 10, "Y", 64, "Z", 20, "Id", "minecraft:diamond_ore",
                    "Distance", 5.5));
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
}

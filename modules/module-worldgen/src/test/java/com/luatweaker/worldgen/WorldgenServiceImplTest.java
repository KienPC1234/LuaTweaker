package com.luatweaker.worldgen;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class WorldgenServiceImplTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void testAddOre_StoresEntry() {
        service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 10);

        List<WorldgenServiceImpl.OreEntry> ores = service.getPendingOres();
        assertEquals(1, ores.size());

        WorldgenServiceImpl.OreEntry entry = ores.get(0);
        assertEquals("mymod:ruby_ore", entry.blockId());
        assertEquals("minecraft:overworld", entry.dimension());
        assertEquals(-64, entry.minHeight());
        assertEquals(32, entry.maxHeight());
        assertEquals(8, entry.clusterSize());
        assertEquals(10, entry.frequency());
        assertTrue(entry.biomes().isEmpty());
    }

    @Test
    void testAddOreBiomeFiltered_StoresBiomes() {
        service.addOreBiomeFiltered("mymod:ruby_ore", "minecraft:overworld",
                -64, 32, 8, 10, new String[]{"minecraft:plains", "minecraft:forest"});

        WorldgenServiceImpl.OreEntry entry = service.getPendingOres().get(0);
        assertEquals(2, entry.biomes().size());
        assertEquals("minecraft:plains", entry.biomes().get(0));
    }

    @Test
    void testAddOreBiomeFiltered_RejectsEmptyBiomes() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addOreBiomeFiltered("mymod:ruby_ore", "minecraft:overworld",
                -64, 32, 8, 10, new String[]{}));
    }

    @Test
    void testAddOre_RejectsBlankBlockId() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addOre("", "minecraft:overworld", -64, 32, 8, 10));
    }

    @Test
    void testAddOre_RejectsInvalidHeightRange() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addOre("mymod:ruby_ore", "minecraft:overworld", 32, -64, 8, 10));
    }

    @Test
    void testAddOre_RejectsZeroClusterSize() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 0, 10));
    }

    @Test
    void testAddOre_RejectsZeroFrequency() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 0));
    }

    @Test
    void testAddVegetation_StoresEntry() {
        service.addVegetation("mymod:crystal_flower", 0.1, new String[]{"minecraft:plains"});

        List<WorldgenServiceImpl.VegetationEntry> veg = service.getPendingVegetation();
        assertEquals(1, veg.size());
        assertEquals("mymod:crystal_flower", veg.get(0).blockId());
        assertEquals(0.1, veg.get(0).chance());
    }

    @Test
    void testAddVegetation_RejectsInvalidChance() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addVegetation("mymod:flower", 1.5, new String[]{"minecraft:plains"}));
    }

    @Test
    void testAddVegetation_RejectsEmptyBiomes() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addVegetation("mymod:flower", 0.5, new String[]{}));
    }

    @Test
    void testRemoveOre_StoresEntry() {
        service.removeOre("minecraft:coal_ore", "minecraft:overworld");

        List<WorldgenServiceImpl.OreRemovalEntry> removals = service.getPendingRemovals();
        assertEquals(1, removals.size());
        assertEquals("minecraft:coal_ore", removals.get(0).blockId());
    }

    @Test
    void testRemoveOre_RejectsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
            service.removeOre("", "minecraft:overworld"));
    }

    @Test
    void testGetModifications_ReturnsCorrectSummary() {
        service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 10);
        service.addOre("mymod:sapphire_ore", "minecraft:overworld", -64, 16, 6, 5);
        service.addVegetation("mymod:flower", 0.1, new String[]{"minecraft:plains"});
        service.removeOre("minecraft:coal_ore", "minecraft:overworld");

        Map<String, Object> summary = service.getModifications();
        assertEquals(2, summary.get("ores"));
        assertEquals(1, summary.get("vegetation"));
        assertEquals(1, summary.get("removals"));
    }

    @Test
    void testClearAll_RemovesEverything() {
        service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 10);
        service.addVegetation("mymod:flower", 0.1, new String[]{"minecraft:plains"});
        service.removeOre("minecraft:coal_ore", "minecraft:overworld");

        service.clearAll();

        assertTrue(service.getPendingOres().isEmpty());
        assertTrue(service.getPendingVegetation().isEmpty());
        assertTrue(service.getPendingRemovals().isEmpty());
    }
}

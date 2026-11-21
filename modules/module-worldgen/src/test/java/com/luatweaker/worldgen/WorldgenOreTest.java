package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Ore registration and removal entries. */
class WorldgenOreTest {

    private static final String RUBY_ORE = "mymod:ruby_ore";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String PLAINS = "minecraft:plains";
    private static final String FOREST = "minecraft:forest";
    private static final String COAL_ORE = "minecraft:coal_ore";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void addOreStoresExactEntry() {
        service.addOre(RUBY_ORE, OVERWORLD, -64, 32, 8, 10);

        assertEquals(
            List.of(new WorldgenServiceImpl.OreEntry(RUBY_ORE, OVERWORLD, -64, 32, 8, 10, List.of())),
            service.getPendingOres(),
            "addOre must store the exact entry with an empty biome filter");
    }

    @Test
    void addOreBiomeFilteredStoresBiomes() {
        service.addOreBiomeFiltered(RUBY_ORE, OVERWORLD, -64, 32, 8, 10, new String[]{PLAINS, FOREST});

        assertEquals(
            List.of(new WorldgenServiceImpl.OreEntry(RUBY_ORE, OVERWORLD, -64, 32, 8, 10,
                List.of(PLAINS, FOREST))),
            service.getPendingOres(),
            "addOreBiomeFiltered must store the biome list in the supplied order");
    }

    @Test
    void removeOreStoresRemoval() {
        service.removeOre(COAL_ORE, OVERWORLD);

        assertEquals(List.of(new WorldgenServiceImpl.OreRemovalEntry(COAL_ORE, OVERWORLD)),
            service.getPendingRemovals(),
            "removeOre must record the removal for the given dimension");
    }
}

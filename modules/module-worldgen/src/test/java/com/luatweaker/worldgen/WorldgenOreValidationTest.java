package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Validation of ore registration inputs. */
class WorldgenOreValidationTest {

    private static final String RUBY_ORE = "mymod:ruby_ore";
    private static final String OVERWORLD = "minecraft:overworld";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void addOreRejectsBlankBlockId() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addOre("", OVERWORLD, -64, 32, 8, 10),
            "a blank ore block id must be rejected");
    }

    @Test
    void addOreRejectsInvalidHeightRange() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addOre(RUBY_ORE, OVERWORLD, 32, -64, 8, 10),
            "a min height above the max height must be rejected");
    }

    @Test
    void addOreRejectsZeroClusterSize() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addOre(RUBY_ORE, OVERWORLD, -64, 32, 0, 10),
            "a non-positive cluster size must be rejected");
    }

    @Test
    void addOreRejectsZeroFrequency() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addOre(RUBY_ORE, OVERWORLD, -64, 32, 8, 0),
            "a non-positive frequency must be rejected");
    }

    @Test
    void addOreBiomeFilteredRejectsEmptyBiomes() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addOreBiomeFiltered(RUBY_ORE, OVERWORLD, -64, 32, 8, 10, new String[0]),
            "a biome-filtered ore with no biomes must be rejected");
    }

    @Test
    void removeOreRejectsBlankBlockId() {
        assertThrows(IllegalArgumentException.class,
            () -> service.removeOre("", OVERWORLD),
            "a blank ore block id must be rejected by removeOre");
    }
}

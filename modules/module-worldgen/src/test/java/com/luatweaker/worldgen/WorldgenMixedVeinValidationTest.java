package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Validation of mixed-vein specs that describe bad ore lists or placement values. */
class WorldgenMixedVeinValidationTest {

    private static final String VEIN = "mymod:vein";
    private static final String PLAINS = WorldgenFixture.PLAINS;
    private static final String BIOMES_KEY = "biomes";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void missingOresIsRejected() {
        FakeLuaTable spec = new FakeLuaTable().put("biomes", FakeLuaTables.listOf(PLAINS));

        assertThrows(IllegalArgumentException.class, () -> service.addMixedVein(VEIN, spec),
            "a mixed vein without an ores list must be rejected");
    }

    @Test
    void emptyOresIsRejected() {
        FakeLuaTable spec = new FakeLuaTable()
            .put("ores", FakeLuaTables.listOf())
            .put(BIOMES_KEY, FakeLuaTables.listOf(PLAINS));

        assertThrows(IllegalArgumentException.class, () -> service.addMixedVein(VEIN, spec),
            "a mixed vein with an empty ores list must be rejected");
    }

    @Test
    void oreWithoutBlockIsRejected() {
        FakeLuaTable spec = new FakeLuaTable()
            .put("ores", FakeLuaTables.listOf(new FakeLuaTable().put("weight", 5)))
            .put(BIOMES_KEY, FakeLuaTables.listOf(PLAINS));

        assertThrows(IllegalArgumentException.class, () -> service.addMixedVein(VEIN, spec),
            "an ore candidate without a block must be rejected");
    }

    @Test
    void zeroWeightIsRejected() {
        FakeLuaTable ore = new FakeLuaTable().put("block", "mymod:x").put("weight", 0);
        FakeLuaTable spec = new FakeLuaTable()
            .put("ores", FakeLuaTables.listOf(ore))
            .put(BIOMES_KEY, FakeLuaTables.listOf(PLAINS));

        assertThrows(IllegalArgumentException.class, () -> service.addMixedVein(VEIN, spec),
            "an ore candidate with a non-positive weight must be rejected");
    }

    @Test
    void oversizeIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addMixedVein(VEIN, WorldgenFixture.magnetiteVeinSpec().put("size", 257)),
            "a mixed-vein size above the cap must be rejected");
    }

    @Test
    void unknownReplaceIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addMixedVein(VEIN, WorldgenFixture.magnetiteVeinSpec().put("replace", "moon")),
            "an unknown replace target must be rejected");
    }
}

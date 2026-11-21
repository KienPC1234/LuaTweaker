package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Mixed-vein ids, placement modes and cross-registry collisions. */
class WorldgenMixedVeinCollisionTest {

    private static final String VEIN = "mymod:vein";
    private static final String UPPERCASE_VEIN = "UPPER:id";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void countWithRarityIsRejected() {
        FakeLuaTable spec = WorldgenFixture.magnetiteVeinSpec().put("count", 4).put("rarity", 9);

        assertThrows(IllegalArgumentException.class, () -> service.addMixedVein(VEIN, spec),
            "count and rarity together must be rejected");
    }

    @Test
    void uppercaseIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addMixedVein(UPPERCASE_VEIN, WorldgenFixture.magnetiteVeinSpec()),
            "a mixed-vein id with uppercase characters must be rejected");
    }

    @Test
    void duplicateVeinIdIsRejected() {
        service.addMixedVein(VEIN, WorldgenFixture.magnetiteVeinSpec());

        assertThrows(IllegalArgumentException.class,
            () -> service.addMixedVein(VEIN, WorldgenFixture.magnetiteVeinSpec()),
            "registering the same mixed-vein id twice must be rejected");
    }

    @Test
    void featureIdCollisionIsRejected() {
        service.addMixedVein(VEIN, WorldgenFixture.magnetiteVeinSpec());

        assertThrows(IllegalArgumentException.class,
            () -> service.addFeature(VEIN, WorldgenFixture.featureSpec()),
            "a feature id already used by a mixed vein must be rejected");
    }

    @Test
    void emptyBiomesIsRejected() {
        FakeLuaTable spec = WorldgenFixture.magnetiteVeinSpec().put("biomes", FakeLuaTables.listOf());

        assertThrows(IllegalArgumentException.class, () -> service.addMixedVein(VEIN, spec),
            "a mixed vein without biomes must be rejected");
    }
}

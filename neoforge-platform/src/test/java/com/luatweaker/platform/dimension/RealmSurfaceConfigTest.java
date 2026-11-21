package com.luatweaker.platform.dimension;

import com.luatweaker.dimension.DimensionConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Dimension surface blocks must come from the Lua config. */
class RealmSurfaceConfigTest {

    private static DimensionConfig realm;

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
        realm = DimensionShowcaseFixture.getRealm();
    }

    @Test
    void surfaceBlockComesFromConfig() {
        assertEquals("luatweaker:crystal_grass", realm.surfaceBlock(),
                "the surface block must come from the dimension config");
    }

    @Test
    void subsurfaceBlockComesFromConfig() {
        assertEquals("luatweaker:crystal_dirt", realm.subsurfaceBlock(),
                "the subsurface block must come from the dimension config");
    }

    @Test
    void fillerBlockComesFromConfig() {
        assertEquals("luatweaker:crystal_stone", realm.fillerBlock(),
                "the filler block must come from the dimension config");
    }

    @Test
    void waterBlockComesFromConfig() {
        assertEquals("minecraft:water", realm.waterBlock(),
                "the water block must come from the dimension config");
    }
}

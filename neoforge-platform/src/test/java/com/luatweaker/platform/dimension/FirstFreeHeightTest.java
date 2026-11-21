package com.luatweaker.platform.dimension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The vanilla getBaseHeight contract: first free block above the surface. */
class FirstFreeHeightTest {

    @Test
    void firstFreeHeightIsOneAboveTheSurface() {
        assertEquals(71, LuaChunkGenerator.firstFreeHeight(70, -64, 320, 63, false),
                "the first free height must sit one above the surface");
    }

    @Test
    void waterCountsWhenAsked() {
        assertEquals(64, LuaChunkGenerator.firstFreeHeight(50, -64, 320, 63, true),
                "counting water must report the ocean surface");
    }

    @Test
    void waterIsSkippedByDefault() {
        assertEquals(51, LuaChunkGenerator.firstFreeHeight(50, -64, 320, 63, false),
                "skipping water must report the dry surface plus one");
    }

    @Test
    void voidColumnFallsBackToMinY() {
        assertEquals(-64, LuaChunkGenerator.firstFreeHeight(-65, -64, 320, 63, false),
                "a void column must fall back to the minimum build height");
    }

    @Test
    void waterFilledVoidReportsTheOceanSurface() {
        assertEquals(64, LuaChunkGenerator.firstFreeHeight(-65, -64, 320, 63, true),
                "a water-filled void column must report the ocean surface");
    }

    @Test
    void surfaceAtTopStaysInsideTheBuildHeight() {
        assertEquals(320, LuaChunkGenerator.firstFreeHeight(319, -64, 320, 63, false),
                "the top surface must not escape the build height");
    }
}

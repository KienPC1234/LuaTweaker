package com.luatweaker.common.dimension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teleports must only accept ground the player can actually stand on. */
class MinecraftDimensionPlatformTest {

    private static final int MIN_Y = -64;

    @Test
    void acceptsGroundWithTwoBlocksOfHeadroom() {
        assertTrue(MinecraftDimensionPlatform.isSafeSpawnColumn(181, MIN_Y, true, true, true),
                "solid ground with free feet and head space must be accepted");
    }

    @Test
    void rejectsUngeneratedVoidColumn() {
        assertFalse(MinecraftDimensionPlatform.isSafeSpawnColumn(MIN_Y, MIN_Y, true, true, true),
                "an ungenerated/empty column reports the build floor");
    }

    @Test
    void rejectsBottomLayerArtifacts() {
        assertFalse(MinecraftDimensionPlatform.isSafeSpawnColumn(MIN_Y + 2, MIN_Y, true, true, true),
                "a leftover bottom-layer block must not count as ground");
    }

    @Test
    void rejectsFluidColumns() {
        assertFalse(MinecraftDimensionPlatform.isSafeSpawnColumn(100, MIN_Y, false, true, true),
                "water/lava is not ground");
    }

    @Test
    void rejectsOccupiedFeetSpace() {
        assertFalse(MinecraftDimensionPlatform.isSafeSpawnColumn(100, MIN_Y, true, false, true),
                "feet space is occupied");
    }

    @Test
    void rejectsOccupiedHeadSpace() {
        assertFalse(MinecraftDimensionPlatform.isSafeSpawnColumn(100, MIN_Y, true, true, false),
                "head space is occupied");
    }
}

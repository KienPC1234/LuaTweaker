package com.luatweaker.platform.dimension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teleports must only accept ground the player can actually stand on. */
class NeoForgeDimensionPlatformTest {

    private static final int MIN_Y = -64;

    @Test
    void acceptsGroundWithTwoBlocksOfHeadroom() {
        assertTrue(NeoForgeDimensionPlatform.isSafeSpawnColumn(181, MIN_Y, true, true, true));
    }

    @Test
    void rejectsTheVoidAndBottomLayerArtifacts() {
        assertFalse(NeoForgeDimensionPlatform.isSafeSpawnColumn(MIN_Y, MIN_Y, true, true, true),
                "an ungenerated/empty column reports the build floor");
        assertFalse(NeoForgeDimensionPlatform.isSafeSpawnColumn(MIN_Y + 2, MIN_Y, true, true, true),
                "a leftover bottom-layer block must not count as ground");
    }

    @Test
    void rejectsFluidOrBlockedColumns() {
        assertFalse(NeoForgeDimensionPlatform.isSafeSpawnColumn(100, MIN_Y, false, true, true),
                "water/lava is not ground");
        assertFalse(NeoForgeDimensionPlatform.isSafeSpawnColumn(100, MIN_Y, true, false, true),
                "feet space is occupied");
        assertFalse(NeoForgeDimensionPlatform.isSafeSpawnColumn(100, MIN_Y, true, true, false),
                "head space is occupied");
    }
}

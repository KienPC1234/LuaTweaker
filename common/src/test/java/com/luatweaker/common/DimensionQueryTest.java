package com.luatweaker.common;

import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Query helpers must read the same pipeline the chunks use. */
class DimensionQueryTest {

    private static DimensionServiceImpl dimensions;

    @BeforeAll
    static void loadMods() {
        AutonomousModsFixture.ensureLoaded();
        dimensions = AutonomousModsFixture.getDimensions();
    }

    @Test
    void spawnColumnIsDry() {
        int spawnHeight = dimensions.getColumnHeight(AutonomousModsFixture.GRAND_WILDS, 0, 0);

        assertTrue(spawnHeight > 62, "spawn column must be dry, got " + spawnHeight);
    }

    @Test
    void previewChunkMatchesColumnHeight() {
        int spawnHeight = dimensions.getColumnHeight(AutonomousModsFixture.GRAND_WILDS, 0, 0);

        assertEquals(spawnHeight, dimensions.previewChunk(AutonomousModsFixture.GRAND_WILDS, 0, 0)
                        .rawget("heights").asTable().rawget(1).asInt(),
                "PreviewChunk must report the same height as GetColumnHeight");
    }

    @Test
    void spawnAreaIsPlains() {
        assertEquals("minecraft:plains",
                dimensions.getBiomeAt(AutonomousModsFixture.GRAND_WILDS, 0, 0),
                "the spawn area must be plains");
    }

    @Test
    void spawnAreaHasDrySurface() {
        assertNotNull(dimensions.findSurface(AutonomousModsFixture.GRAND_WILDS, 0, 0, 32),
                "the spawn area must have a dry surface");
    }
}

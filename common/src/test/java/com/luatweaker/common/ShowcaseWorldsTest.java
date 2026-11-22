package com.luatweaker.common;

import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Showcase worlds must register through their real pipelines (chunk batch,
 * height spec, density spec) or the test scripts are silently not exercising
 * the new APIs.
 */
class ShowcaseWorldsTest {

    private static DimensionServiceImpl dimensions;

    @BeforeAll
    static void loadMods() {
        AutonomousModsFixture.ensureLoaded();
        dimensions = AutonomousModsFixture.getDimensions();
    }

    @Test
    void vanillaShowcaseWorldsAreRegistered() {
        assertTrue(dimensions.getDimensionIds().containsAll(List.of(
                        AutonomousModsFixture.GRAND_WILDS, AutonomousModsFixture.ALPINE_SPEC,
                        AutonomousModsFixture.SKY_ISLES)),
                "vanilla showcase worlds missing: " + dimensions.getDimensionIds());
    }

    @Test
    void grandWildsUsesChunkMode() {
        assertEquals(DimensionServiceImpl.TerrainMode.CHUNK,
                dimensions.getTerrainMode(AutonomousModsFixture.GRAND_WILDS),
                "grand_wilds must stream terrain through the chunk batch pipeline");
    }

    @Test
    void grandWildsHasBiomeBatch() {
        assertTrue(dimensions.hasBiomeBatch(AutonomousModsFixture.GRAND_WILDS),
                "grand_wilds must register its biome batch");
    }

    @Test
    void alpineSpecUsesSpecMode() {
        assertEquals(DimensionServiceImpl.TerrainMode.SPEC,
                dimensions.getTerrainMode(AutonomousModsFixture.ALPINE_SPEC),
                "alpine_spec must stream terrain through the height-spec pipeline");
    }

    @Test
    void alpineSpecHasTerrainSpec() {
        assertNotNull(dimensions.getTerrainSpec(AutonomousModsFixture.ALPINE_SPEC),
                "alpine_spec must register its height spec");
    }

    @Test
    void skyIslesUsesDensityPipeline() {
        assertNotNull(dimensions.getTerrainSpec(AutonomousModsFixture.SKY_ISLES).density(),
                "sky_isles must use the 3D density pipeline");
    }

    @Test
    void skyIslesHaveNoWaterFill() {
        assertFalse(dimensions.getTerrainSpec(AutonomousModsFixture.SKY_ISLES).water(),
                "sky_isles must not fill water below the islands");
    }

    @Test
    void grandWildsRegistersCaveCarver() {
        assertTrue(dimensions.hasCaveSpec(AutonomousModsFixture.GRAND_WILDS),
                "grand_wilds must register the native cave carver");
    }

    @Test
    void alpineSpecRegistersCaveCarver() {
        assertTrue(dimensions.hasCaveSpec(AutonomousModsFixture.ALPINE_SPEC),
                "alpine_spec must register the native cave carver");
    }
}

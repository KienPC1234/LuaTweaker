package com.luatweaker.dimension;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies 3D density sampling: thresholds, splines, platforms and loud failures. */
class TerrainSamplerDensityTest extends TerrainSamplerTestBase {

    private static final String FBM = "fbm";

    @Test
    void densityThresholdBelowZeroFillsWholeColumn() {
        TerrainSampler sampler = sampler(densitySpec(-1, List.of()), 7L);
        DensityField field = sampler.sampleDensity(0, 0, -64, 320);

        assertEquals(Arrays.asList(true, true, 319),
                Arrays.asList(field.isSolid(0, -64, 0), field.isSolid(15, 319, 15),
                        field.topSolid(3, 3)),
                "a negative density threshold must fill the whole column solid");
    }

    @Test
    void densityThresholdAboveZeroLeavesVoid() {
        TerrainSampler sampler = sampler(densitySpec(1, List.of()), 7L);
        DensityField field = sampler.sampleDensity(0, 0, -64, 320);

        assertEquals(Arrays.asList(false, -65),
                Arrays.asList(field.isSolid(0, 100, 0), field.topSolid(0, 0)),
                "a positive density threshold with zero noise must leave the column void");
    }

    @Test
    void densityThresholdSplineCarvesAFloatingBand() {
        TerrainSpec spec = densitySpec(0, List.of(
                new TerrainSpec.SplinePoint(-64, 1.0),
                new TerrainSpec.SplinePoint(180, -1.0),
                new TerrainSpec.SplinePoint(319, 1.0)));
        DensityField field = sampler(spec, 7L).sampleDensity(0, 0, -64, 320);

        assertEquals(Arrays.asList(false, false, true),
                Arrays.asList(field.isSolid(0, -64, 0), field.isSolid(0, 300, 0),
                        field.isSolid(0, 180, 0)),
                "a threshold spline must carve the bottom and top while keeping the band solid");
    }

    @Test
    void densityBandTopSitsInsideSplineBand() {
        TerrainSpec spec = densitySpec(0, List.of(
                new TerrainSpec.SplinePoint(-64, 1.0),
                new TerrainSpec.SplinePoint(180, -1.0),
                new TerrainSpec.SplinePoint(319, 1.0)));
        int top = sampler(spec, 7L).sampleDensity(0, 0, -64, 320).topSolid(0, 0);

        assertTrue(top > 180 && top < 319,
                "the top solid block must sit inside the carved band, got " + top);
    }

    @Test
    void densityIsDeterministicForSameSeed() {
        TerrainSpec.Density density = new TerrainSpec.Density(
                List.of(layer("fbm3", 1.0, 0.02)), 0.1, 4, 4, List.of());
        TerrainSpec spec = new TerrainSpec(null, List.of(layer(FBM, 0.0, 0.01)),
                List.of(), List.of(), List.of(), density, true);
        DensityField first = sampler(spec, 21L).sampleDensity(0, 0, -64, 320);
        DensityField second = sampler(spec, 21L).sampleDensity(0, 0, -64, 320);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(first.topSolid(x, z), second.topSolid(x, z),
                        "the same seed must reproduce the density field at " + x + "," + z);
            }
        }
    }

    @Test
    void densityOutsideBuildRangeIsNeverSolid() {
        DensityField field = sampler(densitySpec(-1, List.of()), 7L).sampleDensity(0, 0, -64, 320);

        assertEquals(Arrays.asList(false, false),
                Arrays.asList(field.isSolid(0, -65, 0), field.isSolid(0, 320, 0)),
                "blocks outside the build range must never be solid");
    }

    @Test
    void densityPlatformForcesSolidSpawnIsland() {
        TerrainSpec.Density density = new TerrainSpec.Density(
                List.of(layer("fbm3", 0.0, 0.02)), 1.0, 4, 4, List.of());
        TerrainSpec.Platform platform = new TerrainSpec.Platform(0, 0, 6, 170, 182);
        TerrainSpec spec = new TerrainSpec(null, List.of(layer(FBM, 0.0, 0.01)),
                List.of(), List.of(), List.of(platform), density, true);
        DensityField field = sampler(spec, 7L).sampleDensity(0, 0, -64, 320);

        assertEquals(Arrays.asList(true, 182, false, false),
                Arrays.asList(field.isSolid(0, 180, 0), field.topSolid(0, 0),
                        field.isSolid(7, 180, 0), field.isSolid(0, 169, 0)),
                "a density platform must fill its radius and stop at bottomY/topY");
    }

    @Test
    void heightAtPlatformRaisesColumn() {
        TerrainSpec.Platform platform = new TerrainSpec.Platform(0, 0, 4, 60, 70);
        TerrainSpec spec = new TerrainSpec(null, List.of(layer(FBM, 0.0, 0.01)),
                List.of(), List.of(), List.of(platform), null, true);
        TerrainSampler sampler = sampler(spec, 7L);

        assertEquals(List.of(70, 70, 63),
                List.of(sampler.heightAt(0, 0), sampler.heightAt(4, 0), sampler.heightAt(5, 0)),
                "a platform must raise the column inside its radius and leave the rest unchanged");
    }

    @Test
    void densitySectionIsAbsentWithoutDensity() {
        TerrainSampler sampler = sampler(terrain(null,
                List.of(layer(FBM, 1.0, 0.01)), List.of(), List.of()), 7L);

        assertFalse(sampler.hasDensity(),
                "a spec without a density section must report no density field");
    }

    @Test
    void densitySamplingWithoutSectionThrows() {
        TerrainSampler sampler = sampler(terrain(null,
                List.of(layer(FBM, 1.0, 0.01)), List.of(), List.of()), 7L);

        assertThrows(IllegalStateException.class, () -> sampler.sampleDensity(0, 0, -64, 320),
                "sampling density without a density section must fail loudly");
    }
}

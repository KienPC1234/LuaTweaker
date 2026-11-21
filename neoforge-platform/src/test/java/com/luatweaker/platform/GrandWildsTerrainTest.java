package com.luatweaker.platform;

import com.luatweaker.platform.GrandWildsSampler.TerrainSample;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grand_wilds callbacks must produce varied terrain (a flat world here
 * means the chunk cache or a script edit collapsed the noise stack).
 */
class GrandWildsTerrainTest {

    private static final int HEIGHT_SPREAD_MIN = 50;
    private static final int MEDIAN_MIN = 80;
    private static final int MEDIAN_MAX = 170;
    private static final double MIN_OCEAN_FRACTION = 0.03;
    private static final double MAX_OCEAN_FRACTION = 0.40;
    private static final double MIN_PEAK_FRACTION = 0.03;
    private static final double MIN_STEEP_FRACTION = 0.002;
    private static final double MAX_STEEP_FRACTION = 0.15;
    private static final double MAX_CLIFF_FRACTION = 0.05;

    @BeforeAll
    static void loadMods() {
        AutonomousModsFixture.ensureLoaded();
    }

    private static TerrainSample sample() {
        return GrandWildsSampler.sample(AutonomousModsFixture.getDimensions());
    }

    @Test
    void terrainIsNotFlat() {
        TerrainSample sample = sample();

        assertTrue(sample.maxHeight() - sample.minHeight() > HEIGHT_SPREAD_MIN,
                "grand_wilds terrain is flat: " + sample.minHeight() + ".." + sample.maxHeight());
    }

    @Test
    void medianHeightIsLand() {
        TerrainSample sample = sample();

        assertTrue(sample.medianHeight() > MEDIAN_MIN && sample.medianHeight() < MEDIAN_MAX,
                "grand_wilds median height is not land: " + sample.medianHeight());
    }

    @Test
    void oceanFractionIsInRange() {
        TerrainSample sample = sample();

        assertTrue(sample.oceanFraction() > MIN_OCEAN_FRACTION
                        && sample.oceanFraction() < MAX_OCEAN_FRACTION,
                "grand_wilds ocean/river fraction out of range: " + sample.oceanFraction());
    }

    @Test
    void peaksExist() {
        TerrainSample sample = sample();

        assertTrue(sample.peakFraction() > MIN_PEAK_FRACTION,
                "grand_wilds has no high mountains: " + sample.peakFraction());
    }

    @Test
    void slopesArePresentButNotCliffs() {
        TerrainSample sample = sample();
        // Real slopes but no per-column spikes: a lattice/interpolation bug shows up as either
        // a perfectly flat field or a field of 1-block cliffs.
        assertTrue(sample.steepFraction() > MIN_STEEP_FRACTION
                        && sample.steepFraction() < MAX_STEEP_FRACTION,
                "grand_wilds slope distribution is off: " + sample.steepFraction()
                        + " of columns step >= 4");
    }

    @Test
    void spikeCliffsAreAbsent() {
        TerrainSample sample = sample();

        assertTrue(sample.cliffFraction() < MAX_CLIFF_FRACTION,
                "grand_wilds has spike cliffs: " + sample.cliffFraction()
                        + " of columns step >= 8");
    }
}

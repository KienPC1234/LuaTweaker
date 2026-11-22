package com.luatweaker.common;

import com.luatweaker.common.RiverSampler.RiverSample;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * River/lake quality: a wide, shallow valley must not be a one-block chasm. The old
 * lake carve produced 130-block drops next to a single water column. Rivers are
 * sparse at this continental scale, so locate one and sample a window around it.
 */
class GrandWildsRiverTest {

    private static final int MIN_BANK_SAMPLES = 200;
    private static final double MIN_WATER_FRACTION = 0.01;
    private static final int MAX_BANK_STEP = 30;
    private static final int MAX_MEDIAN_BANK_STEP = 6;
    private static final int MAX_WIDE_BANK_STEP = 16;
    private static final int MIN_RIVER_WIDTH = 4;
    private static final int MIN_WIDE_RIVER_WIDTH = 8;
    private static final int MAX_WIDE_RIVER_WIDTH = 60;
    private static final double MAX_NARROW_FRACTION = 0.25;

    @BeforeAll
    static void loadMods() {
        AutonomousModsFixture.ensureLoaded();
    }

    private static RiverSample sample() {
        return RiverSampler.sample(AutonomousModsFixture.getDimensions());
    }

    @Test
    void riverBanksAreFound() {
        assertTrue(sample().bankSteps().size() > MIN_BANK_SAMPLES,
                "no river banks found in the sample");
    }

    @Test
    void riverWaterIsFound() {
        RiverSample sample = sample();

        assertTrue(sample.riverWaterFraction() > MIN_WATER_FRACTION,
                "no river water in the sampled window: " + sample.riverWaterFraction());
    }

    @Test
    void riverBanksAreNotShafts() {
        RiverSample sample = sample();

        assertTrue(sample.maxBankStep() <= MAX_BANK_STEP,
                "river bank is a vertical shaft: " + sample.maxBankStep() + " blocks (p95="
                        + sample.wideBankStep() + ")");
    }

    @Test
    void riverBanksAreNotCliffs() {
        RiverSample sample = sample();

        assertTrue(sample.medianBankStep() <= MAX_MEDIAN_BANK_STEP
                        && sample.wideBankStep() <= MAX_WIDE_BANK_STEP,
                "river banks are cliffs: p50=" + sample.medianBankStep()
                        + " p95=" + sample.wideBankStep());
    }

    @Test
    void riversAreNotNarrowCuts() {
        RiverSample sample = sample();

        assertTrue(sample.medianRiverWidth() >= MIN_RIVER_WIDTH,
                "rivers are narrow cuts, median width=" + sample.medianRiverWidth());
    }

    @Test
    void riversReachAHealthyWidth() {
        RiverSample sample = sample();

        assertTrue(sample.wideRiverWidth() >= MIN_WIDE_RIVER_WIDTH,
                "rivers are too narrow, p95 width=" + sample.wideRiverWidth());
    }

    @Test
    void riversDoNotFloodTheLowlands() {
        RiverSample sample = sample();

        assertTrue(sample.wideRiverWidth() <= MAX_WIDE_RIVER_WIDTH,
                "rivers are flooding the lowlands, p95 width=" + sample.wideRiverWidth());
    }

    @Test
    void oneBlockRiversAreRare() {
        RiverSample sample = sample();

        assertTrue(sample.narrowRuns() < sample.riverWidths().size() * MAX_NARROW_FRACTION,
                "too many one-block rivers: " + sample.narrowRuns() + "/"
                        + sample.riverWidths().size());
    }
}

package com.luatweaker.noise;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Native sampler: warp strength is a block-space displacement, so the sampled field
 * must stay smooth across neighbouring blocks.
 */
class NoiseSamplerTest {

    private static final double MAX_NEIGHBOUR_DELTA = 0.2;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void warpedFbmStaysSmoothAcrossNeighbouringBlocks() {
        NoiseServiceImpl service = new NoiseServiceImpl(7L);
        NoiseLayer layer = new NoiseLayer("warped_fbm", 0.0011, 4, 2.0, 0.5, 0.8, 0, 70.0, 1.0);
        NoiseServiceImpl.NoiseSampler sampler = service.createSampler();

        double previous = sampler.sample2D(layer, -64, 0);
        double maxDelta = 0.0;
        for (int x = -63; x <= 64; x++) {
            double value = sampler.sample2D(layer, x, 0);
            maxDelta = Math.max(maxDelta, Math.abs(value - previous));
            previous = value;
        }

        Assertions.assertTrue(maxDelta < MAX_NEIGHBOUR_DELTA,
                "warped_fbm must stay smooth; warpStrength is a block displacement, max delta=" + maxDelta);
    }
}

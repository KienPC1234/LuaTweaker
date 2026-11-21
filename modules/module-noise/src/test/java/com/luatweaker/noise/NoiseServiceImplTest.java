package com.luatweaker.noise;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Seed determinism of every noise function.
 */
class NoiseServiceImplTest {

    private static final double EPSILON = 1e-9;
    private static final long SEED_A = 12_345L;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void sameSeedReproducesEveryNoiseFunction() {
        NoiseServiceImpl first = new NoiseServiceImpl(SEED_A);
        NoiseServiceImpl second = new NoiseServiceImpl(SEED_A);

        for (int i = 0; i < 50; i++) {
            double x = i * 0.37;
            double z = i * 1.13;
            Assertions.assertArrayEquals(sampleAll(first, x, z), sampleAll(second, x, z), EPSILON,
                    "seed " + SEED_A + " must reproduce every function at sample " + i);
        }
    }

    @Test
    void differentSeedsProduceDifferentOutput() {
        NoiseServiceImpl first = new NoiseServiceImpl(1L);
        NoiseServiceImpl second = new NoiseServiceImpl(2L);
        boolean differs = false;
        for (int i = 0; i < 200 && !differs; i++) {
            double delta = first.simplex(i * 0.31, i * 0.77, 1.0) - second.simplex(i * 0.31, i * 0.77, 1.0);
            differs = Math.abs(delta) > 1e-6;
        }

        Assertions.assertTrue(differs, "different seeds must produce different noise");
    }

    @Test
    void negativeAndHugeSeedsStayFiniteAndDeterministic() {
        NoiseServiceImpl negative = new NoiseServiceImpl(-99L);
        NoiseServiceImpl huge = new NoiseServiceImpl(Long.MAX_VALUE);

        double first = negative.simplex(1.5, 2.5, 1.0);
        double second = negative.simplex(1.5, 2.5, 1.0);

        Assertions.assertTrue(Double.isFinite(first) && Double.isFinite(huge.simplex(1.5, 2.5, 1.0))
                        && first == second,
                "negative and huge seeds must stay finite and deterministic");
    }

    @Test
    void setSeedChangesSubsequentOutput() {
        NoiseServiceImpl service = new NoiseServiceImpl();
        double before = service.simplex(3.0, 4.0, 1.0);

        service.setSeed(777L);

        Assertions.assertNotEquals(before, service.simplex(3.0, 4.0, 1.0),
                "setSeed must change the output of the existing service instance");
    }

    private static double[] sampleAll(NoiseServiceImpl service, double x, double z) {
        double[] warped = service.domainWarp(x, z, 20.0, 1.0);
        return new double[] {
            service.simplex(x, z, 1.0),
            service.fBm(x, z, 4, 2.0, 0.5),
            service.ridged(x, z, 4, 1.0, 2.0),
            service.voronoi(x, z, 0.8, 0),
            warped[0],
            warped[1],
        };
    }
}

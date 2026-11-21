package com.luatweaker.noise;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Domain warping: shape, determinism, bounded output and validation.
 */
class NoiseDomainWarpTest {

    private static final double EPSILON = 1e-9;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void returnsTwoWarpedValues() {
        NoiseServiceImpl service = new NoiseServiceImpl(29L);

        double[] warped = service.domainWarp(3.0, 4.0, 20.0, 1.0);

        Assertions.assertTrue(warped != null && warped.length == 2,
                "domainWarp must return the two warped coordinates");
    }

    @Test
    void isDeterministic() {
        NoiseServiceImpl service = new NoiseServiceImpl(31L);

        Assertions.assertArrayEquals(service.domainWarp(3.0, 4.0, 20.0, 1.0),
                service.domainWarp(3.0, 4.0, 20.0, 1.0), EPSILON,
                "the same coordinates must always warp to the same offsets");
    }

    @Test
    void outputStaysFiniteAndBounded() {
        NoiseServiceImpl service = new NoiseServiceImpl(37L);

        for (int i = 0; i < 100; i++) {
            double[] warped = service.domainWarp(i * 0.5, i * 0.9, 50.0, 1.0);
            Assertions.assertTrue(Double.isFinite(warped[0]) && Double.isFinite(warped[1])
                            && Math.abs(warped[0]) <= 1.0 && Math.abs(warped[1]) <= 1.0,
                    "domainWarp out of range: " + warped[0] + ", " + warped[1]);
        }
    }

    @Test
    void rejectsInvalidFrequency() {
        NoiseServiceImpl service = new NoiseServiceImpl();

        Assertions.assertAll("domainWarp must reject a non-positive frequency",
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.domainWarp(1, 1, 20.0, 0), "a zero frequency must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.domainWarp(1, 1, 20.0, -1.0), "a negative frequency must be rejected"));
    }

    @Test
    void rejectsNaNStrength() {
        NoiseServiceImpl service = new NoiseServiceImpl();

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.domainWarp(1, 1, Double.NaN, 1.0),
                "a NaN warp strength must be rejected");
    }
}

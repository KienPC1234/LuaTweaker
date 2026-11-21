package com.luatweaker.noise;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 2D simplex: normalization, determinism, frequency scaling and validation.
 */
class NoiseSimplexTest {

    private static final double EPSILON = 1e-9;
    private static final long SEED = 42L;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void simplexStaysWithinUnitRange() {
        NoiseServiceImpl service = new NoiseServiceImpl(SEED);

        for (int i = 0; i < 500; i++) {
            double value = service.simplex(i * 0.123, i * 0.321, 1.0);
            Assertions.assertTrue(value >= -1.0 && value <= 1.0, "simplex out of range: " + value);
        }
    }

    @Test
    void simplexUsesTheFullDynamicRange() {
        // Regression: a too-weak normalization produced ~+-0.25 noise, which
        // made every fBm-based terrain flat.
        NoiseServiceImpl service = new NoiseServiceImpl(SEED);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < 200_000; i++) {
            double x = i * 0.618_034 % 1000.0 - 500.0;
            double z = i * 0.414_214 % 1000.0 - 500.0;
            double value = service.simplex(x, z, 1.0);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        Assertions.assertTrue(max >= 0.85 && min <= -0.85 && max <= 1.05 && min >= -1.05,
                "simplex must span at least [-0.85, 0.85] and stay near [-1, 1], got " + min + ".." + max);
    }

    @Test
    void simplexIsDeterministic() {
        NoiseServiceImpl service = new NoiseServiceImpl(7L);

        Assertions.assertEquals(service.simplex(1.1, 2.2, 1.0), service.simplex(1.1, 2.2, 1.0), EPSILON,
                "the same coordinates must always produce the same simplex value");
    }

    @Test
    void simplexFrequencyScalesTheInput() {
        NoiseServiceImpl service = new NoiseServiceImpl(9L);

        Assertions.assertEquals(service.simplex(2.0, 3.0, 2.0), service.simplex(4.0, 6.0, 1.0), EPSILON,
                "simplex(x, z, f) must equal simplex(x*f, z*f, 1)");
    }

    @Test
    void simplexRejectsNonPositiveFrequency() {
        NoiseServiceImpl service = new NoiseServiceImpl();

        Assertions.assertAll("a non-positive or NaN frequency must be rejected",
            () -> Assertions.assertThrows(IllegalArgumentException.class, () -> service.simplex(1, 1, 0),
                "a zero frequency must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class, () -> service.simplex(1, 1, -1),
                "a negative frequency must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.simplex(1, 1, Double.NaN), "a NaN frequency must be rejected"));
    }
}

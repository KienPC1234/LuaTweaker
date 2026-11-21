package com.luatweaker.noise;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 3D simplex and 3D fBm reproducibility, range and validation.
 */
class NoiseThreeDimensionalTest {

    private static final double EPSILON = 1e-9;
    private static final long SEED = 4242L;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void simplex3ReproducesForTheSameSeed() {
        NoiseServiceImpl first = new NoiseServiceImpl(SEED);
        NoiseServiceImpl second = new NoiseServiceImpl(SEED);

        for (int i = 0; i < 50; i++) {
            double x = i * 0.37;
            double y = i * 0.53;
            double z = i * 1.13;
            Assertions.assertEquals(first.simplex3(x, y, z, 1.0), second.simplex3(x, y, z, 1.0), EPSILON,
                    "simplex3 must reproduce for the same seed at sample " + i);
        }
    }

    @Test
    void simplex3StaysWithinUnitRange() {
        NoiseServiceImpl service = new NoiseServiceImpl(SEED);

        for (int i = 0; i < 50; i++) {
            double value = service.simplex3(i * 0.37, i * 0.53, i * 1.13, 1.0);
            Assertions.assertTrue(value >= -1.0 - 1e-6 && value <= 1.0 + 1e-6,
                    "simplex3 range violated: " + value);
        }
    }

    @Test
    void simplex3VariesAcrossPositions() {
        NoiseServiceImpl service = new NoiseServiceImpl(SEED);
        double first = service.simplex3(0.0, 0.0, 0.0, 1.0);
        boolean varies = false;
        for (int i = 1; i < 50 && !varies; i++) {
            varies = Math.abs(service.simplex3(i * 0.37, i * 0.53, i * 1.13, 1.0) - first) > 1e-3;
        }

        Assertions.assertTrue(varies, "simplex3 must vary across positions");
    }

    @Test
    void fbm3StaysNormalized() {
        NoiseServiceImpl service = new NoiseServiceImpl(99L);

        for (int i = 0; i < 50; i++) {
            double value = service.fbm3(i * 0.31, i * 0.77, i * 1.7, 4, 2.0, 0.5);
            Assertions.assertTrue(value >= -1.0 - EPSILON && value <= 1.0 + EPSILON,
                    "fbm3 must stay normalized: " + value);
        }
    }

    @Test
    void fbm3IsReproducible() {
        NoiseServiceImpl first = new NoiseServiceImpl(99L);
        NoiseServiceImpl second = new NoiseServiceImpl(99L);

        Assertions.assertEquals(first.fbm3(1.0, 2.0, 3.0, 4, 2.0, 0.5),
                second.fbm3(1.0, 2.0, 3.0, 4, 2.0, 0.5), EPSILON,
                "fbm3 must reproduce for the same seed");
    }

    @Test
    void simplex3AndFbm3RejectInvalidInput() {
        NoiseServiceImpl service = new NoiseServiceImpl();

        Assertions.assertAll("invalid 3D noise parameters must be rejected",
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.simplex3(0, 0, 0, 0.0), "simplex3 must reject a zero frequency"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.fbm3(0, 0, 0, 0, 2.0, 0.5), "fbm3 must reject zero octaves"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.fbm3(0, 0, 0, 4, -1.0, 0.5), "fbm3 must reject a negative lacunarity"));
    }
}

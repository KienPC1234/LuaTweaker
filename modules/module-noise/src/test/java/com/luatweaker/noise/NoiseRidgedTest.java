package com.luatweaker.noise;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Ridged multifractal: non-negativity, range, frequency scaling and validation.
 */
class NoiseRidgedTest {

    private static final double EPSILON = 1e-9;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void ridgedStaysInTheUnitRange() {
        NoiseServiceImpl service = new NoiseServiceImpl(17L);

        for (int i = 0; i < 200; i++) {
            double value = service.ridged(i * 0.11, i * 0.29, 4, 1.0, 2.0);
            Assertions.assertTrue(value >= 0.0 && value <= 1.0, "ridged out of range: " + value);
        }
    }

    @Test
    void ridgedIsAlwaysNonNegative() {
        NoiseServiceImpl service = new NoiseServiceImpl(19L);

        for (int i = 0; i < 500; i++) {
            Assertions.assertTrue(service.ridged(i * 0.07, i * 0.13, 5, 1.0, 2.0) >= 0.0,
                    "ridged noise must never be negative");
        }
    }

    @Test
    void ridgedRejectsZeroOctaves() {
        NoiseServiceImpl service = new NoiseServiceImpl();

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.ridged(1, 1, 0, 1.0, 2.0),
                "ridged must reject zero octaves");
    }

    @Test
    void ridgedRejectsInvalidFrequency() {
        NoiseServiceImpl service = new NoiseServiceImpl();

        Assertions.assertAll("ridged must reject a non-positive frequency",
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.ridged(1, 1, 4, 0, 2.0), "a zero frequency must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.ridged(1, 1, 4, -1.0, 2.0), "a negative frequency must be rejected"));
    }

    @Test
    void ridgedRejectsInvalidLacunarity() {
        NoiseServiceImpl service = new NoiseServiceImpl();

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.ridged(1, 1, 4, 1.0, 0),
                "ridged must reject a zero lacunarity");
    }

    @Test
    void ridgedFrequencyScalesTheInput() {
        NoiseServiceImpl service = new NoiseServiceImpl(23L);

        Assertions.assertEquals(service.ridged(2.0, 3.0, 3, 2.0, 2.0),
                service.ridged(4.0, 6.0, 3, 1.0, 2.0), EPSILON,
                "ridged(x, z, o, f, l) must equal ridged(x*f, z*f, o, 1, l)");
    }
}

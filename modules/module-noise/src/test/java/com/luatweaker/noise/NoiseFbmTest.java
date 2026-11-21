package com.luatweaker.noise;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 2D fBm: octave composition, normalization, determinism and validation.
 */
class NoiseFbmTest {

    private static final double EPSILON = 1e-9;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void singleOctaveEqualsSimplex() {
        NoiseServiceImpl service = new NoiseServiceImpl(5L);

        for (int i = 0; i < 20; i++) {
            double x = i * 0.5;
            double z = i * 0.25;
            Assertions.assertEquals(service.simplex(x, z, 1.0), service.fBm(x, z, 1, 2.0, 0.5), EPSILON,
                    "fBm with one octave must equal simplex at sample " + i);
        }
    }

    @Test
    void fbmStaysWithinUnitRange() {
        NoiseServiceImpl service = new NoiseServiceImpl(11L);

        for (int i = 0; i < 200; i++) {
            double value = service.fBm(i * 0.17, i * 0.53, 6, 2.0, 0.5);
            Assertions.assertTrue(value >= -1.0 && value <= 1.0, "fBm out of range: " + value);
        }
    }

    @Test
    void moreOctavesChangeTheResult() {
        NoiseServiceImpl service = new NoiseServiceImpl(3L);

        Assertions.assertNotEquals(service.fBm(2.0, 3.0, 1, 2.0, 0.5),
                service.fBm(2.0, 3.0, 5, 2.0, 0.5),
                "different octave counts must change the result");
    }

    @Test
    void fbmRejectsZeroOrNegativeOctaves() {
        NoiseServiceImpl service = new NoiseServiceImpl();

        Assertions.assertAll("fBm must reject zero and negative octave counts",
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.fBm(1, 1, 0, 2.0, 0.5), "zero octaves must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.fBm(1, 1, -3, 2.0, 0.5), "negative octaves must be rejected"));
    }

    @Test
    void fbmRejectsInvalidLacunarity() {
        NoiseServiceImpl service = new NoiseServiceImpl();

        Assertions.assertAll("fBm must reject a non-positive lacunarity",
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.fBm(1, 1, 4, 0, 0.5), "a zero lacunarity must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.fBm(1, 1, 4, -2.0, 0.5), "a negative lacunarity must be rejected"));
    }

    @Test
    void fbmRejectsInvalidGain() {
        NoiseServiceImpl service = new NoiseServiceImpl();

        Assertions.assertAll("fBm must reject a non-positive gain",
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.fBm(1, 1, 4, 2.0, 0), "a zero gain must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.fBm(1, 1, 4, 2.0, -0.5), "a negative gain must be rejected"));
    }

    @Test
    void fbmIsDeterministic() {
        NoiseServiceImpl service = new NoiseServiceImpl(13L);

        Assertions.assertEquals(service.fBm(0.5, 0.5, 4, 2.0, 0.5),
                service.fBm(0.5, 0.5, 4, 2.0, 0.5), EPSILON,
                "the same coordinates must always produce the same fBm value");
    }
}

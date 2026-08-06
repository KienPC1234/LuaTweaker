package com.luatweaker.noise;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers every noise function: success paths, edge cases, invalid inputs,
 * seed reproducibility, determinism, and a 1M-evaluation performance gate.
 */
public class NoiseServiceImplTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private static final double EPS = 1e-9;

    // ---------- setSeed ----------

    @Test
    void setSeed_MakesOutputReproducible() {
        NoiseServiceImpl a = new NoiseServiceImpl();
        NoiseServiceImpl b = new NoiseServiceImpl();
        a.setSeed(12345L);
        b.setSeed(12345L);
        for (int i = 0; i < 50; i++) {
            double x = i * 0.37, z = i * 1.13;
            assertEquals(a.simplex(x, z, 1.0), b.simplex(x, z, 1.0), EPS, "simplex must reproduce for same seed");
            assertEquals(a.fBm(x, z, 4, 2.0, 0.5), b.fBm(x, z, 4, 2.0, 0.5), EPS, "fBm must reproduce for same seed");
            assertEquals(a.ridged(x, z, 4, 1.0, 2.0), b.ridged(x, z, 4, 1.0, 2.0), EPS, "ridged must reproduce for same seed");
            assertEquals(a.voronoi(x, z, 0.8, 0), b.voronoi(x, z, 0.8, 0), EPS, "voronoi must reproduce for same seed");
            assertArrayEquals(a.domainWarp(x, z, 20.0, 1.0), b.domainWarp(x, z, 20.0, 1.0), EPS);
        }
    }

    @Test
    void setSeed_DifferentSeedsProduceDifferentOutput() {
        NoiseServiceImpl a = new NoiseServiceImpl();
        NoiseServiceImpl b = new NoiseServiceImpl();
        a.setSeed(1L);
        b.setSeed(2L);
        boolean differs = false;
        for (int i = 0; i < 200 && !differs; i++) {
            if (Math.abs(a.simplex(i * 0.31, i * 0.77, 1.0) - b.simplex(i * 0.31, i * 0.77, 1.0)) > 1e-6) {
                differs = true;
            }
        }
        assertTrue(differs, "different seeds must produce different noise");
    }

    @Test
    void setSeed_HandlesNegativeAndHugeSeeds() {
        NoiseServiceImpl a = new NoiseServiceImpl(-99L);
        NoiseServiceImpl b = new NoiseServiceImpl(Long.MAX_VALUE);
        assertTrue(Double.isFinite(a.simplex(1.5, 2.5, 1.0)));
        assertTrue(Double.isFinite(b.simplex(1.5, 2.5, 1.0)));
        assertEquals(a.simplex(1.5, 2.5, 1.0), a.simplex(1.5, 2.5, 1.0), EPS);
    }

    @Test
    void setSeed_ResetsAllFunctions() {
        NoiseServiceImpl service = new NoiseServiceImpl();
        double before = service.simplex(3.0, 4.0, 1.0);
        service.setSeed(777L);
        assertNotEquals(before, service.simplex(3.0, 4.0, 1.0));
    }

    // ---------- simplex ----------

    @Test
    void simplex_StaysWithinUnitRange() {
        NoiseServiceImpl service = new NoiseServiceImpl(42L);
        for (int i = 0; i < 500; i++) {
            double value = service.simplex(i * 0.123, i * 0.321, 1.0);
            assertTrue(value >= -1.0 && value <= 1.0, "simplex out of range: " + value);
        }
    }

    @Test
    void simplex_UsesTheFullDynamicRange() {
        // Regression: a too-weak normalization produced ~+-0.25 noise, which
        // made every fBm-based terrain flat.
        NoiseServiceImpl service = new NoiseServiceImpl(42L);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < 200_000; i++) {
            double x = (i * 0.618034) % 1000.0 - 500.0;
            double z = (i * 0.414214) % 1000.0 - 500.0;
            double value = service.simplex(x, z, 1.0);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        assertTrue(max >= 0.85, "simplex must reach at least +0.85, got max " + max);
        assertTrue(min <= -0.85, "simplex must reach at least -0.85, got min " + min);
        assertTrue(max <= 1.05 && min >= -1.05, "simplex must stay near [-1, 1], got " + min + ".." + max);
    }

    @Test
    void simplex_IsDeterministic() {
        NoiseServiceImpl service = new NoiseServiceImpl(7L);
        assertEquals(service.simplex(1.1, 2.2, 1.0), service.simplex(1.1, 2.2, 1.0), EPS);
    }

    @Test
    void simplex_FrequencyScalesInput() {
        NoiseServiceImpl service = new NoiseServiceImpl(9L);
        assertEquals(service.simplex(2.0, 3.0, 2.0), service.simplex(4.0, 6.0, 1.0), EPS,
                "simplex(x, z, f) must equal simplex(x*f, z*f, 1)");
    }

    @Test
    void simplex_RejectsNonPositiveFrequency() {
        NoiseServiceImpl service = new NoiseServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> service.simplex(1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> service.simplex(1, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> service.simplex(1, 1, Double.NaN));
    }

    // ---------- fBm ----------

    @Test
    void fBm_SingleOctaveEqualsSimplex() {
        NoiseServiceImpl service = new NoiseServiceImpl(5L);
        for (int i = 0; i < 20; i++) {
            double x = i * 0.5, z = i * 0.25;
            assertEquals(service.simplex(x, z, 1.0), service.fBm(x, z, 1, 2.0, 0.5), EPS,
                    "fBm with 1 octave must equal simplex");
        }
    }

    @Test
    void fBm_StaysWithinUnitRange() {
        NoiseServiceImpl service = new NoiseServiceImpl(11L);
        for (int i = 0; i < 200; i++) {
            double value = service.fBm(i * 0.17, i * 0.53, 6, 2.0, 0.5);
            assertTrue(value >= -1.0 && value <= 1.0, "fBm out of range: " + value);
        }
    }

    @Test
    void fBm_MoreOctavesChangesResult() {
        NoiseServiceImpl service = new NoiseServiceImpl(3L);
        double one = service.fBm(2.0, 3.0, 1, 2.0, 0.5);
        double five = service.fBm(2.0, 3.0, 5, 2.0, 0.5);
        assertNotEquals(one, five, "different octave counts must change the result");
    }

    @Test
    void fBm_RejectsZeroOctaves() {
        NoiseServiceImpl service = new NoiseServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> service.fBm(1, 1, 0, 2.0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> service.fBm(1, 1, -3, 2.0, 0.5));
    }

    @Test
    void fBm_RejectsInvalidLacunarity() {
        NoiseServiceImpl service = new NoiseServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> service.fBm(1, 1, 4, 0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> service.fBm(1, 1, 4, -2.0, 0.5));
    }

    @Test
    void fBm_RejectsInvalidGain() {
        NoiseServiceImpl service = new NoiseServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> service.fBm(1, 1, 4, 2.0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.fBm(1, 1, 4, 2.0, -0.5));
    }

    @Test
    void fBm_IsDeterministic() {
        NoiseServiceImpl service = new NoiseServiceImpl(13L);
        assertEquals(service.fBm(0.5, 0.5, 4, 2.0, 0.5), service.fBm(0.5, 0.5, 4, 2.0, 0.5), EPS);
    }

    // ---------- ridged ----------

    @Test
    void ridged_StaysInExpectedRange() {
        NoiseServiceImpl service = new NoiseServiceImpl(17L);
        for (int i = 0; i < 200; i++) {
            double value = service.ridged(i * 0.11, i * 0.29, 4, 1.0, 2.0);
            assertTrue(value >= 0.0 && value <= 1.0, "ridged out of range: " + value);
        }
    }

    @Test
    void ridged_IsAlwaysNonNegative() {
        NoiseServiceImpl service = new NoiseServiceImpl(19L);
        for (int i = 0; i < 500; i++) {
            assertTrue(service.ridged(i * 0.07, i * 0.13, 5, 1.0, 2.0) >= 0.0);
        }
    }

    @Test
    void ridged_RejectsZeroOctaves() {
        NoiseServiceImpl service = new NoiseServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> service.ridged(1, 1, 0, 1.0, 2.0));
    }

    @Test
    void ridged_RejectsInvalidFrequency() {
        NoiseServiceImpl service = new NoiseServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> service.ridged(1, 1, 4, 0, 2.0));
        assertThrows(IllegalArgumentException.class, () -> service.ridged(1, 1, 4, -1.0, 2.0));
    }

    @Test
    void ridged_RejectsInvalidLacunarity() {
        NoiseServiceImpl service = new NoiseServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> service.ridged(1, 1, 4, 1.0, 0));
    }

    @Test
    void ridged_FrequencyScalesInput() {
        NoiseServiceImpl service = new NoiseServiceImpl(23L);
        assertEquals(service.ridged(2.0, 3.0, 3, 2.0, 2.0), service.ridged(4.0, 6.0, 3, 1.0, 2.0), EPS,
                "ridged(x, z, o, f, l) must equal ridged(x*f, z*f, o, 1, l)");
    }

    // ---------- domainWarp ----------

    @Test
    void domainWarp_ReturnsTwoWarpedValues() {
        NoiseServiceImpl service = new NoiseServiceImpl(29L);
        double[] warped = service.domainWarp(3.0, 4.0, 20.0, 1.0);
        assertNotNull(warped);
        assertEquals(2, warped.length);
    }

    @Test
    void domainWarp_IsDeterministic() {
        NoiseServiceImpl service = new NoiseServiceImpl(31L);
        assertArrayEquals(service.domainWarp(3.0, 4.0, 20.0, 1.0),
                service.domainWarp(3.0, 4.0, 20.0, 1.0), EPS);
    }

    @Test
    void domainWarp_OutputStaysInExpectedRange() {
        NoiseServiceImpl service = new NoiseServiceImpl(37L);
        for (int i = 0; i < 100; i++) {
            double[] warped = service.domainWarp(i * 0.5, i * 0.9, 50.0, 1.0);
            assertTrue(Double.isFinite(warped[0]) && Double.isFinite(warped[1]));
            assertTrue(Math.abs(warped[0]) <= 1.0 && Math.abs(warped[1]) <= 1.0,
                    "domainWarp out of range: " + warped[0] + ", " + warped[1]);
        }
    }

    @Test
    void domainWarp_RejectsInvalidFrequency() {
        NoiseServiceImpl service = new NoiseServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> service.domainWarp(1, 1, 20.0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.domainWarp(1, 1, 20.0, -1.0));
    }

    @Test
    void domainWarp_RejectsNaNStrength() {
        NoiseServiceImpl service = new NoiseServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> service.domainWarp(1, 1, Double.NaN, 1.0));
    }

    // ---------- voronoi ----------

    @Test
    void voronoi_F1IsNonNegative() {
        NoiseServiceImpl service = new NoiseServiceImpl(41L);
        for (int i = 0; i < 300; i++) {
            assertTrue(service.voronoi(i * 0.9, i * 0.4, 0.8, 0) >= 0.0, "F1 must be >= 0");
        }
    }

    @Test
    void voronoi_F2MinusF1IsNonNegative() {
        NoiseServiceImpl service = new NoiseServiceImpl(43L);
        for (int i = 0; i < 300; i++) {
            double f1 = service.voronoi(i * 0.9, i * 0.4, 0.8, 0);
            double f2 = service.voronoi(i * 0.9, i * 0.4, 0.8, 1);
            double edge = service.voronoi(i * 0.9, i * 0.4, 0.8, 2);
            assertTrue(f2 >= f1, "F2 must be >= F1");
            assertTrue(edge >= 0.0, "F2-F1 must be >= 0");
        }
    }

    @Test
    void voronoi_IsDeterministic() {
        NoiseServiceImpl service = new NoiseServiceImpl(47L);
        assertEquals(service.voronoi(2.5, 1.5, 0.8, 0), service.voronoi(2.5, 1.5, 0.8, 0), EPS);
    }

    @Test
    void voronoi_RejectsJitterOutsideZeroToOne() {
        NoiseServiceImpl service = new NoiseServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> service.voronoi(1, 1, -0.1, 0));
        assertThrows(IllegalArgumentException.class, () -> service.voronoi(1, 1, 1.1, 0));
    }

    @Test
    void voronoi_RejectsUnknownReturnType() {
        NoiseServiceImpl service = new NoiseServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> service.voronoi(1, 1, 0.8, -1));
        assertThrows(IllegalArgumentException.class, () -> service.voronoi(1, 1, 0.8, 3));
    }

    // ---------- performance gate ----------

    @Test
    void benchmark_OneMillionEvaluationsUnderTwoSeconds() {
        NoiseServiceImpl service = new NoiseServiceImpl(53L);
        long start = System.nanoTime();
        double sum = 0.0;
        for (int i = 0; i < 1_000_000; i++) {
            sum += service.simplex(i * 0.001, i * 0.002, 1.0);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 2000, "1M simplex evals took " + elapsedMs + "ms (limit 2000ms)");
        assertTrue(Double.isFinite(sum), "noise sum must be finite");
    }
}

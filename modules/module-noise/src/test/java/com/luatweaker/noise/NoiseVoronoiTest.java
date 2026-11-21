package com.luatweaker.noise;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Voronoi cell noise: F1/F2 ordering, determinism and validation.
 */
class NoiseVoronoiTest {

    private static final double EPSILON = 1e-9;
    private static final double JITTER = 0.8;
    private static final int F1 = 0;
    private static final int F2 = 1;
    private static final int EDGE = 2;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void f1IsNonNegative() {
        NoiseServiceImpl service = new NoiseServiceImpl(41L);

        for (int i = 0; i < 300; i++) {
            Assertions.assertTrue(service.voronoi(i * 0.9, i * 0.4, JITTER, F1) >= 0.0,
                    "F1 must be >= 0");
        }
    }

    @Test
    void f2IsAtLeastF1AndTheEdgeDistanceIsNonNegative() {
        NoiseServiceImpl service = new NoiseServiceImpl(43L);

        for (int i = 0; i < 300; i++) {
            double f1 = service.voronoi(i * 0.9, i * 0.4, JITTER, F1);
            double f2 = service.voronoi(i * 0.9, i * 0.4, JITTER, F2);
            double edge = service.voronoi(i * 0.9, i * 0.4, JITTER, EDGE);
            Assertions.assertAll("voronoi return types must be ordered and non-negative",
                () -> Assertions.assertTrue(f2 >= f1, "F2 must be >= F1"),
                () -> Assertions.assertTrue(edge >= 0.0, "F2-F1 must be >= 0"));
        }
    }

    @Test
    void isDeterministic() {
        NoiseServiceImpl service = new NoiseServiceImpl(47L);

        Assertions.assertEquals(service.voronoi(2.5, 1.5, JITTER, F1),
                service.voronoi(2.5, 1.5, JITTER, F1), EPSILON,
                "the same coordinates must always produce the same voronoi value");
    }

    @Test
    void rejectsJitterOutsideZeroToOne() {
        NoiseServiceImpl service = new NoiseServiceImpl();

        Assertions.assertAll("jitter outside [0, 1] must be rejected",
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.voronoi(1, 1, -0.1, F1), "a negative jitter must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.voronoi(1, 1, 1.1, F1), "a jitter above one must be rejected"));
    }

    @Test
    void rejectsUnknownReturnType() {
        NoiseServiceImpl service = new NoiseServiceImpl();

        Assertions.assertAll("an unknown voronoi return type must be rejected",
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.voronoi(1, 1, JITTER, -1), "return type -1 must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.voronoi(1, 1, JITTER, 3), "return type 3 must be rejected"));
    }
}

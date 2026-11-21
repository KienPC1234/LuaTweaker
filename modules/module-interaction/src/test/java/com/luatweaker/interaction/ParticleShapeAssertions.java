package com.luatweaker.interaction;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shared assertions for the line/circle/helix shape tests (kept outside the test classes). */
final class ParticleShapeAssertions {

    private static final double EPSILON = 1.0E-9;

    private ParticleShapeAssertions() {
    }

    static void assertLineSamples() {
        List<double[]> points = ParticleShapes.line(0, 0, 0, 1, 0, 0, 0.25);
        assertEquals(5, points.size(), "a 1-block line at 0.25 spacing has 5 samples");
        assertPoint(points.get(0), 0, 0, 0);
        assertPoint(points.get(4), 1, 0, 0);
        assertEquals(0.5, points.get(2)[0], EPSILON, "middle sample must be at half the line");
    }

    static void assertLineRejectsSpacing() {
        assertThrows(IllegalArgumentException.class,
                () -> ParticleShapes.line(0, 0, 0, 1, 0, 0, 0),
                "spacing 0 would divide by zero and must be rejected");
        assertThrows(IllegalArgumentException.class,
                () -> ParticleShapes.line(0, 0, 0, 1, 0, 0, -1),
                "negative spacing must be rejected");
    }

    static void assertCircleSamples() {
        List<double[]> points = ParticleShapes.circle(4, 70, -2, 3, 8);
        assertEquals(8, points.size(), "the requested point count must be produced");
        for (double[] point : points) {
            double dx = point[0] - 4;
            double dz = point[2] + 2;
            assertEquals(3.0, Math.sqrt(dx * dx + dz * dz), 1.0E-6,
                    "every circle point must sit on the radius");
            assertEquals(70.0, point[1], EPSILON, "a horizontal circle must keep the center Y");
        }
    }

    static void assertCircleRejectsTooFewPoints() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ParticleShapes.circle(0, 0, 0, 1, 2));
        assertTrue(error.getMessage().contains("points"),
                "the error must name the invalid points argument");
    }

    static void assertHelixSamples() {
        List<double[]> points = ParticleShapes.helix(0, 10, 0, 2, 4, 2, 10);
        assertEquals(10, points.size(), "the requested point count must be produced");
        assertEquals(10.0, points.get(0)[1], EPSILON, "a helix starts at the base Y");
        assertEquals(14.0, points.get(9)[1], EPSILON, "a helix ends at base + height");
        assertPoint(points.get(0), 2, 10, 0);
        assertPoint(points.get(9), 2, 14, 0);
    }

    static void assertHelixRejectsZeroTurns() {
        assertThrows(IllegalArgumentException.class,
                () -> ParticleShapes.helix(0, 0, 0, 1, 2, 0, 8),
                "zero turns would collapse the helix into a line and must be rejected");
    }

    private static void assertPoint(double[] point, double x, double y, double z) {
        assertEquals(x, point[0], 1.0E-6, "X mismatch");
        assertEquals(y, point[1], 1.0E-6, "Y mismatch");
        assertEquals(z, point[2], 1.0E-6, "Z mismatch");
    }
}

package com.luatweaker.interaction;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Shared assertions for the sphere/Bezier shape tests (kept outside the test classes). */
final class ParticleBezierAssertions {

    private ParticleBezierAssertions() {
    }

    static void assertSphereSamples() {
        List<double[]> points = ParticleShapes.sphere(1, 2, 3, 5, 16);
        assertEquals(16, points.size(), "the requested point count must be produced");
        for (double[] point : points) {
            double dx = point[0] - 1;
            double dy = point[1] - 2;
            double dz = point[2] - 3;
            assertEquals(5.0, Math.sqrt(dx * dx + dy * dy + dz * dz), 1.0E-6,
                    "every sphere point must sit on the requested radius");
        }
    }

    static void assertBezierSamples() {
        List<double[]> control = List.of(
                new double[] { 0, 0, 0 },
                new double[] { 1, 2, 0 },
                new double[] { 2, 0, 0 });
        List<double[]> points = ParticleShapes.bezier(control, 5);
        assertEquals(5, points.size(), "the requested sample count must be produced");
        assertPoint(points.get(0), 0, 0, 0);
        assertPoint(points.get(4), 2, 0, 0);
        assertPoint(points.get(2), 1, 1, 0);
    }

    static void assertBezierRejectsIncompleteControlPoints() {
        assertThrows(IllegalArgumentException.class,
                () -> ParticleShapes.bezier(List.of(new double[] { 0, 0, 0 }), 4),
                "a Bezier needs at least two control points");
        assertThrows(IllegalArgumentException.class,
                () -> ParticleShapes.bezier(
                        List.of(new double[] { 0, 0, 0 }, new double[] { 1, 0 }), 4),
                "a control point without Z must be rejected");
    }

    static void assertBudgetEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> ParticleShapes.sphere(0, 0, 0, 1, ShapeGuard.MAX_POINTS + 1),
                "a single call must not exceed the documented point budget");
    }

    private static void assertPoint(double[] point, double x, double y, double z) {
        assertEquals(x, point[0], 1.0E-6, "X mismatch");
        assertEquals(y, point[1], 1.0E-6, "Y mismatch");
        assertEquals(z, point[2], 1.0E-6, "Z mismatch");
    }
}

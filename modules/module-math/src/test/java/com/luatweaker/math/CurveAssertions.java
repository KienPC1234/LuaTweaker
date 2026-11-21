package com.luatweaker.math;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Shared assertions for the curve tests (kept outside the test classes). */
final class CurveAssertions {

    private static final double EPSILON = 1.0E-9;

    private CurveAssertions() {
    }

    static void assertQuadraticBezier() {
        List<double[]> points = List.of(
                new double[] { 0, 0, 0 },
                new double[] { 1, 2, 0 },
                new double[] { 2, 0, 0 });
        assertPoint(Curves.bezier(0.0, points), 0, 0, 0);
        assertPoint(Curves.bezier(0.5, points), 1, 1, 0);
        assertPoint(Curves.bezier(1.0, points), 2, 0, 0);
    }

    static void assertCatmullEndpoints() {
        List<double[]> points = List.of(
                new double[] { 0, 0, 0 },
                new double[] { 1, 0, 0 },
                new double[] { 2, 0, 0 });
        assertPoint(Curves.catmullRom(0.0, points), 0, 0, 0);
        assertPoint(Curves.catmullRom(1.0, points), 2, 0, 0);
    }

    static void assertOutsideRangeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Curves.line(1.5, new double[] { 0, 0, 0 }, new double[] { 1, 0, 0 }),
                "t > 1 would extrapolate an entity outside the authored path");
        assertThrows(IllegalArgumentException.class,
                () -> Curves.line(-0.1, new double[] { 0, 0, 0 }, new double[] { 1, 0, 0 }),
                "negative t must be rejected");
        assertThrows(IllegalArgumentException.class,
                () -> Curves.line(Double.NaN, new double[] { 0, 0, 0 }, new double[] { 1, 0, 0 }),
                "NaN t must be rejected");
    }

    static void assertMissingPointsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Curves.bezier(0.5, List.of(new double[] { 0, 0, 0 })),
                "a Bezier needs at least two points");
        assertThrows(IllegalArgumentException.class,
                () -> Curves.catmullRom(0.5, List.of()),
                "Catmull-Rom needs at least two points");
    }

    private static void assertPoint(double[] point, double x, double y, double z) {
        assertEquals(x, point[0], EPSILON, "X mismatch");
        assertEquals(y, point[1], EPSILON, "Y mismatch");
        assertEquals(z, point[2], EPSILON, "Z mismatch");
    }
}

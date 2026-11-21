package com.luatweaker.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins the pure curve samplers behind the Lua {@code Curve} table. */
class CurvesTest {

    @Test
    void lineInterpolatesBetweenEndpoints() {
        double[] midpoint = Curves.line(0.5,
                new double[] { 0, 10, -2 },
                new double[] { 4, 20, 2 });

        assertEquals(2, midpoint[0], 1.0E-9, "the line midpoint X must be interpolated");
    }

    @Test
    void bezierFollowsAQuadraticCurve() {
        CurveAssertions.assertQuadraticBezier();
    }

    @Test
    void catmullRomHitsTheEndpoints() {
        CurveAssertions.assertCatmullEndpoints();
    }

    @Test
    void catmullRomWithTwoPointsIsLinear() {
        assertEquals(0.5, Curves.catmullRom(0.5, java.util.List.of(
                new double[] { 0, 0, 0 }, new double[] { 1, 0, 0 }))[0],
                1.0E-9, "two control points must interpolate linearly");
    }

    @Test
    void tOutsideTheUnitRangeIsRejected() {
        CurveAssertions.assertOutsideRangeRejected();
    }

    @Test
    void missingControlPointsAreRejected() {
        CurveAssertions.assertMissingPointsRejected();
    }
}

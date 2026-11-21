package com.luatweaker.client.tween;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link EasingHelper}: every style/direction starts and ends on the exact endpoints, progress
 * is clamped, OUT mirrors IN, and the curves are genuinely distinct.
 */
class EasingHelperTest {

    private static final double START = 3.0;
    private static final double END = 17.0;

    @Test
    void everyStyleStartsAtTheStartValue() {
        for (EasingHelper.EasingStyle style : EasingHelper.EasingStyle.values()) {
            for (EasingHelper.EasingDirection direction : EasingHelper.EasingDirection.values()) {
                assertEquals(START, EasingHelper.interpolate(START, END, 0.0, style, direction),
                        1e-9, style + "/" + direction + " must start at the start value");
            }
        }
    }

    @Test
    void everyStyleEndsAtTheEndValue() {
        for (EasingHelper.EasingStyle style : EasingHelper.EasingStyle.values()) {
            for (EasingHelper.EasingDirection direction : EasingHelper.EasingDirection.values()) {
                assertEquals(END, EasingHelper.interpolate(START, END, 1.0, style, direction),
                        1e-9, style + "/" + direction + " must end at the end value");
            }
        }
    }

    @Test
    void negativeProgressClampsToTheStartValue() {
        assertEquals(0.0, EasingHelper.interpolate(0.0, 10.0, -4.0,
                        EasingHelper.EasingStyle.QUAD, EasingHelper.EasingDirection.IN), 1e-9,
                "progress below 0 must clamp to the start instead of overshooting backwards");
    }

    @Test
    void progressAboveOneClampsToTheEndValue() {
        assertEquals(10.0, EasingHelper.interpolate(0.0, 10.0, 4.0,
                        EasingHelper.EasingStyle.QUAD, EasingHelper.EasingDirection.IN), 1e-9,
                "progress above 1 must clamp to the end instead of overshooting forwards");
    }

    @Test
    void outIsTheMirrorOfIn() {
        for (EasingHelper.EasingStyle style : EasingHelper.EasingStyle.values()) {
            for (double progress = 0.1; progress < 1.0; progress += 0.1) {
                double in = EasingHelper.applyEasing(progress, style,
                        EasingHelper.EasingDirection.IN);
                double out = EasingHelper.applyEasing(1.0 - progress, style,
                        EasingHelper.EasingDirection.OUT);
                assertEquals(1.0 - in, out, 1e-9, style + " OUT must mirror IN");
            }
        }
    }

    @Test
    void linearAtTheMidpointIsExactlyHalf() {
        assertEquals(0.5, EasingHelper.applyEasing(0.5, EasingHelper.EasingStyle.LINEAR,
                        EasingHelper.EasingDirection.IN), 1e-9,
                "a linear ease must be exactly halfway at the midpoint");
    }

    @Test
    void sineEaseInIsBelowLinearAtTheMidpoint() {
        double linear = EasingHelper.applyEasing(0.5, EasingHelper.EasingStyle.LINEAR,
                EasingHelper.EasingDirection.IN);
        double sine = EasingHelper.applyEasing(0.5, EasingHelper.EasingStyle.SINE,
                EasingHelper.EasingDirection.IN);
        assertTrue(sine < linear, "an ease-in curve is below linear at the midpoint");
    }

    @Test
    void cubicEaseInStartsSlowerThanSine() {
        double sine = EasingHelper.applyEasing(0.5, EasingHelper.EasingStyle.SINE,
                EasingHelper.EasingDirection.IN);
        double cubic = EasingHelper.applyEasing(0.5, EasingHelper.EasingStyle.CUBIC,
                EasingHelper.EasingDirection.IN);
        assertTrue(cubic < sine, "cubic starts slower than sine");
    }

    @Test
    void quadInOutCrossesAtTheMidpoint() {
        assertEquals(0.5, EasingHelper.applyEasing(0.5, EasingHelper.EasingStyle.QUAD,
                        EasingHelper.EasingDirection.IN_OUT), 1e-9,
                "a symmetric in-out quad curve must hit 0.5 at the midpoint");
    }

    @Test
    void quadEaseInIsQuadratic() {
        assertEquals(0.0625, EasingHelper.applyEasing(0.25, EasingHelper.EasingStyle.QUAD,
                        EasingHelper.EasingDirection.IN), 1e-9,
                "quad ease-in at t=0.25 must be t*t = 0.0625");
    }
}

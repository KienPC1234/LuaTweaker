package com.luatweaker.client.tween;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EasingHelperTest {

    @Test
    public void everyStyleStartsAtStartAndEndsAtEnd() {
        for (EasingHelper.EasingStyle style : EasingHelper.EasingStyle.values()) {
            for (EasingHelper.EasingDirection direction : EasingHelper.EasingDirection.values()) {
                double atStart = EasingHelper.interpolate(3.0, 17.0, 0.0, style, direction);
                double atEnd = EasingHelper.interpolate(3.0, 17.0, 1.0, style, direction);
                assertEquals(3.0, atStart, 1e-9,
                        style + "/" + direction + " must start at the start value");
                assertEquals(17.0, atEnd, 1e-9,
                        style + "/" + direction + " must end at the end value");
            }
        }
    }

    @Test
    public void progressIsClamped() {
        assertEquals(0.0, EasingHelper.interpolate(0.0, 10.0, -4.0,
                EasingHelper.EasingStyle.QUAD, EasingHelper.EasingDirection.IN), 1e-9);
        assertEquals(10.0, EasingHelper.interpolate(0.0, 10.0, 4.0,
                EasingHelper.EasingStyle.QUAD, EasingHelper.EasingDirection.IN), 1e-9);
    }

    @Test
    public void outIsTheMirrorOfIn() {
        for (EasingHelper.EasingStyle style : EasingHelper.EasingStyle.values()) {
            for (double t = 0.1; t < 1.0; t += 0.1) {
                double in = EasingHelper.applyEasing(t, style, EasingHelper.EasingDirection.IN);
                double out = EasingHelper.applyEasing(1.0 - t, style, EasingHelper.EasingDirection.OUT);
                assertEquals(1.0 - in, out, 1e-9, style + " OUT must mirror IN");
            }
        }
    }

    @Test
    public void easingStylesAreDistinctAtTheMidpoint() {
        double sine = EasingHelper.applyEasing(0.5, EasingHelper.EasingStyle.SINE,
                EasingHelper.EasingDirection.IN);
        double cubic = EasingHelper.applyEasing(0.5, EasingHelper.EasingStyle.CUBIC,
                EasingHelper.EasingDirection.IN);
        double linear = EasingHelper.applyEasing(0.5, EasingHelper.EasingStyle.LINEAR,
                EasingHelper.EasingDirection.IN);

        assertEquals(0.5, linear, 1e-9);
        assertTrue(sine < linear, "an ease-in curve is below linear at the midpoint");
        assertTrue(cubic < sine, "cubic starts slower than sine");
    }

    @Test
    public void quadInOutCrossesAtTheMidpoint() {
        assertEquals(0.5, EasingHelper.applyEasing(0.5, EasingHelper.EasingStyle.QUAD,
                EasingHelper.EasingDirection.IN_OUT), 1e-9);
        assertEquals(0.0625, EasingHelper.applyEasing(0.25, EasingHelper.EasingStyle.QUAD,
                EasingHelper.EasingDirection.IN), 1e-9);
    }
}

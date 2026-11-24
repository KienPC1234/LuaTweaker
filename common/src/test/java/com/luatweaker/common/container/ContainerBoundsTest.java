package com.luatweaker.common.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies container widget and label bounds clamping inside the 176px beveled panel. */
class ContainerBoundsTest {

    private static final int PANEL_W = ContainerLayout.PANEL_WIDTH;
    private static final int PANEL_H = ContainerLayout.imageHeight(3);

    @Test
    void clampsOverflowingWidgetRightEdgeInsideBeveledBorder() {
        int clampedX = ContainerBounds.clampX(150, 48, PANEL_W);

        assertEquals(PANEL_W - ContainerBounds.INNER_INSET - 48, clampedX,
                "a widget extending past 170px must shift left inside the beveled frame");
    }

    @Test
    void clampsNegativeCoordinatesToInnerInset() {
        int clampedX = ContainerBounds.clampX(-12, 20, PANEL_W);
        int clampedY = ContainerBounds.clampY(-8, 20, PANEL_H);

        assertTrue(clampedX == ContainerBounds.INNER_INSET && clampedY == ContainerBounds.INNER_INSET,
                "negative widget offsets must clamp to the 6px inner frame inset");
    }

    @Test
    void clampsOversizedDimensionsToInteriorSpan() {
        int clampedW = ContainerBounds.clampWidth(320, PANEL_W);
        int clampedH = ContainerBounds.clampHeight(400, PANEL_H);

        assertTrue(clampedW == PANEL_W - ContainerBounds.INNER_INSET * 2
                        && clampedH == PANEL_H - ContainerBounds.INNER_INSET * 2,
                "oversized widget dimensions must fit strictly within the panel interior");
    }

    @Test
    void computesSafeLabelWidthsForLeftCenterAndRightAlignment() {
        int leftAvail = ContainerBounds.maxLabelWidth("left", 112, PANEL_W);
        int centerAvail = ContainerBounds.maxLabelWidth("center", 0, PANEL_W);
        int rightAvail = ContainerBounds.maxLabelWidth("right", 10, PANEL_W);

        assertTrue(leftAvail == 58 && centerAvail == 164 && rightAvail == 160,
                "label width budgets must keep left/center/right text inside [6..170]");
    }
}

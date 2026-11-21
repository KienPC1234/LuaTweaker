package com.luatweaker.platform.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The container GUI geometry must exactly match the vanilla chest layout:
 * 9-col grids start at x=8, smaller grids are centered, and nothing may
 * overlap the 176px panel.
 */
class ContainerLayoutGridTest {

    private static final int PANEL_WIDTH = 176;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_SPRITE = 16;

    @Test
    void nineColumnGridStartsAtEight() {
        assertEquals(8, ContainerLayout.containerStartX(9),
                "a full 9-column chest row must start at x=8 like vanilla");
    }

    @Test
    void sixColumnGridIsCentered() {
        assertEquals(35, ContainerLayout.containerStartX(6),
                "a 6-column grid must center itself in the panel");
    }

    @Test
    void threeColumnGridMatchesBarrelPosition() {
        assertEquals(62, ContainerLayout.containerStartX(3),
                "a 3-column grid must sit at the vanilla barrel position");
    }

    @Test
    void gridFitsInsidePanelForEveryWidth() {
        for (int cols = 1; cols <= 9; cols++) {
            int lastSlotX = ContainerLayout.containerStartX(cols) + (cols - 1) * SLOT_SIZE;

            assertTrue(lastSlotX + SLOT_SPRITE <= PANEL_WIDTH,
                    cols + "-col grid must fit the panel");
        }
    }
}

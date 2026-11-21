package com.luatweaker.platform.client.luamods;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mod-list layout math must keep buttons inside narrow windows. */
class LuamodLayoutsTest {

    private static final int WIDE_SCREEN = 1200;
    private static final int NARROW_SCREEN = 300;

    @Test
    void buttonWidthFillsWideScreens() {
        assertEquals(150, LuamodLayouts.buttonWidth(WIDE_SCREEN, 5),
                "five buttons on a wide screen must share the row evenly");
    }

    @Test
    void wideScreenKeepsOneRow() {
        assertTrue(LuamodLayouts.fitsRow(WIDE_SCREEN, 5),
                "wide screens keep one row");
    }

    @Test
    void narrowScreenWrapsFiveButtons() {
        assertFalse(LuamodLayouts.fitsRow(NARROW_SCREEN, 5),
                "narrow screens must wrap");
    }

    @Test
    void narrowScreenKeepsThreeButtonsInOneRow() {
        assertTrue(LuamodLayouts.fitsRow(NARROW_SCREEN, 3),
                "three buttons still fit narrow");
    }

    @Test
    void narrowScreenKeepsTwoButtonsInOneRow() {
        assertTrue(LuamodLayouts.fitsRow(NARROW_SCREEN, 2),
                "two buttons still fit narrow");
    }

    @Test
    void wrappedRowOfThreeStaysInsideTheWindow() {
        int buttonWidth = LuamodLayouts.buttonWidth(NARROW_SCREEN, 3);
        int left = LuamodLayouts.rowLeft(NARROW_SCREEN, 3, buttonWidth);
        int right = LuamodLayouts.buttonX(left, 2, buttonWidth) + buttonWidth;

        assertTrue(left >= 0 && right <= NARROW_SCREEN,
                "wrapped row of three fits: " + left + ".." + right);
    }

    @Test
    void wrappedRowOfTwoStaysInsideTheWindow() {
        int buttonWidth = LuamodLayouts.buttonWidth(NARROW_SCREEN, 2);
        int left = LuamodLayouts.rowLeft(NARROW_SCREEN, 2, buttonWidth);
        int right = LuamodLayouts.buttonX(left, 1, buttonWidth) + buttonWidth;

        assertTrue(left >= 0 && right <= NARROW_SCREEN,
                "wrapped row of two fits: " + left + ".." + right);
    }

    @Test
    void buttonXStepsByWidthPlusGap() {
        assertEquals(LuamodLayouts.GAP + 100, LuamodLayouts.buttonX(0, 1, 100),
                "the second button must start one width plus one gap after the row start");
    }
}

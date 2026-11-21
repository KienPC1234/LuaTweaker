package com.luatweaker.platform.container;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Anchor math: nine points, offsets, label alignment and placeholder text. */
class GuiAnchorTest {

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 168;

    @Nested
    class ResolveX {

        @Test
        void topLeftKeepsOffset() {
            assertEquals(44, GuiAnchor.resolveX("topleft", PANEL_W, 18, 44),
                    "topleft must keep the author x");
        }

        @Test
        void centerIsCentered() {
            assertEquals((PANEL_W - 18) / 2 + 5, GuiAnchor.resolveX("center", PANEL_W, 18, 5),
                    "center must middle the widget then add the offset");
        }

        @Test
        void rightPinsToRightEdge() {
            assertEquals(PANEL_W - 16 - 8, GuiAnchor.resolveX("topright", PANEL_W, 16, 8),
                    "topright must measure inward from the right edge");
        }

        @Test
        void unknownAnchorFallsBackToTopLeft() {
            assertEquals(44, GuiAnchor.resolveX("bogus", PANEL_W, 18, 44),
                    "a typo must degrade to the historical behavior, never throw");
        }
    }

    @Nested
    class ResolveY {

        @Test
        void topKeepsOffset() {
            assertEquals(30, GuiAnchor.resolveY("top", PANEL_W, 18, 30),
                    "top must keep the author y");
        }

        @Test
        void middleIsMiddle() {
            assertEquals((PANEL_H - 50) / 2, GuiAnchor.resolveY("left", PANEL_H, 50, 0),
                    "left must middle the widget vertically");
        }

        @Test
        void bottomPinsToBottomEdge() {
            assertEquals(PANEL_H - 20 - 4, GuiAnchor.resolveY("bottom", PANEL_H, 20, 4),
                    "bottom must measure up from the bottom edge");
        }
    }

    @Nested
    class LabelAlign {

        @Test
        void leftKeepsX() {
            assertEquals(40, GuiAnchor.labelX("left", PANEL_W, 60, 40),
                    "left alignment must keep the author x");
        }

        @Test
        void centerMiddlesText() {
            assertEquals((PANEL_W - 60) / 2 + 3, GuiAnchor.labelX("center", PANEL_W, 60, 3),
                    "center must middle the measured text then add the offset");
        }

        @Test
        void rightPinsToRightEdge() {
            assertEquals(PANEL_W - 8 - 60, GuiAnchor.labelX("right", PANEL_W, 60, 8),
                    "right must end the text at the margin");
        }
    }

    @Nested
    class TextFormat {

        @Test
        void linesSplitOnNewline() {
            assertEquals(2, GuiAnchor.lines("first\nsecond").size(),
                    "one newline must yield two lines");
        }

        @Test
        void placeholdersResolve() {
            assertEquals("Heat 120 / 500",
                    GuiAnchor.placeholders("Heat {cur} / {max}",
                            key -> "cur".equals(key) ? "120" : "500"),
                    "known keys must be substituted");
        }

        @Test
        void unknownKeysStayVisible() {
            assertEquals("Heat {nope}",
                    GuiAnchor.placeholders("Heat {nope}", key -> null),
                    "a typo must stay visible instead of silently vanishing");
        }
    }
}

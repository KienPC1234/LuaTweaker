package com.luatweaker.common.client.luamods;

import net.minecraft.client.gui.Font;

/**
 * Shared layout math for the LuaMod manager screens. Vanilla windows can be
 * narrow (small GUI scale) or short, so every row computes its widths from the
 * live screen size and every string is trimmed to pixels, never characters.
 */
public final class LuamodLayouts {
    private LuamodLayouts() {}

    static final int MARGIN = 16;
    static final int GAP = 6;
    static final int MIN_BUTTON = 70;
    static final int MAX_BUTTON = 150;

    /** Even button width for a row of {@code count} buttons, clamped to sanity. */
    public static int buttonWidth(int screenWidth, int count) {
        int avail = screenWidth - MARGIN * 2 - GAP * Math.max(0, count - 1);
        return Math.clamp(avail / Math.max(1, count), MIN_BUTTON, MAX_BUTTON);
    }

    /** True when {@code count} minimum-size buttons fit side by side. */
    public static boolean fitsRow(int screenWidth, int count) {
        return count * MIN_BUTTON + GAP * Math.max(0, count - 1) <= screenWidth - MARGIN * 2;
    }

    /** Left edge of a centered button row. */
    public static int rowLeft(int screenWidth, int count, int buttonWidth) {
        return screenWidth / 2 - (count * buttonWidth + GAP * Math.max(0, count - 1)) / 2;
    }

    /** X of button {@code index} in a row laid out with {@link #rowLeft}. */
    public static int buttonX(int rowLeft, int index, int buttonWidth) {
        return rowLeft + index * (buttonWidth + GAP);
    }

    /** Trims to pixel width with an ellipsis; never returns a broken layout. */
    public static String trim(Font font, String text, int maxWidth) {
        if (text == null) { return ""; }
        if (maxWidth <= 0 || font == null) { return ""; }
        if (font.width(text) <= maxWidth) { return text; }
        int ellipsis = font.width("...");
        if (maxWidth <= ellipsis + 4) { return ""; }
        return font.plainSubstrByWidth(text, maxWidth - ellipsis) + "...";
    }
}

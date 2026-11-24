package com.luatweaker.common.client.luamods;

import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared layout math for the LuaMod manager screens. Vanilla windows can be
 * narrow (large GUI scale) or short, so every row computes its widths from the
 * live screen size and every string is trimmed or wrapped to pixels.
 */
public final class LuamodLayouts {
    private LuamodLayouts() {}

    static final int MARGIN = 16;
    static final int GAP = 6;
    static final int MIN_BUTTON = 70;
    static final int MAX_BUTTON = 150;
    private static final int MIN_WRAP_WIDTH = 8;
    public static final int FOOTER_BOTTOM_OFFSET = 26;
    public static final int BUTTON_ROW_STEP = 24;

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
        if (text == null || maxWidth <= 0 || font == null) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsis = font.width("...");
        if (maxWidth <= ellipsis + 4) {
            return "";
        }
        return font.plainSubstrByWidth(text, maxWidth - ellipsis) + "...";
    }

    /**
     * Wraps {@code text} into at most {@code maxLines} pixel-bounded lines,
     * trimming the final line with an ellipsis if text still remains.
     */
    public static List<String> wrapLines(Font font, String text, int maxWidth, int maxLines) {
        if (!canWrap(font, text, maxWidth, maxLines)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        String remaining = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        while (!remaining.isEmpty()) {
            int lastLineIndex = maxLines - 1;
            if (out.size() >= lastLineIndex) {
                out.add(trim(font, remaining.replace('\n', ' '), maxWidth));
                break;
            }
            String nextSlice = takeNextWrappedLine(font, remaining, maxWidth);
            out.add(nextSlice);
            remaining = remaining.substring(nextSlice.length()).stripLeading();
        }
        return out;
    }

    private static boolean canWrap(Font font, String text, int maxWidth, int maxLines) {
        return font != null && text != null && !text.isEmpty() && maxWidth > MIN_WRAP_WIDTH && maxLines > 0;
    }

    private static String takeNextWrappedLine(Font font, String remaining, int maxWidth) {
        int newline = remaining.indexOf('\n');
        String paragraph = newline >= 0 ? remaining.substring(0, newline) : remaining;
        if (font.width(paragraph) <= maxWidth) {
            return paragraph;
        }
        String rawFit = font.plainSubstrByWidth(paragraph, maxWidth);
        int lastSpace = rawFit.lastIndexOf(' ');
        if (lastSpace > rawFit.length() / 3) {
            return rawFit.substring(0, lastSpace);
        }
        return rawFit.isEmpty() ? paragraph.substring(0, 1) : rawFit;
    }
}

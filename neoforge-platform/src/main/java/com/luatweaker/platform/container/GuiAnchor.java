package com.luatweaker.platform.container;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure anchor math for machine-GUI widgets: label alignment, bar/button anchoring
 * to the panel, multi-line splitting and {Key} placeholder resolution.
 *
 * <p>No Minecraft types on purpose: the screens delegate every coordinate
 * decision here, so layout stays unit-testable and Lua authors get predictable
 * results. Unknown anchors and alignments fall back to top-left/left instead of
 * throwing, so a typo degrades to the historical behavior.</p>
 */
public final class GuiAnchor {

    /** Horizontal alignment names shared with label specs. */
    public static final String ALIGN_LEFT = "left";
    public static final String ALIGN_CENTER = "center";
    public static final String ALIGN_RIGHT = "right";

    /** Panel-relative placeholder names ({@code {Key}} resolved from synced values). */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");

    private GuiAnchor() {
    }

    /**
     * Resolves a widget's left edge inside the panel.
     *
     * @param anchor  topleft/top/topright (offsetX), left/center/right handled per
     *                group below, anything else behaves like topleft
     * @param panelW  panel width (176 for vanilla geometry)
     * @param widgetW widget width in pixels
     * @param offsetX author offset added after anchoring
     */
    public static int resolveX(@NotNull String anchor, int panelW, int widgetW, int offsetX) {
        String place = normalized(anchor);
        if (isRightColumn(place)) {
            return panelW - widgetW - offsetX;
        }
        if (isCenterColumn(place)) {
            return (panelW - widgetW) / 2 + offsetX;
        }
        return offsetX;
    }

    /**
     * Resolves a widget's top edge inside the panel. The row comes from the
     * anchor name (top*, *left/top/topright → top band; *center/right → middle;
     * bottom* → bottom band), independent of the column used for x.
     * @param panelH  panel height (114 + rows * 18 for vanilla geometry)
     * @param widgetH widget height in pixels
     * @param offsetY author offset added after anchoring
     */
    public static int resolveY(@NotNull String anchor, int panelH, int widgetH, int offsetY) {
        String place = normalized(anchor);
        if (isBottomRow(place)) {
            return panelH - widgetH - offsetY;
        }
        if (isCenterRow(place)) {
            return (panelH - widgetH) / 2 + offsetY;
        }
        return offsetY;
    }

    /**
     * Resolves a label's draw x for an alignment.
     *
     * @param align  "left" (default), "center" or "right" against the panel width
     * @param panelW panel width
     * @param textW  measured text width in pixels
     * @param x      author offset (added for left/center, margin for right)
     */
    public static int labelX(@NotNull String align, int panelW, int textW, int x) {
        String mode = align.trim().toLowerCase(java.util.Locale.ROOT);
        if (ALIGN_CENTER.equals(mode)) {
            return (panelW - textW) / 2 + x;
        }
        if (ALIGN_RIGHT.equals(mode)) {
            return panelW - x - textW;
        }
        return x;
    }

    /** Splits label text into lines (\n separated, trailing empties kept). */
    public static @NotNull List<String> lines(@NotNull String text) {
        return Arrays.asList(text.split("\n", -1));
    }

    /**
     * Replaces {Key} placeholders using the lookup; unknown keys are left intact
     * so a typo stays visible instead of silently vanishing.
     */
    public static @NotNull String placeholders(@NotNull String text,
            @NotNull Function<String, String> lookup) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (matcher.find()) {
            String replacement = lookup.apply(matcher.group(1));
            matcher.appendReplacement(out, replacement != null
                    ? Matcher.quoteReplacement(replacement) : matcher.group(0));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static @NotNull String normalized(@NotNull String anchor) {
        String place = anchor.trim().toLowerCase(java.util.Locale.ROOT);
        return place.isEmpty() ? "topleft" : place;
    }

    private static boolean isRightColumn(@NotNull String anchor) {
        return "topright".equals(anchor) || "right".equals(anchor) || "bottomright".equals(anchor);
    }

    private static boolean isCenterColumn(@NotNull String anchor) {
        return "top".equals(anchor) || "center".equals(anchor) || "bottom".equals(anchor);
    }

    private static boolean isBottomRow(@NotNull String anchor) {
        return "bottomleft".equals(anchor) || "bottom".equals(anchor) || "bottomright".equals(anchor);
    }

    private static boolean isCenterRow(@NotNull String anchor) {
        return "left".equals(anchor) || "center".equals(anchor) || "right".equals(anchor);
    }
}

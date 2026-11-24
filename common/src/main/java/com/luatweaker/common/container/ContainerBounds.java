package com.luatweaker.common.container;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Pure bounds-clamping math for container GUI widgets and labels so author
 * coordinates never bleed across the 4px beveled panel border.
 */
public final class ContainerBounds {

    /** Safe inner inset inside the 4px beveled vanilla container frame. */
    public static final int INNER_INSET = 6;
    private static final int MIN_WIDGET_SIZE = 8;

    private ContainerBounds() {
    }

    public static int clampWidth(int requestedWidth, int panelWidth) {
        int maxAllowed = Math.max(MIN_WIDGET_SIZE, panelWidth - INNER_INSET * 2);
        return Math.clamp(requestedWidth, MIN_WIDGET_SIZE, maxAllowed);
    }

    public static int clampHeight(int requestedHeight, int panelHeight) {
        int maxAllowed = Math.max(MIN_WIDGET_SIZE, panelHeight - INNER_INSET * 2);
        return Math.clamp(requestedHeight, MIN_WIDGET_SIZE, maxAllowed);
    }

    public static int clampX(int relX, int widgetWidth, int panelWidth) {
        int maxLeft = Math.max(INNER_INSET, panelWidth - INNER_INSET - widgetWidth);
        return Math.clamp(relX, INNER_INSET, maxLeft);
    }

    public static int clampY(int relY, int widgetHeight, int panelHeight) {
        int maxTop = Math.max(INNER_INSET, panelHeight - INNER_INSET - widgetHeight);
        return Math.clamp(relY, INNER_INSET, maxTop);
    }

    /**
     * Computes the maximum pixel width a label may occupy inside {@code panelWidth}
     * without crossing {@link #INNER_INSET} on either side.
     */
    public static int maxLabelWidth(@NotNull String align, int relX, int panelWidth) {
        String mode = align.trim().toLowerCase(Locale.ROOT);
        int rightLimit = panelWidth - INNER_INSET;
        if (GuiAnchor.ALIGN_CENTER.equals(mode)) {
            int centerX = panelWidth / 2 + relX;
            int halfSpan = Math.min(centerX - INNER_INSET, rightLimit - centerX);
            return Math.max(0, halfSpan * 2);
        }
        if (GuiAnchor.ALIGN_RIGHT.equals(mode)) {
            int rightEdge = Math.min(rightLimit, panelWidth - relX);
            return Math.max(0, rightEdge - INNER_INSET);
        }
        int leftEdge = Math.max(INNER_INSET, relX);
        return Math.max(0, rightLimit - leftEdge);
    }
}

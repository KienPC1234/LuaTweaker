package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

/**
 * A text label rendered in a container screen.
 *
 * @param x          panel x offset (meaning depends on {@code align})
 * @param y          panel y offset (top of the first line)
 * @param textOrVar  static text, template with {Key} placeholders resolved from
 *                   synced block-entity values, or multi-line text with \n
 * @param color      ARGB or RGB text color
 * @param align      "left" (default), "center" or "right" against the 176-wide panel
 * @param scale      text scale (1.0 = vanilla, clamped 0.5..3.0)
 * @param shadow     drop shadow for readability over bars and dark art
 */
@LuaDoc(description = "Text label widget rendered on a machine container screen.")
public record MachineLabelSpec(
        int x,
        int y,
        String textOrVar,
        int color,
        String align,
        float scale,
        boolean shadow
) {
    public MachineLabelSpec(int x, int y, String textOrVar, int color) {
        this(x, y, textOrVar, color, "left", 1.0f, false);
    }
}

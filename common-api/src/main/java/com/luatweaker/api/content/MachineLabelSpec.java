package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

/**
 * A text label rendered in a container screen.
 *
 * @param x          panel x offset
 * @param y          panel y offset
 * @param textOrVar  static text or template string with {Key} placeholders
 * @param color      ARGB or RGB text color
 */
@LuaDoc(description = "Text label widget rendered on a machine container screen.")
public record MachineLabelSpec(int x, int y, String textOrVar, int color) {
}

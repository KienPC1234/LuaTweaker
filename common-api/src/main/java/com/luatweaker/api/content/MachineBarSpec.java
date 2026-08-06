package com.luatweaker.api.content;

/**
 * A bar element rendered on top of a Lua-configured container GUI.
 *
 * <p>Bars show a value synced from the block entity while the menu is open:
 * <ul>
 *   <li>{@code "energy"} — stored FE / capacity (green by default)</li>
 *   <li>{@code "fluid"} — stored fluid amount / capacity (blue by default)</li>
 *   <li>{@code "progress"} — Lua progress value 0..1 (amber by default)</li>
 * </ul>
 * A wide bar fills horizontally, a tall bar fills vertically.
 *
 * @param id     unique bar id (unused by rendering, useful for Lua tooltips later)
 * @param x      panel x (0 = left edge of the 176-wide panel)
 * @param y      panel y (0 = top edge)
 * @param width  bar width in pixels
 * @param height bar height in pixels
 * @param source one of "energy", "fluid", "progress"
 * @param color  ARGB color (e.g. 0xFF00E676); 0 or negative = source default
 */
public record MachineBarSpec(String id, int x, int y, int width, int height, String source, int color) {
}

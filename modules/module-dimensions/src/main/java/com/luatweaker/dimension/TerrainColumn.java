package com.luatweaker.dimension;

import org.jetbrains.annotations.NotNull;

/**
 * Pure per-column terrain logic shared by the chunk generator and tests:
 * which block occupies a depth of a ground column. Custom layering is the
 * job of the Lua strata picker ({@code SetStrataPicker}); this class only
 * supplies the vanilla-like surface/subsurface/filler stack.
 */
public final class TerrainColumn {

    private TerrainColumn() {}

    /**
     * Returns the block id for a position in a column whose surface is at
     * {@code surfaceY}: surface -> subsurface -> filler, with an optional
     * bedrock floor at the bottom.
     */
    /** Depth below the surface that uses the subsurface block. */
    private static final int SUBSURFACE_DEPTH = 1;

    @NotNull
    public static String blockAtDepth(@NotNull DimensionConfig cfg, int surfaceY, int y) {
        if (cfg.hasBedrock() && y == cfg.minHeight()) {
            return "minecraft:bedrock";
        }
        int depth = surfaceY - y;
        if (depth == 0) {
            return cfg.surfaceBlock();
        }
        if (depth == SUBSURFACE_DEPTH) {
            return cfg.subsurfaceBlock();
        }
        return cfg.fillerBlock();
    }

    /**
     * Resolves a raw surface height to a block Y inside the build range.
     *
     * <p>Values below the floor become {@code minY - 1} ("no ground") instead of being
     * clamped onto the bottom layer: a clamped bottom block made void columns look solid to
     * height queries, so spawn/teleport checks accepted the very bottom of a floating world.
     * Shared by the chunk generator and the query API so both report the same Y.</p>
     */
    public static int clampHeight(int rawHeight, int minY, int maxY) {
        if (rawHeight < minY + 1) {
            return minY - 1;
        }
        return Math.clamp(rawHeight, minY + 1, maxY - 1);
    }
}

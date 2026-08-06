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
    @NotNull
    public static String blockAtDepth(@NotNull DimensionConfig cfg, int surfaceY, int y) {
        if (cfg.hasBedrock() && y == cfg.minHeight()) {
            return "minecraft:bedrock";
        }
        int depth = surfaceY - y;
        if (depth == 0) {
            return cfg.surfaceBlock();
        }
        if (depth == 1) {
            return cfg.subsurfaceBlock();
        }
        return cfg.fillerBlock();
    }
}

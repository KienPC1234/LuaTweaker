package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parses the vertical world-shape section of a dimension config
 * (sea level, height limits, surface blocks).
 */
final class DimensionBoundsParser {

    private DimensionBoundsParser() {
    }

    record VerticalBounds(int seaLevel, int minHeight, int maxHeight, int logicalHeight) {
    }

    record SurfaceBlocks(String surface, String subsurface, String filler, String water) {
    }

    @Nullable
    static Integer validateSeaLevel(@NotNull ILuaTable table, @NotNull String dimensionId,
            @NotNull DimensionConfig config) {
        Double raw = LuaTableValues.optionalNumber(table, "seaLevel");
        Integer seaLevel = raw == null ? null : (int) Math.floor(raw);
        if (seaLevel != null && (seaLevel <= config.minHeight() || seaLevel >= config.maxHeight())) {
            throw new IllegalArgumentException("terrain spec seaLevel (" + seaLevel + ") for '" + dimensionId
                    + "' must be between minHeight (" + config.minHeight() + ") and maxHeight ("
                    + config.maxHeight() + ")");
        }
        return seaLevel;
    }

    static VerticalBounds parseVerticalBounds(@NotNull ILuaTable t) {
        int seaLevel = LuaTableValues.intRange(t, "seaLevel", DimensionConfig.DEFAULT_SEA_LEVEL, -1000, 1000);
        int minHeight = LuaTableValues.intRange(t, "minHeight", DimensionConfig.DEFAULT_MIN_HEIGHT,
                DimensionConfig.MIN_WORLD_Y, DimensionConfig.MAX_WORLD_TOP - DimensionConfig.MIN_WORLD_HEIGHT);
        int maxHeight = LuaTableValues.intRange(t, "maxHeight", DimensionConfig.DEFAULT_MAX_HEIGHT,
                DimensionConfig.MIN_WORLD_Y + DimensionConfig.MIN_WORLD_HEIGHT, DimensionConfig.MAX_WORLD_TOP);
        if (minHeight >= maxHeight) {
            throw new IllegalArgumentException("minHeight (" + minHeight + ") must be < maxHeight (" + maxHeight + ")");
        }
        int height = maxHeight - minHeight;
        checkWorldTop(minHeight, maxHeight, height);
        if (seaLevel <= minHeight || seaLevel >= maxHeight) {
            throw new IllegalArgumentException("seaLevel (" + seaLevel + ") must be between minHeight (" + minHeight
                    + ") and maxHeight (" + maxHeight + ")");
        }
        int logicalHeight = LuaTableValues.intRange(t, "logicalHeight", height, 0, DimensionConfig.MAX_WORLD_HEIGHT);
        if (logicalHeight > height) {
            throw new IllegalArgumentException("logicalHeight (" + logicalHeight
                    + ") must be <= height (" + height + "), as required by DimensionType");
        }
        return new VerticalBounds(seaLevel, minHeight, maxHeight, logicalHeight);
    }

    private static void checkWorldTop(int minHeight, int maxHeight, int height) {
        // Vanilla DimensionType constructor rejects these; validating here turns a broken
        // datapack into a clear Lua error instead of a world-load crash.
        if (minHeight % DimensionConfig.WORLD_SECTION != 0) {
            throw new IllegalArgumentException("minHeight must be a multiple of 16 (vanilla DimensionType), got: "
                    + minHeight);
        }
        if (maxHeight % DimensionConfig.WORLD_SECTION != 0) {
            throw new IllegalArgumentException("maxHeight must be a multiple of 16 (vanilla DimensionType), got: "
                    + maxHeight);
        }
        if (height < DimensionConfig.MIN_WORLD_HEIGHT) {
            throw new IllegalArgumentException("build height must be at least 16, got: " + height);
        }
        if (minHeight + height > DimensionConfig.MAX_WORLD_TOP) {
            throw new IllegalArgumentException("minHeight + height must not exceed "
                    + DimensionConfig.MAX_WORLD_TOP + " (vanilla DimensionType), got: " + (minHeight + height));
        }
    }

    static SurfaceBlocks parseSurfaceBlocks(@NotNull ILuaTable t) {
        return new SurfaceBlocks(
                LuaTableValues.resourceId(t, "surfaceBlock", "minecraft:grass_block"),
                LuaTableValues.resourceId(t, "subsurfaceBlock", "minecraft:dirt"),
                LuaTableValues.resourceId(t, "fillerBlock", "minecraft:stone"),
                LuaTableValues.resourceId(t, "waterBlock", "minecraft:water"));
    }
}

package com.luatweaker.dimension;

import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Parses the three batch-callback results of one chunk
 * (heights, surfaces, sparse block overrides). Pure validation:
 * malformed shapes throw, ignorable entries warn and are skipped.
 */
final class ChunkCallbackParser {

    private ChunkCallbackParser() {
    }

    static int[] parseHeights(@NotNull String dimensionId, int chunkX, int chunkZ,
            ILuaValue[] results, int minY) {
        ILuaTable table = requireHeightsTable(results, dimensionId, chunkX, chunkZ);
        int[] heights = new int[DimensionServiceImpl.CHUNK_COLUMNS];
        Arrays.fill(heights, minY - 1);
        HeightStats stats = new HeightStats();
        for (int i = 1; i <= DimensionServiceImpl.CHUNK_COLUMNS; i++) {
            Integer height = parseHeightEntry(table.rawget(i), stats);
            if (height != null) {
                heights[i - 1] = height;
            }
        }
        if (stats.missing > 0 || stats.invalid > 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    DimensionServiceImpl.CHUNK_CALLBACK_PREFIX + dimensionId
                            + DimensionServiceImpl.CHUNK_AT + chunkX + ", " + chunkZ
                            + "): " + (stats.missing + stats.invalid) + " column(s) generate as void ("
                            + stats.missing + " missing, " + stats.invalid + " not a finite number)");
        }
        return heights;
    }

    @NotNull
    private static ILuaTable requireHeightsTable(ILuaValue[] results,
            @NotNull String dimensionId, int chunkX, int chunkZ) {
        ILuaValue heightsValue = results.length > 0 ? results[0] : null;
        if (heightsValue == null || heightsValue.isNil() || !heightsValue.isTable()) {
            throw new IllegalStateException(DimensionServiceImpl.CHUNK_CALLBACK_PREFIX + dimensionId
                    + "' must return a heights table of " + DimensionServiceImpl.CHUNK_COLUMNS + " numbers for chunk ("
                    + chunkX + ", " + chunkZ + ")");
        }
        return heightsValue.asTable();
    }

    private static final class HeightStats {
        private int missing;
        private int invalid;
    }

    @Nullable
    private static Integer parseHeightEntry(@Nullable ILuaValue value, @NotNull HeightStats stats) {
        if (value == null || value.isNil()) {
            stats.missing++;
            return null;
        }
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
            stats.invalid++;
            return null;
        }
        return (int) Math.floor(value.asDouble());
    }

    static String[] parseSurfaces(@NotNull String dimensionId, int chunkX, int chunkZ,
            ILuaValue[] results, @NotNull String defaultBlock) {
        String[] surfaces = new String[DimensionServiceImpl.CHUNK_COLUMNS];
        Arrays.fill(surfaces, defaultBlock);
        ILuaTable table = surfacesTableOrWarn(results, dimensionId, chunkX, chunkZ);
        if (table == null) {
            return surfaces;
        }
        int invalid = 0;
        for (int i = 1; i <= DimensionServiceImpl.CHUNK_COLUMNS; i++) {
            ILuaValue value = table.rawget(i);
            String block = parseSurfaceEntry(value);
            if (block == null) {
                if (value != null && !value.isNil()) {
                    invalid++;
                }
                continue;
            }
            surfaces[i - 1] = block;
        }
        if (invalid > 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    DimensionServiceImpl.CHUNK_CALLBACK_PREFIX + dimensionId
                            + DimensionServiceImpl.CHUNK_AT + chunkX + ", " + chunkZ
                            + ") returned " + invalid + " invalid surface block id(s); using the config default");
        }
        return surfaces;
    }

    @Nullable
    private static ILuaTable surfacesTableOrWarn(ILuaValue[] results,
            @NotNull String dimensionId, int chunkX, int chunkZ) {
        ILuaValue surfacesValue = results.length > 1 ? results[1] : null;
        if (surfacesValue == null || surfacesValue.isNil()) {
            return null;
        }
        if (!surfacesValue.isTable()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    DimensionServiceImpl.CHUNK_CALLBACK_PREFIX + dimensionId
                            + DimensionServiceImpl.CHUNK_AT + chunkX + ", " + chunkZ
                            + ") returned a non-table surfaces value; using the config surface block");
            return null;
        }
        return surfacesValue.asTable();
    }

    @Nullable
    private static String parseSurfaceEntry(@Nullable ILuaValue value) {
        if (value == null || value.isNil()) {
            return null;
        }
        if (!value.isString() || !LuaTableValues.RESOURCE_LOCATION.matcher(value.asString()).matches()) {
            return null;
        }
        return value.asString();
    }
}

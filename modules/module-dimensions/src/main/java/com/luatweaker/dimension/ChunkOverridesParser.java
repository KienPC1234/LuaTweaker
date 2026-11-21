package com.luatweaker.dimension;

import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses the sparse block-override result of one chunk callback.
 * Pure validation: malformed entries warn and are skipped, an over-cap
 * table fails loudly instead of exhausting the heap.
 */
final class ChunkOverridesParser {

    private ChunkOverridesParser() {
    }

    static Map<Integer, Map<Integer, String>> parseOverrides(@NotNull String dimensionId,
            int chunkX, int chunkZ, ILuaValue[] results, int minY, int maxY, int maxOverrides) {
        ILuaValue overridesValue = results.length > 2 ? results[2] : null;
        if (overridesValue == null || overridesValue.isNil()) {
            return Map.of();
        }
        if (!overridesValue.isTable()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    DimensionServiceImpl.CHUNK_CALLBACK_PREFIX + dimensionId
                            + DimensionServiceImpl.CHUNK_AT + chunkX + ", " + chunkZ
                            + ") returned a non-table overrides value; ignoring it");
            return Map.of();
        }
        Map<Integer, Map<Integer, String>> overrides = new ConcurrentHashMap<>();
        OverrideStats stats = new OverrideStats();
        for (Map.Entry<ILuaValue, ILuaValue> entry : overridesValue.asTable().asMap().entrySet()) {
            stats.total += parseColumnOverrides(entry, overrides, dimensionId, chunkX, chunkZ,
                    minY, maxY, maxOverrides, stats);
        }
        if (stats.rejected > 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    DimensionServiceImpl.CHUNK_CALLBACK_PREFIX + dimensionId
                            + DimensionServiceImpl.CHUNK_AT + chunkX + ", " + chunkZ
                            + ") returned " + stats.rejected + " invalid override entr(y/ies); they were ignored");
        }
        return Map.copyOf(overrides);
    }

    private static final class OverrideStats {
        private int rejected;
        private int total;
    }

    private static int parseColumnOverrides(@NotNull Map.Entry<ILuaValue, ILuaValue> entry,
            @NotNull Map<Integer, Map<Integer, String>> overrides,
            @NotNull String dimensionId, int chunkX, int chunkZ,
            int minY, int maxY, int maxOverrides, @NotNull OverrideStats stats) {
        ILuaValue key = entry.getKey();
        ILuaValue value = entry.getValue();
        if (key == null || !key.isNumber() || value == null || !value.isTable()) {
            stats.rejected++;
            return 0;
        }
        int column = key.asInt();
        if (column < 1 || column > DimensionServiceImpl.CHUNK_COLUMNS) {
            stats.rejected++;
            return 0;
        }
        Map<Integer, String> columnOverrides = newColumnOverrides();
        int accepted = 0;
        for (Map.Entry<ILuaValue, ILuaValue> blockEntry : value.asTable().asMap().entrySet()) {
            accepted += parseBlockOverride(blockEntry, columnOverrides, dimensionId, chunkX, chunkZ,
                    minY, maxY, maxOverrides, stats.total + accepted, stats);
        }
        if (!columnOverrides.isEmpty()) {
            overrides.put(column - 1, Map.copyOf(columnOverrides));
        }
        return accepted;
    }

    private static int parseBlockOverride(@NotNull Map.Entry<ILuaValue, ILuaValue> blockEntry,
            @NotNull Map<Integer, String> columnOverrides,
            @NotNull String dimensionId, int chunkX, int chunkZ,
            int minY, int maxY, int maxOverrides, int total, @NotNull OverrideStats stats) {
        if (!hasBlockOverrideShape(blockEntry)) {
            stats.rejected++;
            return 0;
        }
        int y = blockEntry.getKey().asInt();
        if (y < minY || y >= maxY) {
            stats.rejected++;
            return 0;
        }
        if (total >= maxOverrides) {
            throw new IllegalStateException(DimensionServiceImpl.CHUNK_CALLBACK_PREFIX + dimensionId + "' chunk ("
                    + chunkX + ", " + chunkZ + ") returned more than " + maxOverrides
                    + " block overrides; reduce the overrides or use a block picker");
        }
        columnOverrides.put(y, blockEntry.getValue().asString());
        return 1;
    }

    private static boolean hasBlockOverrideShape(@NotNull Map.Entry<ILuaValue, ILuaValue> blockEntry) {
        ILuaValue yKey = blockEntry.getKey();
        ILuaValue blockValue = blockEntry.getValue();
        return yKey != null && yKey.isNumber() && blockValue != null
                && blockValue.isString()
                && LuaTableValues.RESOURCE_LOCATION.matcher(blockValue.asString()).matches();
    }

    /** Fresh per-column override map; each chunk column gets its own mutable copy. */
    private static Map<Integer, String> newColumnOverrides() {
        return new ConcurrentHashMap<>();
    }
}

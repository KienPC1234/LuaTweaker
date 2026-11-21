package com.luatweaker.platform.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Read-only bulk queries behind the Lua {@code World} service: pattern checks and
 * radius searches. Unloaded chunks are skipped (never force generation while the
 * Lua execution gate is held).
 */
final class WorldQueryOps {

    private static final int MAX_FIND_RADIUS = 48;
    private static final int MAX_FIND_RESULTS = 256;
    private static final int DEFAULT_FIND_RESULTS = 64;

    private WorldQueryOps() {
    }

    static boolean checkPattern(@NotNull String dimension, int originX, int originY, int originZ,
                                @NotNull List<Map<String, Object>> pattern) {
        return ServerLevels.map(ServerLevels.resolve(dimension), false, level -> {
            for (Map<String, Object> entry : pattern) {
                if (!matchesPatternEntry(level, originX, originY, originZ, entry)) {
                    return false;
                }
            }
            return true;
        });
    }

    private static boolean matchesPatternEntry(ServerLevel level, int originX, int originY, int originZ,
                                                Map<String, Object> entry) {
        BlockPos pos = WorldBlockOps.pos(originX + number(entry.get("dx")),
                originY + number(entry.get("dy")), originZ + number(entry.get("dz")));
        if (!BlockStateCodec.isChunkLoaded(level, pos.getX(), pos.getZ())) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (entry.get("block") instanceof String requiredBlock
                && !BlockStateCodec.idOf(state).equalsIgnoreCase(BlockStateCodec.withNamespace(requiredBlock))) {
            return false;
        }
        if (entry.get("tag") instanceof String requiredTag) {
            ResourceLocation tagId = ResourceLocation.tryParse(requiredTag);
            if (tagId == null || !state.is(TagKey.create(Registries.BLOCK, tagId))) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    static List<Map<String, Object>> findBlocks(@NotNull String dimension, int centerX, int centerY, int centerZ,
                                                int radius, @NotNull String filter, int maxResults) {
        if (radius <= 0 || filter.isEmpty()) {
            return List.of();
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), List.of(),
                level -> searchIn(level, centerX, centerY, centerZ, radius, filter, maxResults));
    }

    @NotNull
    private static List<Map<String, Object>> searchIn(ServerLevel level, int centerX, int centerY, int centerZ,
                                                      int radius, String filter, int maxResults) {
        int effectiveRadius = Math.min(radius, MAX_FIND_RADIUS);
        int limit = maxResults > 0 ? Math.min(maxResults, MAX_FIND_RESULTS) : DEFAULT_FIND_RESULTS;
        var area = new SearchArea(level, centerX, centerY, centerZ,
                Math.max(level.getMinBuildHeight(), centerY - effectiveRadius),
                Math.min(level.getMaxBuildHeight() - 1, centerY + effectiveRadius),
                (double) effectiveRadius * effectiveRadius, filter, limit);
        List<Map<String, Object>> results = new ArrayList<>();
        for (int x = centerX - effectiveRadius; x <= centerX + effectiveRadius; x++) {
            for (int z = centerZ - effectiveRadius; z <= centerZ + effectiveRadius; z++) {
                if (scanColumn(area, x, z, results)) {
                    return results;
                }
            }
        }
        return results;
    }

    /** Scans one vertical column; returns true when the result limit was reached. */
    private static boolean scanColumn(SearchArea area, int x, int z, List<Map<String, Object>> results) {
        if (!BlockStateCodec.isChunkLoaded(area.level(), x, z)) {
            return false;
        }
        for (int y = area.minY(); y <= area.maxY(); y++) {
            double dx = x - area.centerX();
            double dy = y - area.centerY();
            double dz = z - area.centerZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > area.radiusSq()) {
                continue;
            }
            BlockPos pos = WorldBlockOps.pos(x, y, z);
            BlockState state = area.level().getBlockState(pos);
            if (!state.isAir() && BlockConditionMatcher.matches(area.level(), pos, state, area.filter())) {
                results.add(Map.of(
                        "X", x,
                        "Y", y,
                        "Z", z,
                        "Id", BlockStateCodec.idOf(state),
                        "Distance", Math.round(Math.sqrt(distSq) * 100.0) / 100.0));
                if (results.size() >= area.limit()) {
                    return true;
                }
            }
        }
        return false;
    }

    private record SearchArea(ServerLevel level, int centerX, int centerY, int centerZ,
                               int minY, int maxY, double radiusSq, String filter, int limit) {
    }

    private static int number(@Nullable Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}

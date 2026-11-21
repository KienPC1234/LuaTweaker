package com.luatweaker.platform.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates a cuboid region against a Lua shape spec (per-face block rules plus
 * component count limits) and reports the first mismatch with coordinates.
 */
final class CuboidValidator {

    private static final int EDGE_BOUNDARY_COUNT = 2;
    private static final int FACE_BOUNDARY_COUNT = 1;

    private CuboidValidator() {
    }

    @NotNull
    static Map<String, Object> validate(@NotNull String dimension,
                                        int x1, int y1, int z1,
                                        int x2, int y2, int z2,
                                        @NotNull Map<String, Object> spec) {
        return ServerLevels.map(ServerLevels.resolve(dimension),
                CuboidLimits.invalid("Dimension '" + dimension + "' is not loaded"),
                level -> validateOn(level, Box.from(x1, y1, z1, x2, y2, z2), spec));
    }

    @NotNull
    private static Map<String, Object> validateOn(ServerLevel level, Box box, Map<String, Object> spec) {
        Map<String, Object> boundsError = CuboidLimits.checkBounds(box, spec);
        if (boundsError != null) {
            return boundsError;
        }
        RuleSet rules = RuleSet.from(spec);
        List<Map<String, Object>> limits = CuboidLimits.parseLimits(spec);
        var blockCounts = new LinkedHashMap<String, Integer>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    Map<String, Object> mismatch = scanCell(level, box, rules, limits, blockCounts, x, y, z);
                    if (mismatch != null) {
                        return mismatch;
                    }
                }
            }
        }
        Map<String, Object> limitsError = CuboidLimits.checkLimits(limits);
        return limitsError != null ? limitsError : success(box, blockCounts);
    }

    @Nullable
    private static Map<String, Object> scanCell(ServerLevel level, Box box, RuleSet rules,
                                                List<Map<String, Object>> limits,
                                                Map<String, Integer> blockCounts, int x, int y, int z) {
        if (!BlockStateCodec.isChunkLoaded(level, x, z)) {
            return null;
        }
        BlockPos pos = WorldBlockOps.pos(x, y, z);
        BlockState state = level.getBlockState(pos);
        String blockId = BlockStateCodec.idOf(state);
        blockCounts.merge(blockId, 1, Integer::sum);
        for (Map<String, Object> limit : limits) {
            Object match = limit.get(CuboidLimits.MATCH_KEY) != null
                    ? limit.get(CuboidLimits.MATCH_KEY)
                    : limit.get("block");
            if (BlockConditionMatcher.matches(level, pos, state, match)) {
                limit.put(CuboidLimits.COUNT_KEY,
                        (int) limit.getOrDefault(CuboidLimits.COUNT_KEY, 0) + 1);
            }
        }
        String category = categoryFor(box, x, y, z);
        if (!BlockConditionMatcher.matches(level, pos, state, rules.ruleFor(category))) {
            return mismatch(x, y, z, category, blockId);
        }
        return null;
    }

    @NotNull
    private static String categoryFor(Box box, int x, int y, int z) {
        int boundaryCount = onBoundary(x, box.minX(), box.maxX())
                + onBoundary(y, box.minY(), box.maxY())
                + onBoundary(z, box.minZ(), box.maxZ());
        if (boundaryCount >= EDGE_BOUNDARY_COUNT) {
            return "frame";
        }
        if (boundaryCount != FACE_BOUNDARY_COUNT) {
            return "interior";
        }
        if (y == box.minY()) {
            return "floor";
        }
        return y == box.maxY() ? "ceiling" : "wall";
    }

    private static int onBoundary(int value, int min, int max) {
        return value == min || value == max ? 1 : 0;
    }

    @NotNull
    private static Map<String, Object> success(Box box, Map<String, Integer> blockCounts) {
        var success = new LinkedHashMap<String, Object>();
        success.put(CuboidLimits.RESULT_VALID_KEY, true);
        success.put("width", box.width());
        success.put("height", box.height());
        success.put("depth", box.depth());
        success.put("volume", box.width() * box.height() * box.depth());
        success.put("counts", blockCounts);
        success.put("minPos", Map.of("X", box.minX(), "Y", box.minY(), "Z", box.minZ()));
        success.put("maxPos", Map.of("X", box.maxX(), "Y", box.maxY(), "Z", box.maxZ()));
        return success;
    }

    @NotNull
    private static Map<String, Object> mismatch(int x, int y, int z, String category, String blockId) {
        var fail = new LinkedHashMap<String, Object>();
        fail.put(CuboidLimits.RESULT_VALID_KEY, false);
        fail.put(CuboidLimits.RESULT_ERROR_KEY,
                "Mismatch at " + x + ", " + y + ", " + z + " (" + category + "): found " + blockId);
        fail.put("failedPos", Map.of("X", x, "Y", y, "Z", z));
        fail.put("category", category);
        fail.put("actualBlock", blockId);
        return fail;
    }

    /** Inclusive, normalized cuboid bounds. */
    record Box(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {

        static Box from(int x1, int y1, int z1, int x2, int y2, int z2) {
            return new Box(Math.min(x1, x2), Math.max(x1, x2),
                    Math.min(y1, y2), Math.max(y1, y2),
                    Math.min(z1, z2), Math.max(z1, z2));
        }

        int width() {
            return maxX - minX + 1;
        }

        int height() {
            return maxY - minY + 1;
        }

        int depth() {
            return maxZ - minZ + 1;
        }
    }

    /** Per-face rules, with the frame/wall/all fallback chain applied by {@link #ruleFor}. */
    record RuleSet(@Nullable Object frame, @Nullable Object walls, @Nullable Object floor,
                   @Nullable Object ceiling, @Nullable Object interior, @Nullable Object shell) {

        static RuleSet from(Map<String, Object> spec) {
            Object shellRule = spec.get("casing");
            if (shellRule == null) {
                shellRule = spec.get("shell");
            }
            if (shellRule == null) {
                shellRule = spec.get("all");
            }
            return new RuleSet(spec.get("frame"), spec.get("walls"), spec.get("floor"),
                    spec.get("ceiling"), spec.get("interior"), shellRule);
        }

        @Nullable
        Object ruleFor(String category) {
            return switch (category) {
                case "frame" -> firstNonNull(frame, walls, shell);
                case "floor" -> firstNonNull(floor, walls, shell);
                case "ceiling" -> firstNonNull(ceiling, walls, shell);
                case "wall" -> firstNonNull(walls, shell);
                default -> interior;
            };
        }

        @Nullable
        private static Object firstNonNull(@Nullable Object... values) {
            for (Object value : values) {
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
    }
}

package com.luatweaker.common.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.border.WorldBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime read/write operations for {@link WorldBorder} and dimension spawn points.
 */
public final class WorldBorderOps {

    private static final double MIN_BORDER_SIZE = 1.0;
    private static final long NO_LERP_MILLIS = 0L;

    private WorldBorderOps() {
    }

    @Nullable
    public static Map<String, Object> getWorldSpawn(@NotNull String dimension) {
        return ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            BlockPos spawn = level.getSharedSpawnPos();
            var map = new LinkedHashMap<String, Object>();
            map.put("X", spawn.getX());
            map.put("Y", spawn.getY());
            map.put("Z", spawn.getZ());
            map.put("Angle", level.getSharedSpawnAngle());
            return map;
        });
    }

    public static boolean applyWorldSpawn(@NotNull String dimension, int x, int y, int z, float angle) {
        return Float.isFinite(angle) && ServerLevels.map(ServerLevels.resolve(dimension), false, level -> {
            level.setDefaultSpawnPos(new BlockPos(x, y, z), angle);
            return true;
        });
    }

    @Nullable
    public static Map<String, Object> getWorldBorder(@NotNull String dimension) {
        return ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            WorldBorder border = level.getWorldBorder();
            var map = new LinkedHashMap<String, Object>();
            map.put("CenterX", border.getCenterX());
            map.put("CenterZ", border.getCenterZ());
            map.put("Size", border.getSize());
            map.put("DamagePerBlock", border.getDamagePerBlock());
            map.put("DamageSafeZone", border.getDamageSafeZone());
            map.put("WarningTime", border.getWarningTime());
            map.put("WarningBlocks", border.getWarningBlocks());
            return map;
        });
    }

    public static boolean applyWorldBorder(@NotNull String dimension, double centerX, double centerZ,
                                           double size, long lerpMillis) {
        if (!isValidBorderInput(centerX, centerZ, size, lerpMillis)) {
            return false;
        }
        double clampedX = Math.clamp(centerX, -WorldBorder.MAX_CENTER_COORDINATE, WorldBorder.MAX_CENTER_COORDINATE);
        double clampedZ = Math.clamp(centerZ, -WorldBorder.MAX_CENTER_COORDINATE, WorldBorder.MAX_CENTER_COORDINATE);
        double clampedSize = Math.clamp(size, MIN_BORDER_SIZE, WorldBorder.MAX_SIZE);
        return ServerLevels.map(ServerLevels.resolve(dimension), false, level -> {
            WorldBorder border = level.getWorldBorder();
            border.setCenter(clampedX, clampedZ);
            if (lerpMillis > NO_LERP_MILLIS) {
                border.lerpSizeBetween(border.getSize(), clampedSize, lerpMillis);
            } else {
                border.setSize(clampedSize);
            }
            return true;
        });
    }

    private static boolean isValidBorderInput(double centerX, double centerZ, double size, long lerpMillis) {
        return Double.isFinite(centerX)
                && Double.isFinite(centerZ)
                && Double.isFinite(size)
                && size >= MIN_BORDER_SIZE
                && lerpMillis >= 0L;
    }
}

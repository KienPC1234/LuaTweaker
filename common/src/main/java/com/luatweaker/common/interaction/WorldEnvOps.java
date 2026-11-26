package com.luatweaker.common.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime time, weather, live biome, sky/block light, heightmap, and chunk state
 * queries over loaded {@link ServerLevel} instances.
 */
final class WorldEnvOps {

    private static final int DEFAULT_WEATHER_DURATION_TICKS = 6000;
    private static final int UNKNOWN_LIGHT = -1;
    private static final long MIN_DAY_TIME = 0L;
    private static final String LIGHT_BLOCK = "block";
    private static final String LIGHT_SKY = "sky";

    private WorldEnvOps() {
    }

    static long getDayTime(@NotNull String dimension) {
        return ServerLevels.map(ServerLevels.resolve(dimension), -1L, ServerLevel::getDayTime);
    }

    static boolean applyDayTime(@NotNull String dimension, long dayTime) {
        return dayTime >= MIN_DAY_TIME && ServerLevels.map(ServerLevels.resolve(dimension), false, level -> {
            level.setDayTime(dayTime);
            return true;
        });
    }

    static long getGameTime(@NotNull String dimension) {
        return ServerLevels.map(ServerLevels.resolve(dimension), -1L, ServerLevel::getGameTime);
    }

    @Nullable
    static Map<String, Object> getWeather(@NotNull String dimension) {
        return ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            var weather = new LinkedHashMap<String, Object>();
            weather.put("Raining", level.isRaining());
            weather.put("Thundering", level.isThundering());
            weather.put("RainLevel", level.getRainLevel(1.0f));
            weather.put("ThunderLevel", level.getThunderLevel(1.0f));
            return weather;
        });
    }

    static boolean applyWeather(@NotNull String dimension, @NotNull String kind, int durationTicks) {
        if (kind == null) {
            return false;
        }
        int ticks = durationTicks > 0 ? durationTicks : DEFAULT_WEATHER_DURATION_TICKS;
        String normalized = kind.trim().toLowerCase(Locale.ROOT);
        return ServerLevels.map(ServerLevels.resolve(dimension), false, level -> switch (normalized) {
            case "clear" -> {
                level.setWeatherParameters(ticks, 0, false, false);
                yield true;
            }
            case "rain" -> {
                level.setWeatherParameters(0, ticks, true, false);
                yield true;
            }
            case "thunder" -> {
                level.setWeatherParameters(0, ticks, true, true);
                yield true;
            }
            default -> false;
        });
    }

    @Nullable
    static String getBiome(@NotNull String dimension, int x, int y, int z) {
        return ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            if (!BlockStateCodec.isChunkLoaded(level, x, z)) {
                return null;
            }
            return level.getBiome(new BlockPos(x, y, z))
                    .unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse(null);
        });
    }

    static int getLightLevel(@NotNull String dimension, int x, int y, int z, @NotNull String kind) {
        String normalized = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        return ServerLevels.map(ServerLevels.resolve(dimension), UNKNOWN_LIGHT, level -> {
            if (!BlockStateCodec.isChunkLoaded(level, x, z)) {
                return UNKNOWN_LIGHT;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (LIGHT_BLOCK.equals(normalized)) {
                return level.getBrightness(LightLayer.BLOCK, pos);
            }
            if (LIGHT_SKY.equals(normalized)) {
                return level.getBrightness(LightLayer.SKY, pos);
            }
            return level.getMaxLocalRawBrightness(pos);
        });
    }

    static boolean canSeeSky(@NotNull String dimension, int x, int y, int z) {
        return ServerLevels.map(ServerLevels.resolve(dimension), false,
                level -> BlockStateCodec.isChunkLoaded(level, x, z) && level.canSeeSky(new BlockPos(x, y, z)));
    }

    static int getHighestBlockY(@NotNull String dimension, int x, int z, @NotNull String kind) {
        Heightmap.Types type = parseHeightmap(kind);
        return ServerLevels.map(ServerLevels.resolve(dimension), Integer.MIN_VALUE,
                level -> BlockStateCodec.isChunkLoaded(level, x, z) ? level.getHeight(type, x, z) : Integer.MIN_VALUE);
    }

    private static Heightmap.Types parseHeightmap(@Nullable String kind) {
        if (kind == null) {
            return Heightmap.Types.MOTION_BLOCKING;
        }
        return switch (kind.trim().toLowerCase(Locale.ROOT)) {
            case "world_surface" -> Heightmap.Types.WORLD_SURFACE;
            case "ocean_floor" -> Heightmap.Types.OCEAN_FLOOR;
            case "motion_blocking_no_leaves" -> Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
            default -> Heightmap.Types.MOTION_BLOCKING;
        };
    }
}

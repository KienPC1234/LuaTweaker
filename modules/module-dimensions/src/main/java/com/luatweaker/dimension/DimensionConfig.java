package com.luatweaker.dimension;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Immutable, parsed configuration of one custom dimension.
 * Fields are validated at parse time so the platform provider can trust them.
 *
 * <p>Low-level philosophy: this config only carries DATA (terrain bounds,
 * surface blocks, biome weights, vanilla spawner entries). All behavior
 * (caves, ores, trees, lakes, buildings, island shapes) is written by the
 * mod author in Lua through {@code SetTerrainGenerator}, {@code SetBlockPicker},
 * {@code SetStrataPicker} and {@code SetBiomeProvider}.</p>
 */
public record DimensionConfig(
        @NotNull String id,
        boolean hasSkyLight,
        boolean hasCeiling,
        boolean ultraWarm,
        boolean natural,
        double coordinateScale,
        boolean bedWorks,
        boolean respawnAnchorWorks,
        boolean piglinSafe,
        boolean hasRaids,
        int monsterSpawnLightLevel,
        int monsterSpawnBlockLightLimit,
        @NotNull String infiniburn,
        @NotNull String effectsLocation,
        Long fixedTime,
        int skyColor,
        int fogColor,
        double ambientLight,
        int seaLevel,
        int minHeight,
        int maxHeight,
        int logicalHeight,
        @NotNull String surfaceBlock,
        @NotNull String subsurfaceBlock,
        @NotNull String fillerBlock,
        @NotNull String waterBlock,
        boolean hasBedrock,
        int biomeSize,
        Integer spawnX,
        Integer spawnZ,
        @NotNull List<BiomeEntry> biomes,
        @NotNull List<SpawnEntry> spawnEntities
) {

    public static final int DEFAULT_SEA_LEVEL = 63;
    public static final int DEFAULT_MIN_HEIGHT = -64;
    public static final int DEFAULT_MAX_HEIGHT = 320;
    public static final int DEFAULT_SKY_COLOR = 0x78A7FF;
    public static final int DEFAULT_FOG_COLOR = 0xC0D8FF;
    public static final double DEFAULT_AMBIENT_LIGHT = 0.1;
    public static final int DEFAULT_BIOME_SIZE = 4;
    public static final String DEFAULT_INFINIBURN = "#minecraft:infiniburn_overworld";
    public static final String DEFAULT_EFFECTS = "luatweaker:lua";

    public record BiomeEntry(@NotNull String id, int weight) {}

    /** Vanilla natural-spawn entry (weight/group sizes), wired into {@code getMobsAt}. */
    public record SpawnEntry(@NotNull String entity, int weight, int minGroup, int maxGroup) {}

    /** Creates a config with Minecraft-vanilla-safe defaults. */
    public static DimensionConfig defaults(@NotNull String id) {
        return new DimensionConfig(
                id,
                true, false, false, true, 1.0, true, false,
                false, false, 7, 0, DEFAULT_INFINIBURN, DEFAULT_EFFECTS,
                null,
                DEFAULT_SKY_COLOR, DEFAULT_FOG_COLOR, DEFAULT_AMBIENT_LIGHT,
                DEFAULT_SEA_LEVEL, DEFAULT_MIN_HEIGHT, DEFAULT_MAX_HEIGHT, DEFAULT_MAX_HEIGHT - DEFAULT_MIN_HEIGHT,
                "minecraft:grass_block", "minecraft:dirt", "minecraft:stone", "minecraft:water", false,
                DEFAULT_BIOME_SIZE, null, null,
                List.of(), List.of()
        );
    }

    /** Sanity bounds used when validating parsed numbers. */
    public static boolean isColor(int rgb) {
        return rgb >= 0 && rgb <= 0xFFFFFF;
    }
}

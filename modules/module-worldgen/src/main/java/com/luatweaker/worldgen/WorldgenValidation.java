package com.luatweaker.worldgen;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Resource-location, step and numeric guards shared by the registration entry points and
 * the spec parser. All failures are {@link IllegalArgumentException} so the Lua binding
 * reports the offending script call.
 */
final class WorldgenValidation {

    /** Minecraft resource-location charset: [a-z0-9_.-] path, optional [a-z0-9_.-] namespace. */
    static final Pattern RESOURCE_LOCATION =
            Pattern.compile("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");

    private static final double MIN_CHANCE = 0.0;
    private static final double MAX_CHANCE = 1.0;

    private WorldgenValidation() {}

    static void validateOre(@NotNull String blockId, @NotNull String dimension,
                            int minHeight, int maxHeight, int clusterSize, int frequency) {
        validateOreLocation(blockId, dimension);
        validateHeightRange(minHeight, maxHeight);
        validateOreCounts(clusterSize, frequency);
    }

    private static void validateOreLocation(@NotNull String blockId, @NotNull String dimension) {
        if (!RESOURCE_LOCATION.matcher(blockId).matches()) {
            throw new IllegalArgumentException(
                    "blockId must be a valid resource location (e.g. 'mymod:ruby_ore'), got: " + blockId);
        }
        if (!RESOURCE_LOCATION.matcher(dimension).matches()) {
            throw new IllegalArgumentException(
                    "dimension must be a valid resource location (e.g. 'minecraft:overworld'), got: " + dimension);
        }
    }

    private static void validateHeightRange(int minHeight, int maxHeight) {
        if (minHeight > maxHeight) {
            throw new IllegalArgumentException("minHeight (" + minHeight + ") > maxHeight (" + maxHeight + ")");
        }
    }

    private static void validateOreCounts(int clusterSize, int frequency) {
        if (clusterSize <= 0) {
            throw new IllegalArgumentException("clusterSize must be > 0, got: " + clusterSize);
        }
        if (frequency <= 0) {
            throw new IllegalArgumentException("frequency must be > 0, got: " + frequency);
        }
    }

    static @NotNull List<String> validateVegetationArgs(@NotNull String blockId, double chance,
                                                        @NotNull String... biomes) {
        if (!RESOURCE_LOCATION.matcher(blockId).matches()) {
            throw new IllegalArgumentException(
                    "blockId must be a valid resource location (e.g. 'mymod:ruby_flower'), got: " + blockId);
        }
        if (chance < MIN_CHANCE || chance > MAX_CHANCE) {
            throw new IllegalArgumentException("chance must be between " + MIN_CHANCE
                    + " and " + MAX_CHANCE + ", got: " + chance);
        }
        if (biomes.length == 0) {
            throw new IllegalArgumentException("biomes must not be empty");
        }
        for (String biome : biomes) {
            validateBiomeRef(biome);
        }
        return List.of(biomes);
    }

    static void validateBiomeRef(@NotNull String biome) {
        if (biome.startsWith("#")) {
            validateResourceLocation(biome.substring(1), "biome tag target");
            return;
        }
        if (!RESOURCE_LOCATION.matcher(biome).matches()) {
            throw new IllegalArgumentException("biome must be a valid resource location or tag "
                    + "(e.g. 'minecraft:plains' or '#minecraft:is_overworld'), got: " + biome);
        }
    }

    static void validateResourceLocation(@NotNull String id, @NotNull String what) {
        if (!RESOURCE_LOCATION.matcher(id).matches()) {
            throw new IllegalArgumentException(what + " must be a valid resource location, got: " + id);
        }
    }

    static @NotNull String parseStep(@NotNull String step, @NotNull String what) {
        if (!WorldgenDefaults.KNOWN_STEPS.contains(step)) {
            throw new IllegalArgumentException(what + " is not a known generation step, got: " + step);
        }
        return step;
    }
}

package com.luatweaker.worldgen;

import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Pure-Java worldgen registry front end. Records Lua registrations in the pending store;
 * the file-plan/query layers inherited from {@link AbstractWorldgenFilePlan} build the
 * datapack JSON ({@code neoforge:biome_modifier} + configured/placed features) that the
 * platform provider feeds to {@code IDatapackService#addData}.
 *
 * <p>JSON field names verified against NeoForge 21.1.242 ({@code NeoForgeMod} constant
 * pool: {@code biomes}, {@code features}, {@code step} for add-features; {@code steps}
 * plural for remove-features) and against vanilla 1.21.1 datapack JSON.</p>
 */
public class WorldgenServiceImpl extends AbstractWorldgenFilePlan {

    private static final String ERR_FEATURE_ID = "featureId '";
    private static final String ERR_ALREADY_REGISTERED =
            "' is already registered; call ClearAll or use a unique id";

    public record OreEntry(String blockId, String dimension, int minHeight, int maxHeight,
                           int clusterSize, int frequency, List<String> biomes) {}

    public record VegetationEntry(String blockId, double chance, List<String> biomes, String kind) {}

    public record OreRemovalEntry(String blockId, String dimension) {}

    public record AddedFeature(String feature, String step) {}

    public record BiomeModification(String biomeId, List<String> biomes,
                                    List<AddedFeature> added, List<String> removed,
                                    List<String> removeSteps) {}

    public record CustomFeature(String featureId, String block, List<String> biomes, String step,
                                String replace, int count, int size, int minHeight, int maxHeight,
                                String heightType, String anchorMin, String anchorMax,
                                Integer rarity, double discardChance, boolean scattered, int inner) {}

    public record RawConfigured(String featureId, String json) {}

    public record RawPlaced(String featureId, String json) {}

    /** One ore candidate inside a mixed vein: the block to place and its relative weight. */
    public record VeinOre(String block, int weight) {}

    /**
     * GTNH-style mixed vein: one ellipsoid blob (custom {@code luatweaker:mixed_vein}
     * feature) mixing several ores by weight. Placement fields mirror {@link CustomFeature}.
     */
    public record MixedVein(String veinId, List<VeinOre> ores, String replace, List<String> biomes,
                            String step, int count, Integer rarity, int size,
                            int minHeight, int maxHeight, String heightType,
                            String anchorMin, String anchorMax,
                            double discardChance, int inner) {}

    @Override
    public void addOre(@NotNull String blockId, @NotNull String dimension,
                       int minHeight, int maxHeight, int clusterSize, int frequency) {
        WorldgenValidation.validateOre(blockId, dimension, minHeight, maxHeight, clusterSize, frequency);
        pendingOres.add(new OreEntry(blockId, dimension, minHeight, maxHeight, clusterSize,
                frequency, List.of()));
    }

    @Override
    public void addOreBiomeFiltered(@NotNull String blockId, @NotNull String dimension,
                                    int minHeight, int maxHeight, int clusterSize, int frequency,
                                    @NotNull String[] biomes) {
        WorldgenValidation.validateOre(blockId, dimension, minHeight, maxHeight, clusterSize, frequency);
        if (biomes.length == 0) {
            throw new IllegalArgumentException("biomes must not be empty for biome-filtered ore");
        }
        pendingOres.add(new OreEntry(blockId, dimension, minHeight, maxHeight, clusterSize,
                frequency, List.of(biomes)));
    }

    @Override
    public void addVegetation(@NotNull String blockId, double chance, @NotNull String... biomes) {
        pendingVegetation.add(new VegetationEntry(blockId, chance,
                WorldgenValidation.validateVegetationArgs(blockId, chance, biomes),
                WorldgenDefaults.KIND_FLOWER));
    }

    @Override
    public void addVegetationPatch(@NotNull String blockId, double chance, @NotNull String... biomes) {
        pendingVegetation.add(new VegetationEntry(blockId, chance,
                WorldgenValidation.validateVegetationArgs(blockId, chance, biomes),
                WorldgenDefaults.KIND_PATCH));
    }

    @Override
    public boolean removeOre(@NotNull String blockId, @NotNull String dimension) {
        WorldgenValidation.validateResourceLocation(blockId, "blockId");
        WorldgenValidation.validateResourceLocation(dimension, "dimension");
        pendingRemovals.add(new OreRemovalEntry(blockId, dimension));
        return true;
    }

    @Override
    public void modifyBiome(@NotNull String biomeId, @NotNull ILuaTable options) {
        WorldgenValidation.validateResourceLocation(biomeId, "biomeId");
        if (options == null) {
            throw new IllegalArgumentException("modifyBiome requires an options table");
        }
        List<String> biomes = LuaStringListReader.reqStringList(options, "biomes", "biome", "options.biomes");
        for (String biome : biomes) {
            WorldgenValidation.validateBiomeRef(biome);
        }
        String defaultStep = LuaFieldReaders.optStep(options, "step", WorldgenDefaults.DEFAULT_STEP);
        List<AddedFeature> added = WorldgenFeatureListParser.parseAddedFeatures(options, defaultStep);
        List<String> removed = WorldgenFeatureListParser.parseRemovedFeatures(options);
        List<String> removeSteps = WorldgenFeatureListParser.parseRemoveSteps(options);
        if (added.isEmpty() && removed.isEmpty()) {
            throw new IllegalArgumentException(
                    "modifyBiome requires at least one of options.add / options.remove");
        }
        pendingBiomeMods.add(new BiomeModification(biomeId, List.copyOf(biomes),
                List.copyOf(added), List.copyOf(removed), List.copyOf(removeSteps)));
    }

    @Override
    public void addFeature(@NotNull String featureId, @NotNull ILuaTable spec) {
        WorldgenValidation.validateResourceLocation(featureId, "featureId");
        if (spec == null) {
            throw new IllegalArgumentException("addFeature requires a spec table");
        }
        String block = LuaFieldReaders.reqString(spec, "block", "spec.block");
        WorldgenValidation.validateResourceLocation(block, "spec.block");
        List<String> biomes = LuaStringListReader.reqStringList(spec, "biomes", "biome", "spec.biomes");
        for (String biome : biomes) {
            WorldgenValidation.validateBiomeRef(biome);
        }
        if (isFeatureIdTaken(featureId)) {
            throw new IllegalArgumentException(ERR_FEATURE_ID + featureId
                    + ERR_ALREADY_REGISTERED);
        }
        WorldgenPlacement placement = WorldgenPlacementParser.parsePlacement(spec);
        boolean scattered = LuaFieldReaders.optBoolean(spec, "scattered", false);
        pendingFeatures.add(new CustomFeature(featureId, block, List.copyOf(biomes),
                placement.step(), WorldgenPlacementParser.parseReplace(spec),
                placement.count(), placement.size(), placement.minHeight(), placement.maxHeight(),
                placement.heightType(), placement.anchorMin(), placement.anchorMax(),
                placement.rarity(), placement.discardChance(), scattered, placement.inner()));
    }

    @Override
    public void addMixedVein(@NotNull String veinId, @NotNull ILuaTable spec) {
        WorldgenValidation.validateResourceLocation(veinId, "veinId");
        if (spec == null) {
            throw new IllegalArgumentException("addMixedVein requires a spec table");
        }
        List<VeinOre> ores = WorldgenVeinParser.parseVeinOres(spec);
        String replace = WorldgenPlacementParser.parseReplace(spec);
        List<String> biomes = LuaStringListReader.reqStringList(spec, "biomes", "biome", "spec.biomes");
        for (String biome : biomes) {
            WorldgenValidation.validateBiomeRef(biome);
        }
        if (isFeatureIdTaken(veinId)) {
            throw new IllegalArgumentException(ERR_FEATURE_ID + veinId
                    + ERR_ALREADY_REGISTERED);
        }
        WorldgenPlacement placement = WorldgenPlacementParser.parsePlacement(spec);
        if (placement.size() > WorldgenDefaults.MAX_VEIN_SIZE) {
            throw new IllegalArgumentException("spec.size must be <= " + WorldgenDefaults.MAX_VEIN_SIZE
                    + " (mixed_vein codec bound), got: " + placement.size());
        }
        pendingMixedVeins.add(new MixedVein(veinId, List.copyOf(ores), replace, List.copyOf(biomes),
                placement.step(), placement.count(), placement.rarity(), placement.size(),
                placement.minHeight(), placement.maxHeight(), placement.heightType(),
                placement.anchorMin(), placement.anchorMax(),
                placement.discardChance(), placement.inner()));
    }

    @Override
    public void addRawConfiguredFeature(@NotNull String featureId, @NotNull String json) {
        WorldgenValidation.validateResourceLocation(featureId, "featureId");
        String type = WorldgenPlacementParser.parseRawJson(json, "configured", "type", featureId);
        if (!WorldgenDefaults.KNOWN_FEATURE_TYPES.contains(type)) {
            throw new IllegalArgumentException("AddRawConfiguredFeature: unknown vanilla type '" + type
                    + "' for '" + featureId + "' (must be one of the 1.21.1 configured-feature types)");
        }
        if (isFeatureIdTaken(featureId)) {
            throw new IllegalArgumentException(ERR_FEATURE_ID + featureId
                    + ERR_ALREADY_REGISTERED);
        }
        pendingRawConfigured.add(new RawConfigured(featureId, json));
    }

    @Override
    public void addRawPlacedFeature(@NotNull String featureId, @NotNull String json) {
        WorldgenValidation.validateResourceLocation(featureId, "featureId");
        WorldgenPlacementParser.parseRawJson(json, "placed", "feature", featureId);
        if (pendingRawPlaced.stream().anyMatch(f -> f.featureId().equals(featureId))) {
            throw new IllegalArgumentException(ERR_FEATURE_ID + featureId
                    + ERR_ALREADY_REGISTERED);
        }
        pendingRawPlaced.add(new RawPlaced(featureId, json));
    }
}

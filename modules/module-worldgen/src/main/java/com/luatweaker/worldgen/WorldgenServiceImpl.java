package com.luatweaker.worldgen;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.api.worldgen.IWorldgenService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure-Java worldgen registry. Records Lua registrations and builds the
 * datapack JSON ({@code neoforge:biome_modifier} + configured/placed
 * features) the platform provider feeds to {@code IDatapackService#addData}.
 *
 * <p>JSON field names verified against NeoForge 21.1.242
 * ({@code NeoForgeMod} constant pool: {@code biomes}, {@code features},
 * {@code step} for add-features; {@code steps} plural for remove-features)
 * and against vanilla 1.21.1 datapack JSON extracted from
 * {@code minecraft_1.21.1_client.jar} (ore/flower/patch features, rarity
 * filter, height providers, block predicates). Step values verified against
 * {@code GenerationStep$Decoration} serialized names in the deobfuscated
 * game jar.
 */
public class WorldgenServiceImpl implements IWorldgenService, IWorldgenExtendedService {

    /** Minecraft resource-location charset: [a-z0-9_.-] for path, optional [a-z0-9_.-] namespace. */
    private static final java.util.regex.Pattern RESOURCE_LOCATION =
            java.util.regex.Pattern.compile("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");

    /**
     * Verified GenerationStep.Decoration serialized names; see
     * {@link com.luatweaker.api.worldgen.GenerationSteps} (single source of
     * truth shared with the dimensions module for biome feature ordinals).
     */
    private static final java.util.Set<String> KNOWN_STEPS =
            new java.util.LinkedHashSet<>(com.luatweaker.api.worldgen.GenerationSteps.ORDER);

    /**
     * Configured-feature types present in the vanilla 1.21.1 datapack
     * (enumerated from data/minecraft/worldgen/configured_feature/*.json
     * in minecraft_1.21.1_client.jar). Raw JSON with any other type fails
     * fast instead of dying silently at datapack load.
     */
    private static final Set<String> KNOWN_FEATURE_TYPES = Set.of(
            "minecraft:bamboo", "minecraft:basalt_columns", "minecraft:basalt_pillar",
            "minecraft:block_column", "minecraft:block_pile", "minecraft:blue_ice",
            "minecraft:bonus_chest", "minecraft:chorus_plant", "minecraft:delta_feature",
            "minecraft:desert_well", "minecraft:disk", "minecraft:dripstone_cluster",
            "minecraft:end_gateway", "minecraft:end_island", "minecraft:end_spike",
            "minecraft:flower", "minecraft:forest_rock", "minecraft:fossil",
            "minecraft:freeze_top_layer", "minecraft:geode", "minecraft:glowstone_blob",
            "minecraft:huge_brown_mushroom", "minecraft:huge_fungus", "minecraft:huge_red_mushroom",
            "minecraft:ice_spike", "minecraft:iceberg", "minecraft:kelp", "minecraft:lake",
            "minecraft:large_dripstone", "minecraft:monster_room", "minecraft:multiface_growth",
            "minecraft:nether_forest_vegetation", "minecraft:netherrack_replace_blobs",
            "minecraft:ore", "minecraft:random_boolean_selector", "minecraft:random_patch",
            "minecraft:random_selector", "minecraft:root_system", "minecraft:scattered_ore",
            "minecraft:sculk_patch", "minecraft:sea_pickle", "minecraft:seagrass",
            "minecraft:simple_block", "minecraft:simple_random_selector", "minecraft:spring_feature",
            "minecraft:tree", "minecraft:twisting_vines", "minecraft:underwater_magma",
            "minecraft:vegetation_patch", "minecraft:vines", "minecraft:void_start_platform",
            "minecraft:waterlogged_vegetation_patch", "minecraft:weeping_vines");

    /** Default decoration step for added features (verified jar value). */
    private static final String DEFAULT_STEP = "underground_ores";

    /** Defaults mirror vanilla placement shapes (verified against 1.21.1 placed features). */
    private static final String DEFAULT_HEIGHT_TYPE = "uniform";
    private static final String DEFAULT_ANCHOR = "absolute";
    private static final double DEFAULT_DISCARD_CHANCE = 0.0;
    private static final int DEFAULT_BIASED_INNER = 8;

    /**
     * Height-provider types accepted for {@code spec.heightType}. {@code uniform}
     * and {@code trapezoid} shapes verified across vanilla ore placed features
     * (e.g. ore_coal_upper, ore_iron_middle); {@code very_biased_to_bottom}
     * verified in spring_lava.json ({@code inner: 8}). {@code biased_to_bottom}
     * is UNVERIFIED for height (only observed for count in glowstone_extra) but
     * shares the same {min, max, inner} codec family, so it is accepted and any
     * mismatch fails loudly at datapack load.
     */
    private static final Set<String> KNOWN_HEIGHT_TYPES = Set.of(
            "uniform", "trapezoid", "biased_to_bottom", "very_biased_to_bottom");

    /**
     * Vertical anchors for {@code spec.anchorMin}/{@code spec.anchorMax}.
     * All three forms verified in vanilla ore placed features
     * ({@code absolute} in ore_coal_upper, {@code above_bottom} in
     * ore_diamond, {@code below_top} in ore_debris_small).
     */
    private static final Set<String> KNOWN_ANCHORS = Set.of("absolute", "above_bottom", "below_top");

    /** Defaults mirror addOre's @LuaDefault values so both paths behave the same. */
    private static final int DEFAULT_FEATURE_COUNT = 10;
    private static final int DEFAULT_FEATURE_SIZE = 8;
    private static final int DEFAULT_MIN_HEIGHT = -64;
    private static final int DEFAULT_MAX_HEIGHT = 32;

    /** Target-rock selector for addFeature (mirrors the provider's ore-target mapping). */
    private static final Set<String> KNOWN_REPLACE_KINDS = Set.of("stone", "nether", "end");
    private static final String DEFAULT_REPLACE_KIND = "stone";

    /** Vegetation feature kinds. Flower/patch shapes verified against flower_plain.json / patch_grass.json. */
    private static final String KIND_FLOWER = "flower";
    private static final String KIND_PATCH = "patch";

    /** Verified configured-feature numbers (tries/xz_spread/y_spread from the vanilla JSONs). */
    private static final int FLOWER_TRIES = 64;
    private static final int FLOWER_XZ_SPREAD = 6;
    private static final int FLOWER_Y_SPREAD = 2;
    private static final int PATCH_TRIES = 32;
    private static final int PATCH_XZ_SPREAD = 7;
    private static final int PATCH_Y_SPREAD = 3;

    /**
     * Default dimension -&gt; biome-tag mapping. Verbatim copy of the provider's
     * dimensionBiomeTag mapping (NeoForgeWorldgenProvider), made overridable
     * via {@link #registerDimensionBiomeTag}. Dimension <i>existence</i> is
     * verified platform-side; an unregistered dimension resolves empty and
     * the provider skips it loudly.
     */
    private static final Map<String, String> DEFAULT_DIMENSION_TAGS = Map.of(
            "minecraft:overworld", "#minecraft:is_overworld",
            "minecraft:the_nether", "#minecraft:is_nether",
            "minecraft:the_end", "#minecraft:is_end");

    /**
     * Default ore-block -&gt; vanilla placed-feature IDs. Verbatim copy of the
     * provider's table; every one of the 28 feature files was verified to
     * exist under data/minecraft/worldgen/placed_feature/ in the 1.21.1
     * client jar. Made overridable via {@link #registerRemovalFeatures} so
     * modded ores (and the omitted nether deltas variants) can be removed too.
     */
    private static final Map<String, List<String>> DEFAULT_REMOVAL_FEATURES = Map.ofEntries(
            Map.entry("minecraft:coal_ore", List.of("minecraft:ore_coal_upper", "minecraft:ore_coal_lower")),
            Map.entry("minecraft:iron_ore", List.of("minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small")),
            Map.entry("minecraft:gold_ore", List.of("minecraft:ore_gold", "minecraft:ore_gold_lower", "minecraft:ore_gold_extra")),
            Map.entry("minecraft:redstone_ore", List.of("minecraft:ore_redstone", "minecraft:ore_redstone_lower")),
            Map.entry("minecraft:lapis_ore", List.of("minecraft:ore_lapis", "minecraft:ore_lapis_buried")),
            Map.entry("minecraft:diamond_ore", List.of("minecraft:ore_diamond", "minecraft:ore_diamond_medium", "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried")),
            Map.entry("minecraft:emerald_ore", List.of("minecraft:ore_emerald")),
            Map.entry("minecraft:copper_ore", List.of("minecraft:ore_copper", "minecraft:ore_copper_large")),
            Map.entry("minecraft:nether_quartz_ore", List.of("minecraft:ore_quartz_nether")),
            Map.entry("minecraft:nether_gold_ore", List.of("minecraft:ore_gold_nether")),
            Map.entry("minecraft:ancient_debris", List.of("minecraft:ore_ancient_debris_large", "minecraft:ore_debris_small")));

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

    private final List<OreEntry> pendingOres = Collections.synchronizedList(new ArrayList<>());
    private final List<VegetationEntry> pendingVegetation = Collections.synchronizedList(new ArrayList<>());
    private final List<OreRemovalEntry> pendingRemovals = Collections.synchronizedList(new ArrayList<>());
    private final List<BiomeModification> pendingBiomeMods = Collections.synchronizedList(new ArrayList<>());
    private final List<CustomFeature> pendingFeatures = Collections.synchronizedList(new ArrayList<>());
    private final List<RawConfigured> pendingRawConfigured = Collections.synchronizedList(new ArrayList<>());
    private final List<RawPlaced> pendingRawPlaced = Collections.synchronizedList(new ArrayList<>());
    private final List<MixedVein> pendingMixedVeins = Collections.synchronizedList(new ArrayList<>());

    /** User overrides for ore-removal feature lists; block ID -&gt; placed-feature IDs. */
    private final Map<String, List<String>> removalOverrides = new ConcurrentHashMap<>();

    /** Dimension ID -&gt; biome tag; seeded with the 3 vanilla defaults. */
    private final Map<String, String> dimensionTags = new ConcurrentHashMap<>(DEFAULT_DIMENSION_TAGS);

    @Override
    public void addOre(@NotNull String blockId, @NotNull String dimension,
                       int minHeight, int maxHeight, int clusterSize, int frequency) {
        validate(blockId, dimension, minHeight, maxHeight, clusterSize, frequency);
        pendingOres.add(new OreEntry(blockId, dimension, minHeight, maxHeight, clusterSize, frequency, List.of()));
    }

    @Override
    public void addOreBiomeFiltered(@NotNull String blockId, @NotNull String dimension,
                                    int minHeight, int maxHeight, int clusterSize, int frequency,
                                    @NotNull String[] biomes) {
        validate(blockId, dimension, minHeight, maxHeight, clusterSize, frequency);
        if (biomes.length == 0) {
            throw new IllegalArgumentException("biomes must not be empty for biome-filtered ore");
        }
        pendingOres.add(new OreEntry(blockId, dimension, minHeight, maxHeight, clusterSize, frequency, List.of(biomes)));
    }

    @Override
    public void addVegetation(@NotNull String blockId, double chance, @NotNull String[] biomes) {
        pendingVegetation.add(new VegetationEntry(blockId, chance,
                validateVegetationArgs(blockId, chance, biomes), KIND_FLOWER));
    }

    @Override
    public void addVegetationPatch(@NotNull String blockId, double chance, @NotNull String[] biomes) {
        pendingVegetation.add(new VegetationEntry(blockId, chance,
                validateVegetationArgs(blockId, chance, biomes), KIND_PATCH));
    }

    @Override
    public boolean removeOre(@NotNull String blockId, @NotNull String dimension) {
        if (!RESOURCE_LOCATION.matcher(blockId).matches()) {
            throw new IllegalArgumentException("blockId must be a valid resource location, got: " + blockId);
        }
        if (!RESOURCE_LOCATION.matcher(dimension).matches()) {
            throw new IllegalArgumentException("dimension must be a valid resource location, got: " + dimension);
        }
        pendingRemovals.add(new OreRemovalEntry(blockId, dimension));
        return true;
    }

    @Override
    public void modifyBiome(@NotNull String biomeId, @NotNull ILuaTable options) {
        validateResourceLocation(biomeId, "biomeId");
        if (options == null) {
            throw new IllegalArgumentException("modifyBiome requires an options table");
        }
        List<String> biomes = reqStringList(options, "biomes", "biome", "options.biomes");
        for (String biome : biomes) {
            validateBiomeRef(biome);
        }
        String defaultStep = optStep(options, "step", DEFAULT_STEP);
        List<AddedFeature> added = parseAddedFeatures(options, defaultStep);
        List<String> removed = parseRemovedFeatures(options);
        List<String> removeSteps = parseRemoveSteps(options);
        if (added.isEmpty() && removed.isEmpty()) {
            throw new IllegalArgumentException(
                    "modifyBiome requires at least one of options.add / options.remove");
        }
        pendingBiomeMods.add(new BiomeModification(biomeId, List.copyOf(biomes),
                List.copyOf(added), List.copyOf(removed), List.copyOf(removeSteps)));
    }

    @Override
    public void addFeature(@NotNull String featureId, @NotNull ILuaTable spec) {
        validateResourceLocation(featureId, "featureId");
        if (spec == null) {
            throw new IllegalArgumentException("addFeature requires a spec table");
        }
        String block = reqString(spec, "block", "spec.block");
        validateResourceLocation(block, "spec.block");
        List<String> biomes = reqStringList(spec, "biomes", "biome", "spec.biomes");
        for (String biome : biomes) {
            validateBiomeRef(biome);
        }
        if (isFeatureIdTaken(featureId)) {
            throw new IllegalArgumentException(
                    "featureId '" + featureId + "' is already registered; call ClearAll or use a unique id");
        }
        Placement placement = parsePlacement(spec);
        boolean scattered = optBoolean(spec, "scattered", false);
        pendingFeatures.add(new CustomFeature(featureId, block, List.copyOf(biomes),
                placement.step(), parseReplace(spec),
                placement.count(), placement.size(), placement.minHeight(), placement.maxHeight(),
                placement.heightType(), placement.anchorMin(), placement.anchorMax(),
                placement.rarity(), placement.discardChance(), scattered, placement.inner()));
    }

    /**
     * Shared placement fields for ore-like registrations (vanilla placement
     * modifiers: count/rarity, height provider, air-exposure thinning).
     * Parsed once here so {@link #addFeature} and {@link #addMixedVein} cannot
     * drift apart.
     */
    record Placement(String step, int count, Integer rarity, int size,
                     int minHeight, int maxHeight, String heightType,
                     String anchorMin, String anchorMax,
                     double discardChance, int inner) {}

    private @NotNull Placement parsePlacement(@NotNull ILuaTable spec) {
        String step = optStep(spec, "step", DEFAULT_STEP);
        int count = optInt(spec, "count", DEFAULT_FEATURE_COUNT);
        int size = optInt(spec, "size", DEFAULT_FEATURE_SIZE);
        int minHeight = optInt(spec, "minHeight", DEFAULT_MIN_HEIGHT);
        int maxHeight = optInt(spec, "maxHeight", DEFAULT_MAX_HEIGHT);
        if (count <= 0) {
            throw new IllegalArgumentException("spec.count must be > 0, got: " + count);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("spec.size must be > 0, got: " + size);
        }
        if (minHeight > maxHeight) {
            throw new IllegalArgumentException(
                    "spec.minHeight (" + minHeight + ") > spec.maxHeight (" + maxHeight + ")");
        }
        String heightType = optString(spec, "heightType");
        if (heightType == null || heightType.isEmpty()) {
            heightType = DEFAULT_HEIGHT_TYPE;
        } else if (!KNOWN_HEIGHT_TYPES.contains(heightType)) {
            throw new IllegalArgumentException("spec.heightType must be one of "
                    + KNOWN_HEIGHT_TYPES + ", got: " + heightType);
        }
        String anchorMin = optAnchor(spec, "anchorMin");
        String anchorMax = optAnchor(spec, "anchorMax");
        boolean hasCount = hasExplicit(spec, "count");
        boolean hasRarity = hasExplicit(spec, "rarity");
        if (hasCount && hasRarity) {
            throw new IllegalArgumentException(
                    "spec.count and spec.rarity are mutually exclusive (count = N veins per chunk, "
                    + "rarity = 1-in-N chunk chance like vanilla diamond_large); set exactly one");
        }
        Integer rarity = null;
        if (hasRarity) {
            int rawRarity = optInt(spec, "rarity", 1);
            if (rawRarity <= 0) {
                throw new IllegalArgumentException("spec.rarity must be > 0, got: " + rawRarity);
            }
            rarity = rawRarity;
        }
        double discardChance = optDouble(spec, "discardChance", DEFAULT_DISCARD_CHANCE);
        if (!Double.isFinite(discardChance) || discardChance < 0.0 || discardChance > 1.0) {
            throw new IllegalArgumentException(
                    "spec.discardChance must be between 0.0 and 1.0, got: " + discardChance);
        }
        int inner = optInt(spec, "inner", DEFAULT_BIASED_INNER);
        if (inner < 0) {
            throw new IllegalArgumentException("spec.inner must be >= 0, got: " + inner);
        }
        return new Placement(step, count, rarity, size, minHeight, maxHeight,
                heightType, anchorMin, anchorMax, discardChance, inner);
    }

    private @NotNull String parseReplace(@NotNull ILuaTable spec) {
        String replace = optString(spec, "replace");
        if (replace == null || replace.isEmpty()) {
            return DEFAULT_REPLACE_KIND;
        }
        if (!KNOWN_REPLACE_KINDS.contains(replace)) {
            throw new IllegalArgumentException("spec.replace must be one of "
                    + KNOWN_REPLACE_KINDS + ", got: " + replace);
        }
        return replace;
    }

    /** True when any pending registration (feature, raw, vein) already owns the id. */
    private boolean isFeatureIdTaken(@NotNull String featureId) {
        return pendingFeatures.stream().anyMatch(f -> f.featureId().equals(featureId))
                || pendingRawConfigured.stream().anyMatch(f -> f.featureId().equals(featureId))
                || pendingMixedVeins.stream().anyMatch(v -> v.veinId().equals(featureId));
    }

    /**
     * One ore candidate inside a mixed vein: the block to place and its
     * relative weight among the candidates matching the same position.
     */
    public record VeinOre(String block, int weight) {}

    /**
     * GTNH-style mixed vein: one ellipsoid blob (custom
     * {@code luatweaker:mixed_vein} feature) mixing several ores by weight.
     * Placement fields mirror {@link CustomFeature} (same vanilla modifiers).
     */
    public record MixedVein(String veinId, List<VeinOre> ores, String replace, List<String> biomes,
                            String step, int count, Integer rarity, int size,
                            int minHeight, int maxHeight, String heightType,
                            String anchorMin, String anchorMax,
                            double discardChance, int inner) {}

    /**
     * Vanilla {@code OreConfiguration} caps {@code size} at 64; mixed veins are
     * meant to be bigger blobs, and the custom codec allows 0-256 (the
     * ellipsoid math is identical, ~100 blocks wide at the cap).
     */
    private static final int MAX_VEIN_SIZE = 256;

    @Override
    public void addMixedVein(@NotNull String veinId, @NotNull ILuaTable spec) {
        validateResourceLocation(veinId, "veinId");
        if (spec == null) {
            throw new IllegalArgumentException("addMixedVein requires a spec table");
        }
        List<VeinOre> ores = parseVeinOres(spec);
        String replace = parseReplace(spec);
        List<String> biomes = reqStringList(spec, "biomes", "biome", "spec.biomes");
        for (String biome : biomes) {
            validateBiomeRef(biome);
        }
        if (isFeatureIdTaken(veinId)) {
            throw new IllegalArgumentException(
                    "featureId '" + veinId + "' is already registered; call ClearAll or use a unique id");
        }
        Placement placement = parsePlacement(spec);
        if (placement.size() > MAX_VEIN_SIZE) {
            throw new IllegalArgumentException("spec.size must be <= " + MAX_VEIN_SIZE
                    + " (mixed_vein codec bound), got: " + placement.size());
        }
        pendingMixedVeins.add(new MixedVein(veinId, List.copyOf(ores), replace, List.copyOf(biomes),
                placement.step(), placement.count(), placement.rarity(), placement.size(),
                placement.minHeight(), placement.maxHeight(), placement.heightType(),
                placement.anchorMin(), placement.anchorMax(),
                placement.discardChance(), placement.inner()));
    }

    private @NotNull List<VeinOre> parseVeinOres(@NotNull ILuaTable spec) {
        ILuaValue value = spec.rawget("ores");
        if (value == null || value.isNil()) {
            throw new IllegalArgumentException("spec.ores is required (list of {block=..., weight=...})");
        }
        List<VeinOre> ores = new ArrayList<>();
        if (value.isTable()) {
            ILuaTable table = value.asTable();
            if (table == null) {
                throw new IllegalArgumentException("spec.ores must be a list of {block=..., weight=...}");
            }
            if (table.length() == 0) {
                ILuaValue singleBlock = table.rawget("block");
                if (singleBlock != null && !singleBlock.isNil()) {
                    ores.add(parseVeinOre(table, "spec.ores"));
                    return ores;
                }
                throw new IllegalArgumentException("spec.ores must not be empty");
            }
            for (int i = 1; i <= table.length(); i++) {
                ILuaValue item = table.rawget(i);
                if (item == null || item.isNil() || !item.isTable() || item.asTable() == null) {
                    throw new IllegalArgumentException("spec.ores[" + i + "] must be {block=..., weight=...}");
                }
                ores.add(parseVeinOre(item.asTable(), "spec.ores[" + i + "]"));
            }
            return ores;
        }
        throw new IllegalArgumentException("spec.ores must be a list of {block=..., weight=...}");
    }

    private @NotNull VeinOre parseVeinOre(@NotNull ILuaTable entry, @NotNull String what) {
        String block = reqString(entry, "block", what + ".block");
        validateResourceLocation(block, what + ".block");
        int weight = 1;
        ILuaValue rawWeight = entry.rawget("weight");
        if (rawWeight != null && !rawWeight.isNil()) {
            if (!rawWeight.isNumber()) {
                throw new IllegalArgumentException(what + ".weight must be a number");
            }
            weight = (int) rawWeight.asDouble();
        }
        if (weight <= 0) {
            throw new IllegalArgumentException(what + ".weight must be > 0, got: " + weight);
        }
        return new VeinOre(block, weight);
    }

    @Override
    public void addRawConfiguredFeature(@NotNull String featureId, @NotNull String json) {
        validateResourceLocation(featureId, "featureId");
        String type = parseRawJson(json, "configured", "type", featureId);
        if (!KNOWN_FEATURE_TYPES.contains(type)) {
            throw new IllegalArgumentException("AddRawConfiguredFeature: unknown vanilla type '" + type
                    + "' for '" + featureId + "' (must be one of the 1.21.1 configured-feature types)");
        }
        if (isFeatureIdTaken(featureId)) {
            throw new IllegalArgumentException(
                    "featureId '" + featureId + "' is already registered; call ClearAll or use a unique id");
        }
        pendingRawConfigured.add(new RawConfigured(featureId, json));
    }

    @Override
    public void addRawPlacedFeature(@NotNull String featureId, @NotNull String json) {
        validateResourceLocation(featureId, "featureId");
        parseRawJson(json, "placed", "feature", featureId);
        if (pendingRawPlaced.stream().anyMatch(f -> f.featureId().equals(featureId))) {
            throw new IllegalArgumentException(
                    "featureId '" + featureId + "' is already registered; call ClearAll or use a unique id");
        }
        pendingRawPlaced.add(new RawPlaced(featureId, json));
    }

    /**
     * Parses raw JSON, requiring a string field (the type or feature
     * reference). Returns the field value.
     */
    private @NotNull String parseRawJson(@NotNull String json, @NotNull String kind,
                                         @NotNull String field, @NotNull String featureId) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("AddRaw JSON must not be blank for '" + featureId + "'");
        }
        com.google.gson.JsonObject parsed;
        try {
            parsed = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "AddRaw " + kind + " JSON for '" + featureId + "' does not parse: " + e.getMessage());
        }
        com.google.gson.JsonElement member = parsed.get(field);
        if (member == null || !member.isJsonPrimitive() || !member.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("AddRaw " + kind + " JSON for '" + featureId
                    + "' requires a string '" + field + "' field");
        }
        return member.getAsString();
    }

    @Override
    public boolean registerRemovalFeatures(@NotNull String blockId, @NotNull String[] features) {
        validateResourceLocation(blockId, "blockId");
        if (features.length == 0) {
            throw new IllegalArgumentException("features must not be empty for '" + blockId + "'");
        }
        for (String feature : features) {
            validateResourceLocation(feature, "features entry");
        }
        removalOverrides.put(blockId, List.of(features));
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "[Worldgen] Registered removal features for '" + blockId + "': " + List.of(features));
        return true;
    }

    @Override
    public boolean registerDimensionBiomeTag(@NotNull String dimension, @NotNull String biomeTag) {
        validateResourceLocation(dimension, "dimension");
        if (biomeTag == null || biomeTag.isEmpty()) {
            throw new IllegalArgumentException("biomeTag must not be empty");
        }
        if (biomeTag.startsWith("#")) {
            validateResourceLocation(biomeTag.substring(1), "biomeTag target");
        } else {
            validateBiomeRef(biomeTag);
        }
        dimensionTags.put(dimension, biomeTag);
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "[Worldgen] Registered dimension '" + dimension + "' with biome tag '" + biomeTag + "'");
        return true;
    }

    @Override
    public @NotNull Map<String, String> getKnownDimensions() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(dimensionTags));
    }

    @Override
    public @NotNull Map<String, Object> getBiome(@NotNull String biomeId) {
        String message = "STUB getBiome('" + biomeId + "') is not supported: live biome queries need "
                + "a platform PAL capability that does not exist yet (follow-up work, see docs/WORLDGEN.md). "
                + "Use modifyBiome with explicit biome IDs instead.";
        LuaTweakerLog.get().error(LogStage.SYSTEM, "[Worldgen] " + message);
        throw new UnsupportedOperationException(message);
    }

    @Override
    public @NotNull List<String> getRemovalFeatures(@NotNull String blockId) {
        validateResourceLocation(blockId, "blockId");
        return List.copyOf(resolveRemovalFeatures(blockId));
    }

    @Override
    public @Nullable String getDimensionBiomeTag(@NotNull String dimension) {
        validateResourceLocation(dimension, "dimension");
        return resolveDimensionBiomeTag(dimension).orElse(null);
    }

    @Override
    @NotNull
    public Map<String, Object> getModifications() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ores", pendingOres.size());
        summary.put("vegetation", pendingVegetation.size());
        summary.put("removals", pendingRemovals.size());
        summary.put("biomeModifiers", pendingBiomeMods.size());
        summary.put("customFeatures", pendingFeatures.size());
        summary.put("mixedVeins", pendingMixedVeins.size());
        summary.put("rawConfigured", pendingRawConfigured.size());
        summary.put("rawPlaced", pendingRawPlaced.size());
        return summary;
    }

    @Override
    public void clearAll() {
        pendingOres.clear();
        pendingVegetation.clear();
        pendingRemovals.clear();
        pendingBiomeMods.clear();
        pendingFeatures.clear();
        pendingMixedVeins.clear();
        pendingRawConfigured.clear();
        pendingRawPlaced.clear();
    }

    public List<OreEntry> getPendingOres() {
        return Collections.unmodifiableList(pendingOres);
    }

    public List<VegetationEntry> getPendingVegetation() {
        return Collections.unmodifiableList(pendingVegetation);
    }

    public List<OreRemovalEntry> getPendingRemovals() {
        return Collections.unmodifiableList(pendingRemovals);
    }

    public List<BiomeModification> getPendingBiomeMods() {
        return Collections.unmodifiableList(pendingBiomeMods);
    }

    public List<CustomFeature> getPendingFeatures() {
        return Collections.unmodifiableList(pendingFeatures);
    }

    public List<MixedVein> getPendingMixedVeins() {
        return Collections.unmodifiableList(pendingMixedVeins);
    }

    public List<RawConfigured> getPendingRawConfigured() {
        return Collections.unmodifiableList(pendingRawConfigured);
    }

    public List<RawPlaced> getPendingRawPlaced() {
        return Collections.unmodifiableList(pendingRawPlaced);
    }

    /**
     * Placed-feature IDs to strip for an ore block: user override first,
     * then the vanilla defaults. Empty = unknown block; the caller must
     * skip loudly instead of emitting a modifier that references nothing.
     */
    public @NotNull List<String> resolveRemovalFeatures(@NotNull String blockId) {
        List<String> override = removalOverrides.get(blockId);
        if (override != null) {
            return override;
        }
        List<String> defaults = DEFAULT_REMOVAL_FEATURES.get(blockId);
        return defaults != null ? defaults : List.of();
    }

    /**
     * Biome tag (or single biome ID) for a dimension. Empty = the dimension
     * is not registered; the caller must verify existence platform-side and
     * skip loudly instead of emitting a modifier with a dangling selector.
     */
    public @NotNull Optional<String> resolveDimensionBiomeTag(@NotNull String dimension) {
        return Optional.ofNullable(dimensionTags.get(dimension));
    }

    /**
     * Builds the datapack files for raw passthrough entries as
     * relPath -&gt; JSON, using the same path conventions as the provider
     * ({@code luatweaker/worldgen/(configured|placed)_feature/*.json}).
     * JSON was validated at registration; it is emitted verbatim.
     */
    public @NotNull Map<String, String> buildRawFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        for (RawConfigured raw : getPendingRawConfigured()) {
            files.put("luatweaker/worldgen/configured_feature/" + sanitizeFileId(raw.featureId()) + ".json", raw.json());
        }
        for (RawPlaced raw : getPendingRawPlaced()) {
            files.put("luatweaker/worldgen/placed_feature/" + sanitizeFileId(raw.featureId()) + ".json", raw.json());
        }
        return files;
    }

    /**
     * Builds every datapack file for the NEW pending entries (biome
     * modifications + custom features) as relPath -&gt; JSON, using the same
     * path conventions the platform provider uses for ores
     * ({@code luatweaker/neoforge/biome_modifier/*.json},
     * {@code luatweaker/worldgen/(configured|placed)_feature/*.json}).
     * The provider feeds each entry to {@code IDatapackService#addData}.
     */
    public @NotNull Map<String, String> buildBiomeModifierFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        List<BiomeModification> mods = getPendingBiomeMods();
        for (int i = 0; i < mods.size(); i++) {
            BiomeModification mod = mods.get(i);
            String safe = sanitizeFileId(mod.biomeId());
            if (!mod.added().isEmpty()) {
                Map<String, List<String>> byStep = new LinkedHashMap<>();
                for (AddedFeature add : mod.added()) {
                    byStep.computeIfAbsent(add.step(), k -> new ArrayList<>()).add(add.feature());
                }
                for (Map.Entry<String, List<String>> group : byStep.entrySet()) {
                    String stepSafe = sanitizeFileId(group.getKey());
                    files.put("luatweaker/neoforge/biome_modifier/modify_" + safe + "_" + i + "_" + stepSafe + ".json",
                            buildAddFeaturesModifier(mod.biomes(), group.getValue(), group.getKey()));
                }
            }
            if (!mod.removed().isEmpty()) {
                files.put("luatweaker/neoforge/biome_modifier/modify_" + safe + "_" + i + "_remove.json",
                        buildRemoveFeaturesModifier(mod.biomes(), mod.removed(), mod.removeSteps()));
            }
        }
        List<CustomFeature> features = getPendingFeatures();
        for (int i = 0; i < features.size(); i++) {
            CustomFeature feature = features.get(i);
            String safe = sanitizeFileId(feature.featureId());
            files.put("luatweaker/worldgen/configured_feature/" + safe + ".json",
                    buildConfiguredOreFeature(feature));
            files.put("luatweaker/worldgen/placed_feature/" + safe + ".json",
                    buildPlacedOreFeature(safe, feature));
            files.put("luatweaker/neoforge/biome_modifier/add_" + safe + ".json",
                    buildAddFeaturesModifier(feature.biomes(), List.of("luatweaker:" + safe), feature.step()));
        }
        return files;
    }

    /**
     * Builds the configured-feature JSON for a vegetation entry.
     * Flower shape verified against {@code flower_plain.json};
     * patch shape against {@code patch_grass.json} (1.21.1 client jar).
     * Always uses the typed {@code simple_state_provider} form for
     * {@code to_place} (verified in {@code dripleaf.json}).
     */
    public @NotNull String buildVegetationConfiguredJson(@NotNull VegetationEntry veg) {
        String type;
        int tries;
        int xzSpread;
        int ySpread;
        if (KIND_PATCH.equals(veg.kind())) {
            type = "minecraft:random_patch";
            tries = PATCH_TRIES;
            xzSpread = PATCH_XZ_SPREAD;
            ySpread = PATCH_Y_SPREAD;
        } else {
            type = "minecraft:flower";
            tries = FLOWER_TRIES;
            xzSpread = FLOWER_XZ_SPREAD;
            ySpread = FLOWER_Y_SPREAD;
        }
        return "{"
            + "\"type\":\"" + type + "\","
            + "\"config\":{"
            +   "\"tries\":" + tries + ","
            +   "\"xz_spread\":" + xzSpread + ","
            +   "\"y_spread\":" + ySpread + ","
            +   "\"feature\":{"
            +     "\"feature\":{"
            +       "\"type\":\"minecraft:simple_block\","
            +       "\"config\":{\"to_place\":{"
            +         "\"type\":\"minecraft:simple_state_provider\","
            +         "\"state\":{\"Name\":\"" + escapeJson(veg.blockId()) + "\"}"
            +       "}}"
            +     "},"
            +     "\"placement\":[{\"type\":\"minecraft:block_predicate_filter\","
            +       "\"predicate\":{\"type\":\"minecraft:matching_blocks\",\"blocks\":\"minecraft:air\"}}]"
            +   "}"
            + "}"
            + "}";
    }

    private @NotNull String buildConfiguredOreFeature(@NotNull CustomFeature feature) {
        // scattered_ore (ancient-debris pattern) exposes veins instead of burying
        // them; discard_chance_on_air_exposure (e.g. vanilla diamond_large 0.7)
        // thins veins that touch air.
        return "{"
            + "\"type\":\"minecraft:" + (feature.scattered() ? "scattered_ore" : "ore") + "\","
            + "\"config\":{"
            +   "\"discard_chance_on_air_exposure\":" + feature.discardChance() + ","
            +   "\"size\":" + feature.size() + ","
            +   "\"targets\":[" + oreTargets(feature) + "]"
            + "}"
            + "}";
    }

    /**
     * Replaceable targets by rock kind. Stone (vanilla {@code ore_coal.json})
     * and nether (vanilla {@code ore_nether_gold.json}) shapes verified
     * against the 1.21.1 client jar. End ({@code end_stone}) follows the
     * provider convention; UNVERIFIED, no vanilla end-ore precedent exists.
     */
    private @NotNull String oreTargets(@NotNull CustomFeature feature) {
        return String.join(",", rockTargets(feature.block(), feature.replace()));
    }

    /**
     * Replaceable rock predicates by kind, shared by ore features and mixed
     * veins. Each entry pairs one predicate with one state; mixed veins repeat
     * the pair per ore with its own weight.
     */
    private @NotNull List<String> rockTargets(@NotNull String block, @NotNull String replace) {
        String state = "\"state\":{\"Name\":\"" + escapeJson(block) + "\"}";
        return switch (replace) {
            case "nether" -> List.of(
                    "{\"target\":{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:netherrack\"},"
                    + state + "}");
            case "end" -> List.of(
                    "{\"target\":{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:end_stone\"},"
                    + state + "}");
            default -> List.of(
                    "{\"target\":{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:stone_ore_replaceables\"},"
                    + state + "}",
                    "{\"target\":{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:deepslate_ore_replaceables\"},"
                    + state + "}");
        };
    }

    private @NotNull String buildPlacedOreFeature(@NotNull String safeId, @NotNull CustomFeature feature) {
        return buildPlacedFeatureJson("luatweaker:" + safeId, new Placement(
                feature.step(), feature.count(), feature.rarity(), feature.size(),
                feature.minHeight(), feature.maxHeight(), feature.heightType(),
                feature.anchorMin(), feature.anchorMax(), feature.discardChance(), feature.inner()));
    }

    /**
     * Placed-feature JSON from shared placement fields. The {@code feature}
     * reference is the full id (vanilla ids pass through for raw-adjacent
     * flows, {@code luatweaker:} ids for generated ones).
     */
    private @NotNull String buildPlacedFeatureJson(@NotNull String featureRef, @NotNull Placement placement) {
        // Frequency: count = N attempts per chunk (ore_coal_upper pattern) or
        // rarity = 1-in-N chunk chance (ore_diamond_large pattern); mutually
        // exclusive, enforced at registration.
        String frequency = placement.rarity() != null
                ? "{\"type\":\"minecraft:rarity_filter\",\"chance\":" + placement.rarity() + "}"
                : "{\"type\":\"minecraft:count\",\"count\":" + placement.count() + "}";
        String height = "{\"type\":\"minecraft:" + placement.heightType() + "\","
                + "\"min_inclusive\":{\"" + placement.anchorMin() + "\":" + placement.minHeight() + "},"
                + "\"max_inclusive\":{\"" + placement.anchorMax() + "\":" + placement.maxHeight() + "}"
                + (isBiased(placement.heightType()) ? ",\"inner\":" + placement.inner() : "")
                + "}";
        return "{"
            + "\"feature\":\"" + escapeJson(featureRef) + "\","
            + "\"placement\":["
            +   frequency + ","
            +   "{\"type\":\"minecraft:in_square\"},"
            +   "{\"type\":\"minecraft:height_range\",\"height\":" + height + "},"
            +   "{\"type\":\"minecraft:biome\"}"
            + "]"
            + "}";
    }

    /**
     * Builds the datapack files for mixed veins: one custom configured feature
     * ({@code luatweaker:mixed_vein}, registered by the platform) + one placed
     * feature + one add_features modifier per vein. Target field names
     * ({@code target}/{@code state}) mirror {@code OreConfiguration}, plus the
     * custom {@code weight} each candidate carries.
     */
    public @NotNull Map<String, String> buildMixedVeinFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        for (MixedVein vein : getPendingMixedVeins()) {
            String safe = sanitizeFileId(vein.veinId());
            files.put("luatweaker/worldgen/configured_feature/" + safe + ".json",
                    buildMixedVeinConfiguredJson(vein));
            files.put("luatweaker/worldgen/placed_feature/" + safe + ".json",
                    buildPlacedFeatureJson("luatweaker:" + safe, new Placement(
                            vein.step(), vein.count(), vein.rarity(), vein.size(),
                            vein.minHeight(), vein.maxHeight(), vein.heightType(),
                            vein.anchorMin(), vein.anchorMax(),
                            vein.discardChance(), vein.inner())));
            files.put("luatweaker/neoforge/biome_modifier/add_" + safe + ".json",
                    buildAddFeaturesModifier(vein.biomes(), List.of("luatweaker:" + safe), vein.step()));
        }
        return files;
    }

    private @NotNull String buildMixedVeinConfiguredJson(@NotNull MixedVein vein) {
        StringBuilder targets = new StringBuilder();
        for (VeinOre ore : vein.ores()) {
            for (String rockTarget : rockTargets(ore.block(), vein.replace())) {
                if (targets.length() > 0) {
                    targets.append(",");
                }
                targets.append(rockTarget, 0, rockTarget.length() - 1);
                targets.append(",\"weight\":").append(ore.weight()).append("}");
            }
        }
        return "{"
            + "\"type\":\"luatweaker:mixed_vein\","
            + "\"config\":{"
            +   "\"size\":" + vein.size() + ","
            +   "\"discard_chance_on_air_exposure\":" + vein.discardChance() + ","
            +   "\"targets\":[" + targets + "]"
            + "}"
            + "}";
    }

    private static boolean isBiased(@NotNull String heightType) {
        return "biased_to_bottom".equals(heightType) || "very_biased_to_bottom".equals(heightType);
    }

    /**
     * NeoForge add-features modifier. Field names ({@code type},
     * {@code biomes}, {@code features}, singular {@code step}) verified
     * against the 21.1.242 codec constant pool.
     */
    private @NotNull String buildAddFeaturesModifier(@NotNull List<String> biomes,
                                                      @NotNull List<String> features,
                                                      @NotNull String step) {
        StringBuilder list = new StringBuilder("[");
        for (int i = 0; i < features.size(); i++) {
            if (i > 0) {
                list.append(",");
            }
            list.append("\"").append(escapeJson(features.get(i))).append("\"");
        }
        list.append("]");
        return "{"
            + "\"type\":\"neoforge:add_features\","
            + "\"biomes\":" + biomeSelector(biomes) + ","
            + "\"features\":" + list + ","
            + "\"step\":\"" + escapeJson(step) + "\""
            + "}";
    }

    /**
     * NeoForge remove-features modifier. The codec takes plural
     * {@code steps} (verified: {@code Set<Decoration> steps} record component
     * in NeoForge 21.1.242 {@code RemoveFeaturesBiomeModifier}), serialized as
     * a JSON list. The platform provider emits the same plural form.
     */
    private @NotNull String buildRemoveFeaturesModifier(@NotNull List<String> biomes,
                                                        @NotNull List<String> features,
                                                        @NotNull List<String> steps) {
        StringBuilder featureList = new StringBuilder("[");
        for (int i = 0; i < features.size(); i++) {
            if (i > 0) {
                featureList.append(",");
            }
            featureList.append("\"").append(escapeJson(features.get(i))).append("\"");
        }
        featureList.append("]");
        StringBuilder stepList = new StringBuilder("[");
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) {
                stepList.append(",");
            }
            stepList.append("\"").append(escapeJson(steps.get(i))).append("\"");
        }
        stepList.append("]");
        return "{"
            + "\"type\":\"neoforge:remove_features\","
            + "\"biomes\":" + biomeSelector(biomes) + ","
            + "\"features\":" + featureList + ","
            + "\"steps\":" + stepList
            + "}";
    }

    private @NotNull String biomeSelector(@NotNull List<String> biomes) {
        StringBuilder list = new StringBuilder("[");
        for (int i = 0; i < biomes.size(); i++) {
            if (i > 0) {
                list.append(",");
            }
            list.append("\"").append(escapeJson(biomes.get(i))).append("\"");
        }
        return list.append("]").toString();
    }

    private static @NotNull String sanitizeFileId(@NotNull String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private static @NotNull String escapeJson(@NotNull String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private @NotNull List<AddedFeature> parseAddedFeatures(@NotNull ILuaTable options,
                                                           @NotNull String defaultStep) {
        ILuaValue add = options.rawget("add");
        if (add == null || add.isNil()) {
            return List.of();
        }
        List<AddedFeature> result = new ArrayList<>();
        if (add.isTable()) {
            ILuaTable table = add.asTable();
            if (table == null) {
                throw new IllegalArgumentException("options.add must be a feature ID string or a list/table");
            }
            int length = table.length();
            if (length == 0) {
                ILuaValue singleFeature = table.rawget("feature");
                if (singleFeature != null && !singleFeature.isNil()) {
                    result.add(parseSingleAdded(table, defaultStep));
                    return result;
                }
                return List.of();
            }
            for (int i = 1; i <= length; i++) {
                ILuaValue item = table.rawget(i);
                if (item == null || item.isNil()) {
                    break;
                }
                result.add(parseAddedItem(item, defaultStep, "options.add[" + i + "]"));
            }
            return result;
        }
        return List.of(parseAddedItem(add, defaultStep, "options.add"));
    }

    private @NotNull AddedFeature parseAddedItem(@NotNull ILuaValue item,
                                                 @NotNull String defaultStep,
                                                 @NotNull String what) {
        if (item.isTable()) {
            ILuaTable table = item.asTable();
            if (table == null) {
                throw new IllegalArgumentException(what + " must be a feature ID string or {feature=..., step=...}");
            }
            return parseSingleAdded(table, defaultStep);
        }
        String feature = item.asString();
        validateResourceLocation(feature, what);
        return new AddedFeature(feature, defaultStep);
    }

    private @NotNull AddedFeature parseSingleAdded(@NotNull ILuaTable table,
                                                    @NotNull String defaultStep) {
        String feature = reqString(table, "feature", "options.add.feature");
        validateResourceLocation(feature, "options.add.feature");
        return new AddedFeature(feature, optStep(table, "step", defaultStep));
    }

    private @NotNull List<String> parseRemovedFeatures(@NotNull ILuaTable options) {
        ILuaValue remove = options.rawget("remove");
        if (remove == null || remove.isNil()) {
            return List.of();
        }
        List<String> features = asStringList(remove, "options.remove");
        for (String feature : features) {
            validateResourceLocation(feature, "options.remove entry");
        }
        return features;
    }

    private @NotNull List<String> parseRemoveSteps(@NotNull ILuaTable options) {
        ILuaValue steps = options.rawget("removeSteps");
        if (steps == null || steps.isNil()) {
            return List.of(DEFAULT_STEP);
        }
        List<String> parsed = asStringList(steps, "options.removeSteps");
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("options.removeSteps must not be empty");
        }
        for (String step : parsed) {
            parseStep(step, "options.removeSteps entry");
        }
        return parsed;
    }

    private @NotNull String optStep(@NotNull ILuaTable table, @NotNull String key,
                                    @NotNull String fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return fallback;
        }
        return parseStep(value.asString(), "options." + key);
    }

    private static @NotNull String parseStep(@NotNull String step, @NotNull String what) {
        if (!KNOWN_STEPS.contains(step)) {
            throw new IllegalArgumentException(what + " is not a known generation step, got: " + step);
        }
        return step;
    }

    private static @NotNull String reqString(@NotNull ILuaTable table, @NotNull String key,
                                             @NotNull String what) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            throw new IllegalArgumentException(what + " is required");
        }
        String result = value.asString();
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException(what + " must not be empty");
        }
        return result;
    }

    private static @Nullable String optString(@NotNull ILuaTable table, @NotNull String key) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return null;
        }
        return value.asString();
    }

    private static int optInt(@NotNull ILuaTable table, @NotNull String key, int fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return fallback;
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException("spec." + key + " must be a number");
        }
        return (int) value.asDouble();
    }

    private static double optDouble(@NotNull ILuaTable table, @NotNull String key, double fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return fallback;
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException("spec." + key + " must be a number");
        }
        return value.asDouble();
    }

    private static boolean optBoolean(@NotNull ILuaTable table, @NotNull String key, boolean fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return fallback;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException("spec." + key + " must be a boolean");
        }
        return value.asBoolean();
    }

    private static boolean hasExplicit(@NotNull ILuaTable table, @NotNull String key) {
        ILuaValue value = table.rawget(key);
        return value != null && !value.isNil();
    }

    private static @NotNull String optAnchor(@NotNull ILuaTable table, @NotNull String key) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return DEFAULT_ANCHOR;
        }
        String anchor = value.asString();
        if (!KNOWN_ANCHORS.contains(anchor)) {
            throw new IllegalArgumentException("spec." + key + " must be one of "
                    + KNOWN_ANCHORS + ", got: " + anchor);
        }
        return anchor;
    }

    private static @NotNull List<String> reqStringList(@NotNull ILuaTable table,
                                                        @NotNull String primary,
                                                        @Nullable String alias,
                                                        @NotNull String what) {
        ILuaValue value = table.rawget(primary);
        if ((value == null || value.isNil()) && alias != null) {
            value = table.rawget(alias);
        }
        if (value == null || value.isNil()) {
            throw new IllegalArgumentException(what + " is required (string or list of strings)");
        }
        List<String> result = asStringList(value, what);
        if (result.isEmpty()) {
            throw new IllegalArgumentException(what + " must not be empty");
        }
        return result;
    }

    private static @NotNull List<String> asStringList(@NotNull ILuaValue value, @NotNull String what) {
        if (!value.isTable()) {
            String single = value.asString();
            if (single == null || single.isEmpty()) {
                throw new IllegalArgumentException(what + " must be a string or list of strings");
            }
            return List.of(single);
        }
        ILuaTable table = value.asTable();
        if (table == null) {
            throw new IllegalArgumentException(what + " must be a string or list of strings");
        }
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= table.length(); i++) {
            ILuaValue item = table.rawget(i);
            if (item == null || item.isNil()) {
                break;
            }
            String entry = item.asString();
            if (entry == null || entry.isEmpty()) {
                throw new IllegalArgumentException(what + "[" + i + "] must be a string");
            }
            result.add(entry);
        }
        return result;
    }

    private @NotNull List<String> validateVegetationArgs(@NotNull String blockId, double chance,
                                                         @NotNull String[] biomes) {
        if (!RESOURCE_LOCATION.matcher(blockId).matches()) {
            throw new IllegalArgumentException("blockId must be a valid resource location (e.g. 'mymod:ruby_flower'), got: " + blockId);
        }
        if (chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("chance must be between 0.0 and 1.0, got: " + chance);
        }
        if (biomes.length == 0) {
            throw new IllegalArgumentException("biomes must not be empty");
        }
        for (String biome : biomes) {
            validateBiomeRef(biome);
        }
        return List.of(biomes);
    }

    private static void validateBiomeRef(@NotNull String biome) {
        if (biome.startsWith("#")) {
            validateResourceLocation(biome.substring(1), "biome tag target");
            return;
        }
        if (!RESOURCE_LOCATION.matcher(biome).matches()) {
            throw new IllegalArgumentException("biome must be a valid resource location or tag (e.g. 'minecraft:plains' or '#minecraft:is_overworld'), got: " + biome);
        }
    }

    private static void validateResourceLocation(@NotNull String id, @NotNull String what) {
        if (!RESOURCE_LOCATION.matcher(id).matches()) {
            throw new IllegalArgumentException(what + " must be a valid resource location, got: " + id);
        }
    }

    private void validate(@NotNull String blockId, @NotNull String dimension,
                           int minHeight, int maxHeight, int clusterSize, int frequency) {
        if (!RESOURCE_LOCATION.matcher(blockId).matches()) {
            throw new IllegalArgumentException("blockId must be a valid resource location (e.g. 'mymod:ruby_ore'), got: " + blockId);
        }
        if (!RESOURCE_LOCATION.matcher(dimension).matches()) {
            throw new IllegalArgumentException("dimension must be a valid resource location (e.g. 'minecraft:overworld'), got: " + dimension);
        }
        if (minHeight > maxHeight) {
            throw new IllegalArgumentException("minHeight (" + minHeight + ") > maxHeight (" + maxHeight + ")");
        }
        if (clusterSize <= 0) {
            throw new IllegalArgumentException("clusterSize must be > 0, got: " + clusterSize);
        }
        if (frequency <= 0) {
            throw new IllegalArgumentException("frequency must be > 0, got: " + frequency);
        }
    }
}

package com.luatweaker.worldgen;

import com.luatweaker.api.worldgen.GenerationSteps;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single home for the worldgen defaults and the verified 1.21.1 type/step tables.
 * Kept separate from the service so parsers, validators and JSON builders share one
 * source of truth and the service stays a thin registration front end.
 */
final class WorldgenDefaults {

    /** Generated-id namespace used by every custom feature/vein file. */
    static final String NAMESPACE = "luatweaker:";

    /** Datapack roots written by the platform provider. */
    static final String CONFIGURED_FEATURE_DIR = "luatweaker/worldgen/configured_feature/";
    static final String PLACED_FEATURE_DIR = "luatweaker/worldgen/placed_feature/";
    static final String BIOME_MODIFIER_DIR = "luatweaker/neoforge/biome_modifier/";
    static final String JSON_SUFFIX = ".json";

    /** Default decoration step for added features (verified jar value). */
    static final String DEFAULT_STEP = "underground_ores";
    /** Defaults mirror vanilla placement shapes (verified against 1.21.1 placed features). */
    static final String DEFAULT_HEIGHT_TYPE = "uniform";
    static final String DEFAULT_ANCHOR = "absolute";
    static final double DEFAULT_DISCARD_CHANCE = 0.0;
    static final int DEFAULT_BIASED_INNER = 8;
    static final int DEFAULT_FEATURE_COUNT = 10;
    static final int DEFAULT_FEATURE_SIZE = 8;
    static final int DEFAULT_MIN_HEIGHT = -64;
    static final int DEFAULT_MAX_HEIGHT = 32;
    static final String DEFAULT_REPLACE_KIND = "stone";

    /** Vegetation feature kinds (flower/patch shapes verified against vanilla JSONs). */
    static final String KIND_FLOWER = "flower";
    static final String KIND_PATCH = "patch";
    static final int FLOWER_TRIES = 64;
    static final int FLOWER_XZ_SPREAD = 6;
    static final int FLOWER_Y_SPREAD = 2;
    static final int PATCH_TRIES = 32;
    static final int PATCH_XZ_SPREAD = 7;
    static final int PATCH_Y_SPREAD = 3;

    /** Vanilla {@code OreConfiguration} size cap; mixed veins allow the extended bound below. */
    static final int MAX_VEIN_SIZE = 256;

    /** Verified GenerationStep.Decoration serialized names (shared with the dimensions module). */
    static final Set<String> KNOWN_STEPS = new LinkedHashSet<>(GenerationSteps.ORDER);

    /** Height-provider types accepted for {@code spec.heightType} (see the service javadoc). */
    static final Set<String> KNOWN_HEIGHT_TYPES = Set.of(
            "uniform", "trapezoid", "biased_to_bottom", "very_biased_to_bottom");

    /** Vertical anchors for {@code spec.anchorMin}/{@code spec.anchorMax}. */
    static final Set<String> KNOWN_ANCHORS = Set.of("absolute", "above_bottom", "below_top");

    /** Target-rock selector for addFeature (mirrors the provider's ore-target mapping). */
    static final Set<String> KNOWN_REPLACE_KINDS = Set.of("stone", "nether", "end");

    /**
     * Configured-feature types present in the vanilla 1.21.1 datapack (enumerated from
     * data/minecraft/worldgen/configured_feature/*.json). Raw JSON with any other type
     * fails fast instead of dying silently at datapack load.
     */
    static final Set<String> KNOWN_FEATURE_TYPES = Set.of(
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

    /** Default dimension -&gt; biome-tag mapping (overridable via RegisterDimensionBiomeTag). */
    static final Map<String, String> DEFAULT_DIMENSION_TAGS = Map.of(
            "minecraft:overworld", "#minecraft:is_overworld",
            "minecraft:the_nether", "#minecraft:is_nether",
            "minecraft:the_end", "#minecraft:is_end");

    /** Default ore-block -&gt; vanilla placed-feature IDs (verified against the client jar). */
    static final Map<String, List<String>> DEFAULT_REMOVAL_FEATURES = Map.ofEntries(
            Map.entry("minecraft:coal_ore",
                    List.of("minecraft:ore_coal_upper", "minecraft:ore_coal_lower")),
            Map.entry("minecraft:iron_ore",
                    List.of("minecraft:ore_iron_upper", "minecraft:ore_iron_middle",
                            "minecraft:ore_iron_small")),
            Map.entry("minecraft:gold_ore",
                    List.of("minecraft:ore_gold", "minecraft:ore_gold_lower",
                            "minecraft:ore_gold_extra")),
            Map.entry("minecraft:redstone_ore",
                    List.of("minecraft:ore_redstone", "minecraft:ore_redstone_lower")),
            Map.entry("minecraft:lapis_ore",
                    List.of("minecraft:ore_lapis", "minecraft:ore_lapis_buried")),
            Map.entry("minecraft:diamond_ore",
                    List.of("minecraft:ore_diamond", "minecraft:ore_diamond_medium",
                            "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried")),
            Map.entry("minecraft:emerald_ore", List.of("minecraft:ore_emerald")),
            Map.entry("minecraft:copper_ore",
                    List.of("minecraft:ore_copper", "minecraft:ore_copper_large")),
            Map.entry("minecraft:nether_quartz_ore", List.of("minecraft:ore_quartz_nether")),
            Map.entry("minecraft:nether_gold_ore", List.of("minecraft:ore_gold_nether")),
            Map.entry("minecraft:ancient_debris",
                    List.of("minecraft:ore_ancient_debris_large", "minecraft:ore_debris_small")));

    private WorldgenDefaults() {}
}

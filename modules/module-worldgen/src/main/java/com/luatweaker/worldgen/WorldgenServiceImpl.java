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
     * Verified GenerationStep.Decoration serialized names (game jar:
     * GenerationStep$Decoration constructor strings in the 1.21.1
     * deobfuscated jar). Used to validate every step value up front so a
     * typo fails fast in Lua instead of silently breaking the datapack.
     */
    private static final Set<String> KNOWN_STEPS = Set.of(
            "raw_generation", "lakes", "local_modifications", "underground_structures",
            "surface_structures", "strongholds", "underground_ores", "underground_decoration",
            "fluid_springs", "vegetal_decoration", "top_layer_modification");

    /** Default decoration step for added features (verified jar value). */
    private static final String DEFAULT_STEP = "underground_ores";

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
                                String replace, int count, int size, int minHeight, int maxHeight) {}

    private final List<OreEntry> pendingOres = Collections.synchronizedList(new ArrayList<>());
    private final List<VegetationEntry> pendingVegetation = Collections.synchronizedList(new ArrayList<>());
    private final List<OreRemovalEntry> pendingRemovals = Collections.synchronizedList(new ArrayList<>());
    private final List<BiomeModification> pendingBiomeMods = Collections.synchronizedList(new ArrayList<>());
    private final List<CustomFeature> pendingFeatures = Collections.synchronizedList(new ArrayList<>());

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
        String step = optStep(spec, "step", DEFAULT_STEP);
        String replace = optString(spec, "replace");
        if (replace == null || replace.isEmpty()) {
            replace = DEFAULT_REPLACE_KIND;
        } else if (!KNOWN_REPLACE_KINDS.contains(replace)) {
            throw new IllegalArgumentException("spec.replace must be one of "
                    + KNOWN_REPLACE_KINDS + ", got: " + replace);
        }
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
        pendingFeatures.add(new CustomFeature(featureId, block, List.copyOf(biomes),
                step, replace, count, size, minHeight, maxHeight));
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
    @NotNull
    public Map<String, Object> getModifications() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ores", pendingOres.size());
        summary.put("vegetation", pendingVegetation.size());
        summary.put("removals", pendingRemovals.size());
        summary.put("biomeModifiers", pendingBiomeMods.size());
        summary.put("customFeatures", pendingFeatures.size());
        return summary;
    }

    @Override
    public void clearAll() {
        pendingOres.clear();
        pendingVegetation.clear();
        pendingRemovals.clear();
        pendingBiomeMods.clear();
        pendingFeatures.clear();
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
        return "{"
            + "\"type\":\"minecraft:ore\","
            + "\"config\":{"
            +   "\"discard_chance_on_air_exposure\":0.0,"
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
        String state = "\"state\":{\"Name\":\"" + escapeJson(feature.block()) + "\"}";
        return switch (feature.replace()) {
            case "nether" ->
                    "{\"target\":{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:netherrack\"},"
                    + state + "}";
            case "end" ->
                    "{\"target\":{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:end_stone\"},"
                    + state + "}";
            default ->
                    "{\"target\":{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:stone_ore_replaceables\"},"
                    + state + "},"
                    + "{\"target\":{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:deepslate_ore_replaceables\"},"
                    + state + "}";
        };
    }

    private @NotNull String buildPlacedOreFeature(@NotNull String safeId, @NotNull CustomFeature feature) {
        return "{"
            + "\"feature\":\"luatweaker:" + safeId + "\","
            + "\"placement\":["
            +   "{\"type\":\"minecraft:count\",\"count\":" + feature.count() + "},"
            +   "{\"type\":\"minecraft:in_square\"},"
            +   "{\"type\":\"minecraft:height_range\","
            +    "\"height\":{\"type\":\"minecraft:uniform\","
            +               "\"min_inclusive\":{\"absolute\":" + feature.minHeight() + "},"
            +               "\"max_inclusive\":{\"absolute\":" + feature.maxHeight() + "}"
            +    "}"
            +   "},"
            +   "{\"type\":\"minecraft:biome\"}"
            + "]"
            + "}";
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
     * + {@code "steps"} codec string in NeoForge 21.1.242), serialized as a
     * JSON list. NOTE: the platform provider currently emits singular
     * {@code "step"} here, which contradicts the jar; flagged for the
     * platform owner (see docs/WORLDGEN.md follow-ups).
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

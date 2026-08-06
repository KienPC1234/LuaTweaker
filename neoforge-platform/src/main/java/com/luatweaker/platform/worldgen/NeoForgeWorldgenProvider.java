package com.luatweaker.platform.worldgen;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Generates datapack JSON (configured/placed features + NeoForge biome modifiers)
 * for every pending worldgen entry. Runs after each mod-load pass; JSON structures
 * verified against the vanilla 1.21.1 datapack.
 */
public class NeoForgeWorldgenProvider {

    private final WorldgenServiceImpl worldgenService;
    private final IDatapackService datapackService;

    /** Vanilla placed-feature ids per vanilla ore block (verified in 1.21.1 data). */
    private static final Map<String, List<String>> VANILLA_ORE_FEATURES = Map.ofEntries(
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
            Map.entry("minecraft:ancient_debris", List.of("minecraft:ore_ancient_debris_large", "minecraft:ore_debris_small"))
    );

    public NeoForgeWorldgenProvider(@NotNull WorldgenServiceImpl worldgenService, @NotNull IDatapackService datapackService) {
        this.worldgenService = worldgenService;
        this.datapackService = datapackService;
    }

    public void applyAll() {
        int ores = applyOres();
        int vegetation = applyVegetation();
        int removals = applyRemovals();

        LuaTweakerLog.get().info(LogStage.SYSTEM,
            "[WorldgenProvider] Applied: " + ores + " ore entries, "
            + vegetation + " vegetation entries, "
            + removals + " removals");
    }

    private int applyOres() {
        List<WorldgenServiceImpl.OreEntry> ores = worldgenService.getPendingOres();
        for (int i = 0; i < ores.size(); i++) {
            WorldgenServiceImpl.OreEntry ore = ores.get(i);
            if (!isSupportedDimension(ore.dimension())) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "[WorldgenProvider] Skipped ore '" + ore.blockId() + "': unsupported dimension '" + ore.dimension()
                        + "' (supported: minecraft:overworld, minecraft:the_nether, minecraft:the_end)");
                continue;
            }
            String featureId = ore.blockId().replace(":", "_") + "_ore_" + i;
            datapackService.addData("luatweaker/worldgen/configured_feature/" + featureId + ".json",
                    buildConfiguredOreFeature(ore));
            datapackService.addData("luatweaker/worldgen/placed_feature/" + featureId + ".json",
                    buildPlacedOreFeature(featureId, ore));
            datapackService.addData("luatweaker/neoforge/biome_modifier/add_" + featureId + ".json",
                    buildAddFeatureModifier(featureId, ore.dimension(), ore.biomes(), "underground_ores"));
        }
        return ores.size();
    }

    private int applyVegetation() {
        List<WorldgenServiceImpl.VegetationEntry> entries = worldgenService.getPendingVegetation();
        int emitted = 0;
        for (int i = 0; i < entries.size(); i++) {
            WorldgenServiceImpl.VegetationEntry veg = entries.get(i);
            String baseFeatureId = veg.blockId().replace(":", "_") + "_veg_" + i;
            String configuredId = "luatweaker:" + baseFeatureId;
            datapackService.addData("luatweaker/worldgen/configured_feature/" + baseFeatureId + ".json",
                    buildConfiguredFlowerFeature(veg));
            // One placed feature PER biome: multiple 'biome' placements would be
            // AND-ed (never spawn); a single placed feature per biome ORs correctly.
            for (int j = 0; j < veg.biomes().size(); j++) {
                String placedId = baseFeatureId + "_" + j;
                datapackService.addData("luatweaker/worldgen/placed_feature/" + placedId + ".json",
                        buildPlacedFlowerFeature(configuredId, veg, veg.biomes().get(j)));
                emitted++;
            }
        }
        return emitted;
    }

    private int applyRemovals() {
        List<WorldgenServiceImpl.OreRemovalEntry> removals = worldgenService.getPendingRemovals();
        int applied = 0;
        for (int i = 0; i < removals.size(); i++) {
            WorldgenServiceImpl.OreRemovalEntry removal = removals.get(i);
            List<String> features = VANILLA_ORE_FEATURES.get(removal.blockId());
            if (features == null) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "[WorldgenProvider] Cannot remove '" + removal.blockId()
                        + "': no known vanilla ore features for this block (supported: " + VANILLA_ORE_FEATURES.keySet() + ")");
                continue;
            }
            if (!isSupportedDimension(removal.dimension())) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "[WorldgenProvider] Skipped removal of '" + removal.blockId() + "': unsupported dimension '"
                        + removal.dimension() + "'");
                continue;
            }
            datapackService.addData("luatweaker/neoforge/biome_modifier/remove_" + removal.blockId().replace(":", "_") + "_" + i + ".json",
                    buildRemoveFeaturesModifier(features, removal.dimension()));
            applied++;
        }
        return applied;
    }

    // ===== JSON builders (structures verified against vanilla 1.21.1 datapack) =====

    private String buildConfiguredOreFeature(WorldgenServiceImpl.OreEntry ore) {
        return "{"
            + "\"type\":\"minecraft:ore\","
            + "\"config\":{"
            +   "\"discard_chance_on_air_exposure\":0.0,"
            +   "\"size\":" + ore.clusterSize() + ","
            +   "\"targets\":[" + oreTargets(ore) + "]"
            + "}"
            + "}";
    }

    /** Replaceable targets per dimension, mirroring vanilla ore features. */
    private String oreTargets(WorldgenServiceImpl.OreEntry ore) {
        return switch (ore.dimension()) {
            case "minecraft:overworld" ->
                    "{"
                    + "{\"target\":{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:stone_ore_replaceables\"},"
                    + "\"state\":{\"Name\":\"" + escapeJson(ore.blockId()) + "\"}},"
                    + "{\"target\":{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:deepslate_ore_replaceables\"},"
                    + "\"state\":{\"Name\":\"" + escapeJson(ore.blockId()) + "\"}}";
            case "minecraft:the_nether" ->
                    "{\"target\":{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:netherrack\"},"
                    + "\"state\":{\"Name\":\"" + escapeJson(ore.blockId()) + "\"}}";
            default -> // minecraft:the_end
                    "{\"target\":{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:end_stone\"},"
                    + "\"state\":{\"Name\":\"" + escapeJson(ore.blockId()) + "\"}}";
        };
    }

    private String buildPlacedOreFeature(String featureId, WorldgenServiceImpl.OreEntry ore) {
        return "{"
            + "\"feature\":\"luatweaker:" + featureId + "\","
            + "\"placement\":["
            +   "{\"type\":\"minecraft:count\",\"count\":" + ore.frequency() + "},"
            +   "{\"type\":\"minecraft:in_square\"},"
            +   "{\"type\":\"minecraft:height_range\","
            +    "\"height\":{\"type\":\"minecraft:uniform\","
            +               "\"min_inclusive\":{\"absolute\":" + ore.minHeight() + "},"
            +               "\"max_inclusive\":{\"absolute\":" + ore.maxHeight() + "}"
            +    "}"
            +   "},"
            +   "{\"type\":\"minecraft:biome\"}"
            + "]"
            + "}";
    }

    /**
     * NeoForge biome modifier. {@code biomes} is either a biome-id list (filtered
     * ore) or the dimension's biome tag (unfiltered ore).
     */
    private String buildAddFeatureModifier(String featureId, String dimension,
                                           List<String> biomes, String step) {
        return "{"
            + "\"type\":\"neoforge:add_features\","
            + "\"biomes\":" + biomes(biomes, dimension) + ","
            + "\"features\":[\"luatweaker:" + featureId + "\"],"
            + "\"step\":\"" + step + "\""
            + "}";
    }

    private String buildRemoveFeaturesModifier(List<String> features, String dimension) {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < features.size(); i++) {
            if (i > 0) list.append(",");
            list.append("\"").append(escapeJson(features.get(i))).append("\"");
        }
        return "{"
            + "\"type\":\"neoforge:remove_features\","
            + "\"biomes\":" + biomes(List.of(), dimension) + ","
            + "\"features\":[" + list + "],"
            + "\"step\":\"underground_ores\""
            + "}";
    }

    /** Biome list, or the dimension's standard biome tag when no explicit list was given. */
    private String biomes(List<String> biomes, String dimension) {
        if (biomes != null && !biomes.isEmpty()) {
            StringBuilder list = new StringBuilder("[");
            for (int i = 0; i < biomes.size(); i++) {
                if (i > 0) list.append(",");
                list.append("\"").append(escapeJson(biomes.get(i))).append("\"");
            }
            return list.append("]").toString();
        }
        return "\"" + dimensionBiomeTag(dimension) + "\"";
    }

    private String dimensionBiomeTag(String dimension) {
        return switch (dimension) {
            case "minecraft:the_nether" -> "#minecraft:is_nether";
            case "minecraft:the_end" -> "#minecraft:is_end";
            default -> "#minecraft:is_overworld";
        };
    }

    private boolean isSupportedDimension(String dimension) {
        return "minecraft:overworld".equals(dimension)
                || "minecraft:the_nether".equals(dimension)
                || "minecraft:the_end".equals(dimension);
    }

    private String buildConfiguredFlowerFeature(WorldgenServiceImpl.VegetationEntry veg) {
        return "{"
            + "\"type\":\"minecraft:flower\","
            + "\"config\":{"
            +   "\"tries\":64,"
            +   "\"xz_spread\":7,"
            +   "\"y_spread\":3,"
            +   "\"feature\":{"
            +     "\"feature\":{"
            +       "\"type\":\"minecraft:simple_block\","
            +       "\"config\":{\"to_place\":{\"Name\":\"" + escapeJson(veg.blockId()) + "\"}}"
            +     "},"
            +     "\"placement\":[{\"type\":\"minecraft:block_predicate_filter\","
            +       "\"predicate\":{\"type\":\"minecraft:matching_blocks\",\"blocks\":\"minecraft:air\"}}]"
            +   "}"
            + "}"
            + "}";
    }

    private String buildPlacedFlowerFeature(String configuredFeatureId,
                                            WorldgenServiceImpl.VegetationEntry veg, String biomeId) {
        // chance -> rarity_filter: honor the Lua value exactly. chance <= 0 means
        // "never spawn" (a JSON-safe maximum rarity instead of dividing by zero).
        double chance = veg.chance();
        int rarity = chance <= 0 ? Integer.MAX_VALUE : Math.max(1, (int) (1.0 / chance));
        return "{"
            + "\"feature\":\"" + configuredFeatureId + "\","
            + "\"placement\":["
            +   "{\"type\":\"minecraft:rarity_filter\",\"chance\":" + rarity + "},"
            +   "{\"type\":\"minecraft:in_square\"},"
            +   "{\"type\":\"minecraft:heightmap\",\"heightmap\":\"MOTION_BLOCKING\"},"
            +   "{\"type\":\"minecraft:biome\",\"biome\":\"" + escapeJson(biomeId) + "\"}"
            + "]"
            + "}";
    }

    private static String escapeJson(@NotNull String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

package com.luatweaker.worldgen;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Configured/placed feature JSON builders. Every shape below mirrors a vanilla 1.21.1
 * resource (or the NeoForge codec for mixed veins) and is covered by the module tests.
 */
final class WorldgenJsonBuilder {

    private static final String COMMA = ",";
    private static final String QUOTE_COMMA = "\",";

    private WorldgenJsonBuilder() {}

    /**
     * Builds the configured-feature JSON for a vegetation entry. Flower shape verified
     * against {@code flower_plain.json}; patch shape against {@code patch_grass.json}.
     */
    static @NotNull String buildVegetationConfiguredJson(
            @NotNull WorldgenServiceImpl.VegetationEntry veg) {
        String type;
        int tries;
        int xzSpread;
        int ySpread;
        if (WorldgenDefaults.KIND_PATCH.equals(veg.kind())) {
            type = "minecraft:random_patch";
            tries = WorldgenDefaults.PATCH_TRIES;
            xzSpread = WorldgenDefaults.PATCH_XZ_SPREAD;
            ySpread = WorldgenDefaults.PATCH_Y_SPREAD;
        } else {
            type = "minecraft:flower";
            tries = WorldgenDefaults.FLOWER_TRIES;
            xzSpread = WorldgenDefaults.FLOWER_XZ_SPREAD;
            ySpread = WorldgenDefaults.FLOWER_Y_SPREAD;
        }
        return "{"
            + "\"type\":\"" + type + QUOTE_COMMA
            + "\"config\":{"
            +   "\"tries\":" + tries + COMMA
            +   "\"xz_spread\":" + xzSpread + COMMA
            +   "\"y_spread\":" + ySpread + COMMA
            +   "\"feature\":{"
            +     "\"feature\":{"
            +       "\"type\":\"minecraft:simple_block\","
            +       "\"config\":{\"to_place\":{"
            +         "\"type\":\"minecraft:simple_state_provider\","
            +         "\"state\":{\"Name\":\"" + WorldgenJson.escapeJson(veg.blockId()) + "\"}"
            +       "}}"
            +     "},"
            +     "\"placement\":[{\"type\":\"minecraft:block_predicate_filter\","
            +       "\"predicate\":{\"type\":\"minecraft:matching_blocks\",\"blocks\":\"minecraft:air\"}}]"
            +   "}"
            + "}"
            + "}";
    }

    static @NotNull String buildConfiguredOreFeature(
            @NotNull WorldgenServiceImpl.CustomFeature feature) {
        // scattered_ore (ancient-debris pattern) exposes veins instead of burying them;
        // discard_chance_on_air_exposure (e.g. vanilla diamond_large 0.7) thins veins that touch air.
        return "{"
            + "\"type\":\"minecraft:" + (feature.scattered() ? "scattered_ore" : "ore") + QUOTE_COMMA
            + "\"config\":{"
            +   "\"discard_chance_on_air_exposure\":" + feature.discardChance() + COMMA
            +   "\"size\":" + feature.size() + COMMA
            +   "\"targets\":[" + oreTargets(feature) + "]"
            + "}"
            + "}";
    }

    static @NotNull String buildPlacedOreFeature(@NotNull String safeId,
                                                 @NotNull WorldgenServiceImpl.CustomFeature feature) {
        return buildPlacedFeatureJson(WorldgenDefaults.NAMESPACE + safeId, new WorldgenPlacement(
                feature.step(), feature.count(), feature.rarity(), feature.size(),
                feature.minHeight(), feature.maxHeight(), feature.heightType(),
                feature.anchorMin(), feature.anchorMax(), feature.discardChance(), feature.inner()));
    }

    /**
     * Placed-feature JSON from shared placement fields. The {@code feature} reference is
     * the full id (vanilla ids pass through for raw-adjacent flows, {@code luatweaker:} ids
     * for generated ones).
     */
    static @NotNull String buildPlacedFeatureJson(@NotNull String featureRef,
                                                  @NotNull WorldgenPlacement placement) {
        // Frequency: count = N attempts per chunk (ore_coal_upper pattern) or rarity =
        // 1-in-N chunk chance (ore_diamond_large pattern); mutually exclusive, enforced at registration.
        String frequency = placement.rarity() != null
                ? "{\"type\":\"minecraft:rarity_filter\",\"chance\":" + placement.rarity() + "}"
                : "{\"type\":\"minecraft:count\",\"count\":" + placement.count() + "}";
        String height = "{\"type\":\"minecraft:" + placement.heightType() + QUOTE_COMMA
                + "\"min_inclusive\":{\"" + placement.anchorMin() + "\":" + placement.minHeight() + "},"
                + "\"max_inclusive\":{\"" + placement.anchorMax() + "\":" + placement.maxHeight() + "}"
                + (isBiased(placement.heightType()) ? ",\"inner\":" + placement.inner() : "")
                + "}";
        return "{"
            + "\"feature\":\"" + WorldgenJson.escapeJson(featureRef) + QUOTE_COMMA
            + "\"placement\":["
            +   frequency + COMMA
            +   "{\"type\":\"minecraft:in_square\"},"
            +   "{\"type\":\"minecraft:height_range\",\"height\":" + height + "},"
            +   "{\"type\":\"minecraft:biome\"}"
            + "]"
            + "}";
    }

    static @NotNull String buildMixedVeinConfiguredJson(
            @NotNull WorldgenServiceImpl.MixedVein vein) {
        StringBuilder targets = new StringBuilder();
        for (WorldgenServiceImpl.VeinOre ore : vein.ores()) {
            for (String rockTarget : rockTargets(ore.block(), vein.replace())) {
                if (targets.length() > 0) {
                    targets.append(',');
                }
                targets.append(rockTarget, 0, rockTarget.length() - 1)
                        .append(",\"weight\":")
                        .append(ore.weight())
                        .append('}');
            }
        }
        return "{"
            + "\"type\":\"luatweaker:mixed_vein\","
            + "\"config\":{"
            +   "\"size\":" + vein.size() + COMMA
            +   "\"discard_chance_on_air_exposure\":" + vein.discardChance() + COMMA
            +   "\"targets\":[" + targets + "]"
            + "}"
            + "}";
    }

    private static @NotNull String oreTargets(@NotNull WorldgenServiceImpl.CustomFeature feature) {
        return String.join(COMMA, rockTargets(feature.block(), feature.replace()));
    }

    /**
     * Replaceable rock predicates by kind, shared by ore features and mixed veins.
     * Stone/nether shapes verified against the client jar; end follows the provider convention.
     */
    private static @NotNull List<String> rockTargets(@NotNull String block, @NotNull String replace) {
        String state = "\"state\":{\"Name\":\"" + WorldgenJson.escapeJson(block) + "\"}";
        return switch (replace) {
            case "nether" -> List.of(
                    "{\"target\":{\"predicate_type\":\"minecraft:block_match\","
                    + "\"block\":\"minecraft:netherrack\"}," + state + "}");
            case "end" -> List.of(
                    "{\"target\":{\"predicate_type\":\"minecraft:block_match\","
                    + "\"block\":\"minecraft:end_stone\"}," + state + "}");
            default -> List.of(
                    "{\"target\":{\"predicate_type\":\"minecraft:tag_match\","
                    + "\"tag\":\"minecraft:stone_ore_replaceables\"}," + state + "}",
                    "{\"target\":{\"predicate_type\":\"minecraft:tag_match\","
                    + "\"tag\":\"minecraft:deepslate_ore_replaceables\"}," + state + "}");
        };
    }

    private static boolean isBiased(@NotNull String heightType) {
        return "biased_to_bottom".equals(heightType) || "very_biased_to_bottom".equals(heightType);
    }
}

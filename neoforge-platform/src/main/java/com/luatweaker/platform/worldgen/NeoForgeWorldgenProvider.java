package com.luatweaker.platform.worldgen;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class NeoForgeWorldgenProvider {

    private final WorldgenServiceImpl worldgenService;
    private final IDatapackService datapackService;

    public NeoForgeWorldgenProvider(@NotNull WorldgenServiceImpl worldgenService, @NotNull IDatapackService datapackService) {
        this.worldgenService = worldgenService;
        this.datapackService = datapackService;
    }

    public void applyAll() {
        applyOres();
        applyVegetation();

        Map<String, Object> summary = worldgenService.getModifications();
        LuaTweakerLog.get().info(LogStage.SYSTEM,
            "[WorldgenProvider] Applied: " + summary.get("ores") + " ores, "
            + summary.get("vegetation") + " vegetation entries");
    }

    private void applyOres() {
        for (WorldgenServiceImpl.OreEntry ore : worldgenService.getPendingOres()) {
            String featureId = ore.blockId().replace(":", "_") + "_ore";

            String configuredFeatureJson = buildConfiguredOreFeature(ore);
            String placedFeatureJson = buildPlacedOreFeature(featureId, ore);
            String biomeModifierJson = buildBiomeModifier(ore);

            datapackService.addData("luatweaker/worldgen/configured_feature/" + featureId + ".json",
                    configuredFeatureJson);
            datapackService.addData("luatweaker/worldgen/placed_feature/" + featureId + ".json",
                    placedFeatureJson);
            datapackService.addData("luatweaker/neoforge/biome_modifier/add_" + featureId + ".json",
                    biomeModifierJson);
        }
    }

    private void applyVegetation() {
        for (int i = 0; i < worldgenService.getPendingVegetation().size(); i++) {
            WorldgenServiceImpl.VegetationEntry veg = worldgenService.getPendingVegetation().get(i);
            String featureId = veg.blockId().replace(":", "_") + "_veg_" + i;

            String configuredFeatureJson = buildConfiguredFlowerFeature(veg);
            String placedFeatureJson = buildPlacedFlowerFeature(featureId, veg);

            datapackService.addData("luatweaker/worldgen/configured_feature/" + featureId + ".json",
                    configuredFeatureJson);
            datapackService.addData("luatweaker/worldgen/placed_feature/" + featureId + ".json",
                    placedFeatureJson);
        }
    }

    private String buildConfiguredOreFeature(WorldgenServiceImpl.OreEntry ore) {
        return "{"
            + "\"type\":\"minecraft:ore\","
            + "\"config\":{"
            +   "\"size\":" + ore.clusterSize() + ","
            +   "\"discard_on_air_exposure\":0.0,"
            +   "\"targets\":[{"
            +     "\"target\":{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:stone_ore_replaceables\"},"
            +     "\"state\":{\"Name\":\"" + ore.blockId() + "\"}"
            +   "}]"
            + "}"
            + "}";
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

    private String buildBiomeModifier(WorldgenServiceImpl.OreEntry ore) {
        String featureId = ore.blockId().replace(":", "_") + "_ore";
        StringBuilder json = new StringBuilder();
        json.append("{\"type\":\"neoforge:add_features\",\"biomes\":\"#minecraft:is_overworld\",")
            .append("\"features\":[\"luatweaker:").append(featureId).append("\"],")
            .append("\"step\":\"underground_ores\"}");
        return json.toString();
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
            +       "\"config\":{\"to_place\":{\"Name\":\"" + veg.blockId() + "\"}}"
            +     "},"
            +     "\"placement\":[{\"type\":\"minecraft:block_predicate_filter\","
            +       "\"predicate\":{\"type\":\"minecraft:matching_blocks\",\"blocks\":\"minecraft:air\"}}]"
            +   "}"
            + "}"
            + "}";
    }

    private String buildPlacedFlowerFeature(String featureId, WorldgenServiceImpl.VegetationEntry veg) {
        int rarity = Math.max(1, (int)(1.0 / Math.max(0.01, veg.chance())));
        return "{"
            + "\"feature\":\"luatweaker:" + featureId + "\","
            + "\"placement\":["
            +   "{\"type\":\"minecraft:rarity_filter\",\"chance\":" + rarity + "},"
            +   "{\"type\":\"minecraft:in_square\"},"
            +   "{\"type\":\"minecraft:heightmap\",\"heightmap\":\"MOTION_BLOCKING\"},"
            +   "{\"type\":\"minecraft:biome\"}"
            + "]"
            + "}";
    }
}

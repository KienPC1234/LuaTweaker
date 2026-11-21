package com.luatweaker.worldgen;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * NeoForge biome-modifier JSON. Field names verified against the 21.1.242 codec:
 * add-features uses singular {@code step}; remove-features uses plural {@code steps}.
 */
final class WorldgenModifierBuilder {

    private WorldgenModifierBuilder() {}

    static @NotNull String buildAddFeaturesModifier(@NotNull List<String> biomes,
                                                    @NotNull List<String> features,
                                                    @NotNull String step) {
        StringBuilder list = new StringBuilder("[");
        WorldgenJson.appendStringList(list, features);
        list.append(']');
        return "{"
            + "\"type\":\"neoforge:add_features\","
            + "\"biomes\":" + WorldgenJson.biomeSelector(biomes) + ","
            + "\"features\":" + list + ","
            + "\"step\":\"" + WorldgenJson.escapeJson(step) + "\""
            + "}";
    }

    static @NotNull String buildRemoveFeaturesModifier(@NotNull List<String> biomes,
                                                        @NotNull List<String> features,
                                                        @NotNull List<String> steps) {
        StringBuilder featureList = new StringBuilder("[");
        WorldgenJson.appendStringList(featureList, features);
        featureList.append(']');
        StringBuilder stepList = new StringBuilder("[");
        WorldgenJson.appendStringList(stepList, steps);
        stepList.append(']');
        return "{"
            + "\"type\":\"neoforge:remove_features\","
            + "\"biomes\":" + WorldgenJson.biomeSelector(biomes) + ","
            + "\"features\":" + featureList + ","
            + "\"steps\":" + stepList
            + "}";
    }
}

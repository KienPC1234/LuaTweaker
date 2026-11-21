package com.luatweaker.dimension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Shared biome-JSON support: key constants, vanilla defaults, id validation,
 * datapack path building, and the Define starter template. Keeps the service
 * and the section parsers on identical shapes without duplication.
 */
final class BiomeJson {

    /** Minecraft resource-location charset. */
    static final Pattern RESOURCE_LOCATION =
            Pattern.compile("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");

    // Vanilla plains defaults (1.21.1).
    static final int DEFAULT_SKY_COLOR = 7_907_327;
    static final int DEFAULT_FOG_COLOR = 12_638_463;
    static final int DEFAULT_WATER_COLOR = 4_159_204;
    static final int DEFAULT_WATER_FOG_COLOR = 329_011;

    // Biome JSON keys.
    static final String KEY_TYPE = "type";
    static final String KEY_WEIGHT = "weight";
    static final String KEY_MIN_COUNT = "minCount";
    static final String KEY_MAX_COUNT = "maxCount";
    static final String KEY_SPAWNERS = "spawners";
    static final String KEY_SPAWN_COSTS = "spawn_costs";
    static final String KEY_CARVERS = "carvers";
    static final String KEY_FEATURES = "features";
    static final String KEY_EFFECTS = "effects";
    static final String KEY_TEMPERATURE = "temperature";
    static final String KEY_DOWNFALL = "downfall";
    static final String KEY_HAS_PRECIPITATION = "has_precipitation";

    static final String PARAM_BIOME_ID = "biomeId";
    static final String PARAM_ENTITY = "entity";

    private static final String[] VALID_CATEGORIES = {
            "monster", "creature", "ambient", "water_creature", "water_ambient",
            "water_underground_creature", "misc"
    };

    private BiomeJson() {
    }

    static void validateId(@NotNull String name, String id) {
        if (id == null || !RESOURCE_LOCATION.matcher(id).matches()) {
            throw new IllegalArgumentException(name + " must be a valid resource location (e.g. 'mymod:id'), got: " + id);
        }
    }

    static void validateCategory(@NotNull String category) {
        for (String valid : VALID_CATEGORIES) {
            if (valid.equals(category)) {
                return;
            }
        }
        throw new IllegalArgumentException("invalid spawn category '" + category
                + "'; valid: monster, creature, ambient, water_creature, water_ambient, water_underground_creature, misc");
    }

    static String biomePath(@NotNull String biomeId) {
        int colon = biomeId.indexOf(':');
        String ns = colon > 0 ? biomeId.substring(0, colon) : "luatweaker";
        String path = colon > 0 ? biomeId.substring(colon + 1) : biomeId;
        // Biomes are a WORLDGEN registry: datapack path is data/<ns>/worldgen/biome/<name>.json.
        return "data/" + ns + "/worldgen/biome/" + path + ".json";
    }

    /** Starter template for Define: vanilla plains colors, empty spawners/carvers, one empty list per generation step. */
    static JsonObject baseBiomeJson() {
        JsonObject json = new JsonObject();
        json.addProperty(KEY_HAS_PRECIPITATION, true);
        json.addProperty(KEY_TEMPERATURE, 0.6);
        json.addProperty(KEY_DOWNFALL, 0.4);
        JsonObject effects = new JsonObject();
        effects.addProperty("sky_color", DEFAULT_SKY_COLOR);
        effects.addProperty("fog_color", DEFAULT_FOG_COLOR);
        effects.addProperty("water_color", DEFAULT_WATER_COLOR);
        effects.addProperty("water_fog_color", DEFAULT_WATER_FOG_COLOR);
        json.add(KEY_EFFECTS, effects);
        json.add(KEY_SPAWNERS, new JsonObject());
        json.add(KEY_SPAWN_COSTS, new JsonObject());
        json.add(KEY_CARVERS, new JsonObject());
        JsonArray features = new JsonArray();
        com.luatweaker.api.worldgen.GenerationSteps.ORDER.forEach(step -> features.add(new JsonArray()));
        json.add(KEY_FEATURES, features);
        return json;
    }
}

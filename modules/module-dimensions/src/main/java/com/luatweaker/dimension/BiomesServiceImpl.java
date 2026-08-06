package com.luatweaker.dimension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.luatweaker.api.biome.IBiomesService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Merges entity spawn entries into biome datapack JSONs (the virtual datapack
 * files the dimension provider materializes), so entities spawn inside
 * specific biomes via the vanilla spawner.
 */
public class BiomesServiceImpl implements IBiomesService {

    /** Minecraft resource-location charset. */
    private static final java.util.regex.Pattern RESOURCE_LOCATION =
            java.util.regex.Pattern.compile("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");

    private static final String[] VALID_CATEGORIES = {
            "monster", "creature", "ambient", "water_creature", "water_ambient",
            "water_underground_creature", "misc"
    };

    private final ILuaEngine engine;

    public BiomesServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public void addSpawn(@NotNull String biomeId, @NotNull String category, @NotNull String entity,
                         int weight, int minCount, int maxCount) {
        validateId("biomeId", biomeId);
        validateId("entity", entity);
        validateCategory(category);
        if (weight < 1) throw new IllegalArgumentException("weight must be >= 1, got: " + weight);
        if (minCount < 1 || maxCount < minCount) {
            throw new IllegalArgumentException("invalid count range: minCount=" + minCount + " maxCount=" + maxCount);
        }

        String path = biomePath(biomeId);
        JsonObject json = readBiomeJson(path);
        JsonObject spawners = json.getAsJsonObject("spawners");
        if (spawners == null) {
            spawners = new JsonObject();
            json.add("spawners", spawners);
        }
        JsonArray entries = spawners.getAsJsonArray(category);
        if (entries == null) {
            entries = new JsonArray();
            spawners.add(category, entries);
        }
        for (var entry : entries) {
            JsonObject obj = entry.getAsJsonObject();
            if (entity.equals(obj.get("type").getAsString())) {
                obj.addProperty("weight", weight);
                obj.addProperty("minCount", minCount);
                obj.addProperty("maxCount", maxCount);
                writeBiomeJson(path, json);
                return;
            }
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("type", entity);
        entry.addProperty("weight", weight);
        entry.addProperty("minCount", minCount);
        entry.addProperty("maxCount", maxCount);
        entries.add(entry);
        writeBiomeJson(path, json);
    }

    @Override
    public void removeSpawn(@NotNull String biomeId, @NotNull String category, @NotNull String entity) {
        validateId("biomeId", biomeId);
        validateId("entity", entity);
        validateCategory(category);

        String path = biomePath(biomeId);
        JsonObject json = readBiomeJson(path);
        JsonObject spawners = json.getAsJsonObject("spawners");
        if (spawners == null) return;
        JsonArray entries = spawners.getAsJsonArray(category);
        if (entries == null) return;
        for (int i = entries.size() - 1; i >= 0; i--) {
            JsonObject obj = entries.get(i).getAsJsonObject();
            if (entity.equals(obj.get("type").getAsString())) {
                entries.remove(i);
            }
        }
        writeBiomeJson(path, json);
    }

    @Override
    @NotNull
    public Object getSpawns(@NotNull String biomeId) {
        validateId("biomeId", biomeId);
        ILuaTable result = engine.createTable();
        IDatapackService datapack = currentDatapack();
        if (datapack == null) return result;
        String existing = datapack.getVirtualFiles().get(biomePath(biomeId));
        if (existing == null) return result;
        try {
            JsonObject json = com.google.gson.JsonParser.parseString(existing).getAsJsonObject();
            JsonObject spawners = json.getAsJsonObject("spawners");
            if (spawners == null) return result;
            for (Map.Entry<String, com.google.gson.JsonElement> cat : spawners.entrySet()) {
                ILuaTable catTable = engine.createTable();
                JsonArray entries = cat.getValue().getAsJsonArray();
                for (int i = 0; i < entries.size(); i++) {
                    JsonObject entry = entries.get(i).getAsJsonObject();
                    ILuaTable entryTable = engine.createTable();
                    if (entry.has("type")) entryTable.rawset("type", entry.get("type").getAsString());
                    if (entry.has("weight")) entryTable.rawset("weight", entry.get("weight").getAsInt());
                    if (entry.has("minCount")) entryTable.rawset("minCount", entry.get("minCount").getAsInt());
                    if (entry.has("maxCount")) entryTable.rawset("maxCount", entry.get("maxCount").getAsInt());
                    catTable.rawset(i + 1, entryTable);
                }
                result.rawset(cat.getKey(), catTable);
            }
        } catch (Exception e) {
            // Malformed biome JSON: return an empty view rather than crashing.
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Datapack JSON merge
    // -------------------------------------------------------------------------

    private JsonObject readBiomeJson(String path) {
        IDatapackService datapack = currentDatapack();
        if (datapack != null) {
            String existing = datapack.getVirtualFiles().get(path);
            if (existing != null) {
                try {
                    return com.google.gson.JsonParser.parseString(existing).getAsJsonObject();
                } catch (Exception e) {
                    // Fall through to a fresh minimal biome JSON.
                }
            }
        }
        JsonObject json = new JsonObject();
        json.addProperty("has_precipitation", true);
        json.addProperty("temperature", 0.6);
        json.addProperty("downfall", 0.4);
        JsonObject effects = new JsonObject();
        effects.addProperty("sky_color", 7907327);
        effects.addProperty("fog_color", 12638463);
        effects.addProperty("water_color", 4159204);
        effects.addProperty("water_fog_color", 329011);
        json.add("effects", effects);
        json.add("spawners", new JsonObject());
        json.add("spawn_costs", new JsonObject());
        json.add("carvers", new JsonObject());
        json.add("features", new JsonArray());
        return json;
    }

    private void writeBiomeJson(String path, JsonObject json) {
        IDatapackService datapack = currentDatapack();
        if (datapack == null) {
            throw new IllegalStateException("Datapack service is not registered; cannot customize biome '" + path + "'");
        }
        datapack.addData(path, json.toString());
    }

    @Nullable
    private IDatapackService currentDatapack() {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("Datapack");
        return service instanceof IDatapackService datapack ? datapack : null;
    }

    private static String biomePath(String biomeId) {
        int colon = biomeId.indexOf(':');
        String ns = colon > 0 ? biomeId.substring(0, colon) : "luatweaker";
        String path = colon > 0 ? biomeId.substring(colon + 1) : biomeId;
        // Biomes are a WORLDGEN registry: datapack path is data/<ns>/worldgen/biome/<name>.json.
        return "data/" + ns + "/worldgen/biome/" + path + ".json";
    }

    private static void validateId(String name, String id) {
        if (id == null || !RESOURCE_LOCATION.matcher(id).matches()) {
            throw new IllegalArgumentException(name + " must be a valid resource location (e.g. 'mymod:id'), got: " + id);
        }
    }

    private static void validateCategory(String category) {
        for (String valid : VALID_CATEGORIES) {
            if (valid.equals(category)) return;
        }
        throw new IllegalArgumentException("invalid spawn category '" + category
                + "'; valid: monster, creature, ambient, water_creature, water_ambient, water_underground_creature, misc");
    }
}

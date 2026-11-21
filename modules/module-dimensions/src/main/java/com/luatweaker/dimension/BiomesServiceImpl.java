package com.luatweaker.dimension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.luatweaker.api.biome.IBiomesService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Merges entity spawn entries into biome datapack JSONs (the virtual datapack
 * files the dimension provider materializes), so entities spawn inside
 * specific biomes via the vanilla spawner.
 */
public class BiomesServiceImpl implements IBiomesService {

    private static final int MIN_WEIGHT = 1;
    private static final int MIN_COUNT = 1;

    private final ILuaEngine engine;

    public BiomesServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public void addSpawn(@NotNull String biomeId, @NotNull String category, @NotNull String entity,
            int weight, int minCount, int maxCount) {
        BiomeJson.validateId(BiomeJson.PARAM_BIOME_ID, biomeId);
        BiomeJson.validateId(BiomeJson.PARAM_ENTITY, entity);
        BiomeJson.validateCategory(category);
        checkSpawnRange(weight, minCount, maxCount);

        String path = BiomeJson.biomePath(biomeId);
        JsonObject json = requireBiomeJson(path, biomeId);
        JsonArray entries = spawnEntries(json, category);
        JsonObject entry = findSpawnEntry(entries, entity);
        if (entry == null) {
            entry = new JsonObject();
            entry.addProperty(BiomeJson.KEY_TYPE, entity);
            entries.add(entry);
        }
        entry.addProperty(BiomeJson.KEY_WEIGHT, weight);
        entry.addProperty(BiomeJson.KEY_MIN_COUNT, minCount);
        entry.addProperty(BiomeJson.KEY_MAX_COUNT, maxCount);
        writeBiomeJson(path, json);
    }

    private static void checkSpawnRange(int weight, int minCount, int maxCount) {
        if (weight < MIN_WEIGHT) {
            throw new IllegalArgumentException("weight must be >= 1, got: " + weight);
        }
        if (minCount < MIN_COUNT || maxCount < minCount) {
            throw new IllegalArgumentException("invalid count range: minCount=" + minCount + " maxCount=" + maxCount);
        }
    }

    private static JsonArray spawnEntries(@NotNull JsonObject json, @NotNull String category) {
        JsonObject spawners = json.getAsJsonObject(BiomeJson.KEY_SPAWNERS);
        if (spawners == null) {
            spawners = new JsonObject();
            json.add(BiomeJson.KEY_SPAWNERS, spawners);
        }
        JsonArray entries = spawners.getAsJsonArray(category);
        if (entries == null) {
            entries = new JsonArray();
            spawners.add(category, entries);
        }
        return entries;
    }

    @Nullable
    private static JsonObject findSpawnEntry(@NotNull JsonArray entries, @NotNull String entity) {
        for (var item : entries) {
            JsonObject obj = item.getAsJsonObject();
            if (entity.equals(obj.get(BiomeJson.KEY_TYPE).getAsString())) {
                return obj;
            }
        }
        return null;
    }

    @Override
    public void removeSpawn(@NotNull String biomeId, @NotNull String category, @NotNull String entity) {
        BiomeJson.validateId(BiomeJson.PARAM_BIOME_ID, biomeId);
        BiomeJson.validateId(BiomeJson.PARAM_ENTITY, entity);
        BiomeJson.validateCategory(category);

        String path = BiomeJson.biomePath(biomeId);
        JsonObject json = requireBiomeJson(path, biomeId);
        JsonObject spawners = json.getAsJsonObject(BiomeJson.KEY_SPAWNERS);
        if (spawners == null) {
            return;
        }
        JsonArray entries = spawners.getAsJsonArray(category);
        if (entries == null) {
            return;
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            JsonObject obj = entries.get(i).getAsJsonObject();
            if (entity.equals(obj.get(BiomeJson.KEY_TYPE).getAsString())) {
                entries.remove(i);
            }
        }
        writeBiomeJson(path, json);
    }

    @Override
    public void define(@NotNull String biomeId, @NotNull ILuaValue config) {
        BiomeJson.validateId(BiomeJson.PARAM_BIOME_ID, biomeId);
        if (config == null || !config.isTable()) {
            throw new IllegalArgumentException("config must be a table for biome '" + biomeId + "'");
        }
        ILuaTable t = config.asTable();
        JsonObject json = BiomeJson.baseBiomeJson();
        json.addProperty(BiomeJson.KEY_HAS_PRECIPITATION,
                LuaTableReader.bool(t, BiomeJson.KEY_HAS_PRECIPITATION, true));
        json.addProperty(BiomeJson.KEY_TEMPERATURE,
                LuaTableReader.finite(t, BiomeJson.KEY_TEMPERATURE, 0.6, BiomeJson.KEY_TEMPERATURE));
        json.addProperty(BiomeJson.KEY_DOWNFALL,
                LuaTableReader.range(t, BiomeJson.KEY_DOWNFALL, 0.4, 0.0, 1.0, BiomeJson.KEY_DOWNFALL));
        json.add(BiomeJson.KEY_EFFECTS, BiomeEffectsParser.parseEffects(t.rawget(BiomeJson.KEY_EFFECTS)));
        JsonObject spawners = BiomeSpawnParser.parseSpawners(t.rawget(BiomeJson.KEY_SPAWNERS));
        if (spawners != null) {
            json.add(BiomeJson.KEY_SPAWNERS, spawners);
        }
        JsonObject costs = BiomeSpawnParser.parseSpawnCosts(t.rawget(BiomeJson.KEY_SPAWN_COSTS));
        if (costs != null) {
            json.add(BiomeJson.KEY_SPAWN_COSTS, costs);
        }
        JsonObject carvers = BiomeWorldgenParser.parseCarvers(t.rawget(BiomeJson.KEY_CARVERS));
        if (carvers != null) {
            json.add(BiomeJson.KEY_CARVERS, carvers);
        }
        JsonArray features = BiomeWorldgenParser.parseFeatures(t.rawget(BiomeJson.KEY_FEATURES));
        if (features != null) {
            json.add(BiomeJson.KEY_FEATURES, features);
        }
        writeBiomeJson(BiomeJson.biomePath(biomeId), json);
        com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM,
                "[Biomes] Defined biome '" + biomeId + "'.");
    }

    @Override
    @NotNull
    public Object getSpawns(@NotNull String biomeId) {
        BiomeJson.validateId(BiomeJson.PARAM_BIOME_ID, biomeId);
        ILuaTable result = engine.createTable();
        IDatapackService datapack = currentDatapack();
        if (datapack == null) {
            return result;
        }
        String existing = datapack.getVirtualFiles().get(BiomeJson.biomePath(biomeId));
        if (existing == null) {
            return result;
        }
        try {
            BiomeSpawnParser.copySpawnsView(engine,
                    com.google.gson.JsonParser.parseString(existing).getAsJsonObject(), result);
        } catch (JsonSyntaxException | IllegalStateException e) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                    "[Biomes] Ignoring malformed biome JSON for '" + biomeId + "': " + e.getMessage());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Datapack JSON merge
    // -------------------------------------------------------------------------

    /**
     * Reads the biome JSON already present in the virtual datapack. Never
     * auto-creates: writing a minimal file for an unknown id would silently
     * shadow a vanilla (or other-mod) biome with an empty definition.
     * Custom biomes must be created with {@link #define} first.
     */
    private JsonObject requireBiomeJson(String path, String biomeId) {
        IDatapackService datapack = currentDatapack();
        if (datapack != null) {
            String existing = datapack.getVirtualFiles().get(path);
            if (existing != null) {
                try {
                    return com.google.gson.JsonParser.parseString(existing).getAsJsonObject();
                } catch (JsonSyntaxException | IllegalStateException e) {
                    throw new IllegalArgumentException(
                            "Biome '" + biomeId + "' has malformed JSON in the virtual datapack: " + e.getMessage(),
                            e);
                }
            }
        }
        throw new IllegalArgumentException(
                "Unknown biome '" + biomeId + "': define it first with Biomes:Define(\"" + biomeId + "\", {...}) "
                + "instead of letting a minimal file shadow a vanilla biome.");
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
        if (service instanceof IDatapackService datapack) {
            return datapack;
        }
        return null;
    }
}

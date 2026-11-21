package com.luatweaker.dimension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Copies stored biome spawn JSON back into Lua tables for spawn queries.
 */
final class BiomeSpawnView {

    private final ILuaEngine engine;

    BiomeSpawnView(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    void copySpawnsView(@NotNull JsonObject json, @NotNull ILuaTable result) {
        JsonObject spawners = json.getAsJsonObject(BiomeJson.KEY_SPAWNERS);
        if (spawners == null) {
            return;
        }
        for (Map.Entry<String, com.google.gson.JsonElement> cat : spawners.entrySet()) {
            result.rawset(cat.getKey(), copySpawnCategory(cat.getValue().getAsJsonArray()));
        }
    }

    private ILuaTable copySpawnCategory(@NotNull JsonArray entries) {
        ILuaTable catTable = engine.createTable();
        for (int i = 0; i < entries.size(); i++) {
            JsonObject entry = entries.get(i).getAsJsonObject();
            ILuaTable entryTable = engine.createTable();
            if (entry.has(BiomeJson.KEY_TYPE)) {
                entryTable.rawset(BiomeJson.KEY_TYPE, entry.get(BiomeJson.KEY_TYPE).getAsString());
            }
            if (entry.has(BiomeJson.KEY_WEIGHT)) {
                entryTable.rawset(BiomeJson.KEY_WEIGHT, entry.get(BiomeJson.KEY_WEIGHT).getAsInt());
            }
            if (entry.has(BiomeJson.KEY_MIN_COUNT)) {
                entryTable.rawset(BiomeJson.KEY_MIN_COUNT, entry.get(BiomeJson.KEY_MIN_COUNT).getAsInt());
            }
            if (entry.has(BiomeJson.KEY_MAX_COUNT)) {
                entryTable.rawset(BiomeJson.KEY_MAX_COUNT, entry.get(BiomeJson.KEY_MAX_COUNT).getAsInt());
            }
            catTable.rawset(i + 1, entryTable);
        }
        return catTable;
    }
}

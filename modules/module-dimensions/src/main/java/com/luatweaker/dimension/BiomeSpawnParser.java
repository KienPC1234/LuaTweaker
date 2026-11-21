package com.luatweaker.dimension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Parses the spawn-related sections of a biome definition
 * ({@code spawners}, {@code spawn_costs}) and copies stored spawn
 * views back into Lua tables for queries.
 */
final class BiomeSpawnParser {

    private static final double MIN_CHARGE = 0.0;
    private static final int MIN_COUNT = 1;
    private static final int MIN_WEIGHT = 1;

    private BiomeSpawnParser() {
    }

    @Nullable
    static JsonObject parseSpawners(@Nullable ILuaValue value) {
        if (value == null || value.isNil()) {
            return null;
        }
        ILuaTable t = LuaTableReader.requireTable(value, BiomeJson.KEY_SPAWNERS);
        JsonObject spawners = new JsonObject();
        for (Map.Entry<ILuaValue, ILuaValue> entry : t.asMap().entrySet()) {
            String category = spawnCategory(entry.getKey());
            spawners.add(category, parseSpawnList(entry.getValue(), category));
        }
        return spawners;
    }

    private static String spawnCategory(@Nullable ILuaValue key) {
        if (key == null || !key.isString()) {
            throw new IllegalArgumentException("spawners keys must be category strings");
        }
        BiomeJson.validateCategory(key.asString());
        return key.asString();
    }

    private static JsonArray parseSpawnList(@Nullable ILuaValue listVal, @NotNull String category) {
        if (listVal == null || !listVal.isTable()) {
            throw new IllegalArgumentException("spawners['" + category + "'] must be a list of entries");
        }
        JsonArray entries = new JsonArray();
        ILuaTable list = listVal.asTable();
        for (int i = 1; i <= list.length(); i++) {
            ILuaValue item = list.rawget(i);
            if (item == null || !item.isTable()) {
                throw new IllegalArgumentException("spawners['" + category + "'][" + i + "] must be a table");
            }
            entries.add(parseSpawnEntry(item.asTable(), "spawners['" + category + "'][" + i + "]"));
        }
        return entries;
    }

    static JsonObject parseSpawnEntry(@NotNull ILuaTable t, @NotNull String what) {
        String type = LuaTableReader.reqString(t, BiomeJson.KEY_TYPE, what + ".type");
        BiomeJson.validateId(BiomeJson.PARAM_ENTITY, type);
        JsonObject entry = new JsonObject();
        entry.addProperty(BiomeJson.KEY_TYPE, type);
        entry.addProperty(BiomeJson.KEY_WEIGHT, (int) LuaTableReader.reqNumber(t, BiomeJson.KEY_WEIGHT, what + ".weight"));
        entry.addProperty(BiomeJson.KEY_MIN_COUNT, (int) LuaTableReader.reqNumber(t, BiomeJson.KEY_MIN_COUNT, what + ".minCount"));
        entry.addProperty(BiomeJson.KEY_MAX_COUNT, (int) LuaTableReader.reqNumber(t, BiomeJson.KEY_MAX_COUNT, what + ".maxCount"));
        if (entry.get(BiomeJson.KEY_MIN_COUNT).getAsInt() < MIN_COUNT
                || entry.get(BiomeJson.KEY_MAX_COUNT).getAsInt() < entry.get(BiomeJson.KEY_MIN_COUNT).getAsInt()) {
            throw new IllegalArgumentException(what + " needs 1 <= minCount <= maxCount");
        }
        if (entry.get(BiomeJson.KEY_WEIGHT).getAsInt() < MIN_WEIGHT) {
            throw new IllegalArgumentException(what + ".weight must be >= 1");
        }
        return entry;
    }

    @Nullable
    static JsonObject parseSpawnCosts(@Nullable ILuaValue value) {
        if (value == null || value.isNil()) {
            return null;
        }
        ILuaTable t = LuaTableReader.requireTable(value, BiomeJson.KEY_SPAWN_COSTS);
        JsonObject costs = new JsonObject();
        for (Map.Entry<ILuaValue, ILuaValue> entry : t.asMap().entrySet()) {
            String id = spawnCostId(entry.getKey());
            costs.add(id, parseCostEntry(entry.getValue(), id));
        }
        return costs;
    }

    private static String spawnCostId(@Nullable ILuaValue key) {
        if (key == null || !key.isString()) {
            throw new IllegalArgumentException("spawn_costs keys must be entity ids");
        }
        BiomeJson.validateId(BiomeJson.PARAM_ENTITY, key.asString());
        return key.asString();
    }

    private static JsonObject parseCostEntry(@Nullable ILuaValue costVal, @NotNull String id) {
        String what = "spawn_costs['" + id + "']";
        if (costVal == null || !costVal.isTable()) {
            throw new IllegalArgumentException(what + " must be a table {charge, energy_budget}");
        }
        ILuaTable cost = costVal.asTable();
        double charge = LuaTableReader.reqNumber(cost, "charge", what + ".charge");
        double budget = LuaTableReader.reqNumber(cost, "energy_budget", what + ".energy_budget");
        if (!Double.isFinite(charge) || charge < MIN_CHARGE
                || !Double.isFinite(budget) || budget < MIN_CHARGE) {
            throw new IllegalArgumentException(what + " values must be finite and >= 0");
        }
        JsonObject costJson = new JsonObject();
        costJson.addProperty("charge", charge);
        costJson.addProperty("energy_budget", budget);
        return costJson;
    }

    static void copySpawnsView(@NotNull ILuaEngine engine, @NotNull JsonObject json, @NotNull ILuaTable result) {
        new BiomeSpawnView(engine).copySpawnsView(json, result);
    }
}

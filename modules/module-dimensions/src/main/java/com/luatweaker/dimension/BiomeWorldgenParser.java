package com.luatweaker.dimension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Parses the worldgen sections of a biome definition
 * ({@code carvers}, {@code features}).
 */
final class BiomeWorldgenParser {

    private BiomeWorldgenParser() {
    }

    @Nullable
    static JsonObject parseCarvers(@Nullable ILuaValue value) {
        if (value == null || value.isNil()) {
            return null;
        }
        ILuaTable t = LuaTableReader.requireTable(value, BiomeJson.KEY_CARVERS);
        JsonObject carvers = new JsonObject();
        for (Map.Entry<ILuaValue, ILuaValue> entry : t.asMap().entrySet()) {
            String kind = carverKind(entry.getKey());
            carvers.add(kind, featureIdList(entry.getValue(), "carvers." + kind));
        }
        return carvers;
    }

    private static String carverKind(@Nullable ILuaValue key) {
        if (key == null || !key.isString()
                || (!"air".equals(key.asString()) && !"liquid".equals(key.asString()))) {
            throw new IllegalArgumentException("carvers keys must be 'air' or 'liquid'");
        }
        return key.asString();
    }

    @Nullable
    static JsonArray parseFeatures(@Nullable ILuaValue value) {
        if (value == null || value.isNil()) {
            return null;
        }
        ILuaTable t = LuaTableReader.requireTable(value, BiomeJson.KEY_FEATURES);
        Map<String, JsonArray> byStep = new java.util.concurrent.ConcurrentHashMap<>();
        for (Map.Entry<ILuaValue, ILuaValue> entry : t.asMap().entrySet()) {
            String step = featureStep(entry.getKey());
            byStep.put(step, featureIdList(entry.getValue(), "features." + step));
        }
        JsonArray features = new JsonArray();
        for (String step : com.luatweaker.api.worldgen.GenerationSteps.ORDER) {
            features.add(orEmptyList(byStep.get(step)));
        }
        return features;
    }

    private static String featureStep(@Nullable ILuaValue key) {
        if (key == null || !key.isString()
                || !com.luatweaker.api.worldgen.GenerationSteps.isKnownStep(key.asString())) {
            throw new IllegalArgumentException("features keys must be known generation steps "
                    + com.luatweaker.api.worldgen.GenerationSteps.ORDER + ", got: "
                    + (key == null ? "nil" : key.asString()));
        }
        return key.asString();
    }

    private static JsonArray orEmptyList(@Nullable JsonArray slot) {
        if (slot != null) {
            return slot;
        }
        return new JsonArray();
    }

    static JsonArray featureIdList(@Nullable ILuaValue value, @NotNull String what) {
        if (value == null || !value.isTable()) {
            throw new IllegalArgumentException(what + " must be a list of resource-location strings");
        }
        ILuaTable table = value.asTable();
        JsonArray out = new JsonArray();
        for (int i = 1; i <= table.length(); i++) {
            out.add(validatedFeatureId(table.rawget(i), what, i));
        }
        return out;
    }

    private static String validatedFeatureId(@Nullable ILuaValue item, @NotNull String what, int index) {
        if (item == null || !item.isString()) {
            throw new IllegalArgumentException(what + "[" + index + "] must be a resource-location string");
        }
        String id = item.asString();
        String target = id.startsWith("#") ? id.substring(1) : id;
        if (!BiomeJson.RESOURCE_LOCATION.matcher(target).matches()) {
            if (id.startsWith("#")) {
                throw new IllegalArgumentException(what + "[" + index + "] has an invalid tag target: " + id);
            }
            throw new IllegalArgumentException(what + "[" + index + "] must be a valid resource location, got: " + id);
        }
        return id;
    }
}

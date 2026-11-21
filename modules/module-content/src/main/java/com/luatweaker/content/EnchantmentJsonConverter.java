package com.luatweaker.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.util.Map;

/**
 * Handles converting Lua tables to JSON effects for data-driven enchantments.
 */
final class EnchantmentJsonConverter {

    private static final double ZERO_PER_LEVEL = 0.0;

    private EnchantmentJsonConverter() {}

    static JsonObject convertTableToJsonObject(ILuaTable table) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<ILuaValue, ILuaValue> entry : table.asMap().entrySet()) {
            obj.add(entry.getKey().asString(), convertLuaValueToJsonElement(entry.getValue()));
        }
        return obj;
    }

    private static JsonElement convertLuaValueToJsonElement(ILuaValue val) {
        if (val == null || val.isNil()) {
            return JsonNull.INSTANCE;
        }
        if (val.isBoolean()) {
            return new JsonPrimitive(val.asBoolean());
        }
        if (val.isNumber()) {
            return convertNumber(val.asDouble());
        }
        if (val.isTable()) {
            return convertTable(val.asTable());
        }
        return new JsonPrimitive(val.asString());
    }

    private static JsonPrimitive convertNumber(double num) {
        return num == (long) num ? new JsonPrimitive((long) num) : new JsonPrimitive(num);
    }

    private static JsonElement convertTable(ILuaTable tbl) {
        int len = tbl.length();
        if (len > 0 && tbl.asMap().size() == len) {
            JsonArray arr = new JsonArray();
            for (int i = 1; i <= len; i++) {
                arr.add(convertLuaValueToJsonElement(tbl.rawget(i)));
            }
            return arr;
        }
        return convertTableToJsonObject(tbl);
    }

    static void addValueEffect(JsonObject effects, String componentId, double base, double perLevel) {
        JsonArray list = effects.has(componentId) && effects.get(componentId).isJsonArray()
                ? effects.getAsJsonArray(componentId) : new JsonArray();
        JsonObject entry = new JsonObject();
        JsonObject effect = new JsonObject();
        effect.addProperty("type", "minecraft:add");
        if (Double.compare(perLevel, ZERO_PER_LEVEL) == 0) {
            effect.addProperty("value", (float) base);
        } else {
            JsonObject linear = new JsonObject();
            linear.addProperty("type", "minecraft:linear");
            linear.addProperty("base", (float) base);
            linear.addProperty("per_level_above_first", (float) perLevel);
            effect.add("value", linear);
        }
        entry.add("effect", effect);
        list.add(entry);
        effects.add(componentId, list);
    }
}

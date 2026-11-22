package com.luatweaker.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Shared loot-table fragments: conditions and the set_count function. */
final class LootConditions {

    private LootConditions() {
    }

    static JsonObject createSetCount(int min, int max) {
        JsonObject count = new JsonObject();
        count.addProperty(LootTableJsonBuilder.TYPE_KEY, LootTableJsonBuilder.UNIFORM_TYPE);
        count.addProperty(LootTableJsonBuilder.MIN_KEY, min);
        count.addProperty(LootTableJsonBuilder.MAX_KEY, max);
        JsonObject setCount = new JsonObject();
        setCount.addProperty(LootTableJsonBuilder.FUNCTION_KEY, LootTableJsonBuilder.SET_COUNT_FUNCTION);
        setCount.add(LootTableJsonBuilder.COUNT_KEY, count);
        return setCount;
    }

    static JsonObject createSetCount(double min, double max) {
        JsonObject count = new JsonObject();
        count.addProperty(LootTableJsonBuilder.TYPE_KEY, LootTableJsonBuilder.UNIFORM_TYPE);
        count.addProperty(LootTableJsonBuilder.MIN_KEY, min);
        count.addProperty(LootTableJsonBuilder.MAX_KEY, max);
        JsonObject setCount = new JsonObject();
        setCount.addProperty(LootTableJsonBuilder.FUNCTION_KEY, LootTableJsonBuilder.SET_COUNT_FUNCTION);
        setCount.add(LootTableJsonBuilder.COUNT_KEY, count);
        return setCount;
    }

    static JsonArray randomChance(double chance) {
        JsonObject condition = new JsonObject();
        condition.addProperty(LootTableJsonBuilder.CONDITION_KEY, "minecraft:random_chance");
        condition.addProperty(LootTableJsonBuilder.CHANCE_KEY, chance);
        JsonArray conditions = new JsonArray();
        conditions.add(condition);
        return conditions;
    }

    static JsonArray matchTool(String enchantmentId) {
        JsonObject level = new JsonObject();
        level.addProperty(LootTableJsonBuilder.MIN_KEY, 1);
        JsonObject enchantment = new JsonObject();
        enchantment.addProperty(LootTableJsonBuilder.ENCHANTMENT_KEY, enchantmentId);
        enchantment.add("levels", level);
        JsonArray enchantments = new JsonArray();
        enchantments.add(enchantment);
        JsonObject predicate = new JsonObject();
        predicate.add(LootTableJsonBuilder.PREDICATE_KEY, enchantments);
        JsonObject condition = new JsonObject();
        condition.addProperty(LootTableJsonBuilder.CONDITION_KEY, "minecraft:match_tool");
        condition.add(LootTableJsonBuilder.PREDICATE_KEY, predicate);
        JsonArray conditions = new JsonArray();
        conditions.add(condition);
        return conditions;
    }

    static JsonArray singleCondition(String conditionId) {
        JsonObject condition = new JsonObject();
        condition.addProperty(LootTableJsonBuilder.CONDITION_KEY, conditionId);
        JsonArray conditions = new JsonArray();
        conditions.add(condition);
        return conditions;
    }
}

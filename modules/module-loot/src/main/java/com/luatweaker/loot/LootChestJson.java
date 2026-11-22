package com.luatweaker.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/** Chest loot tables: one shared pool with per-entry counts and chances. */
final class LootChestJson {

    private LootChestJson() {
    }

    static JsonObject build(List<LootServiceImpl.ChestLootEntry> entries) {
        JsonArray poolEntries = new JsonArray();
        for (LootServiceImpl.ChestLootEntry entry : entries) {
            poolEntries.add(renderEntry(entry));
        }
        JsonObject pool = new JsonObject();
        pool.addProperty(LootTableJsonBuilder.ROLLS_KEY, LootTableJsonBuilder.SINGLE_COUNT);
        pool.add(LootTableJsonBuilder.ENTRIES_KEY, poolEntries);
        JsonObject root = new JsonObject();
        root.addProperty(LootTableJsonBuilder.TYPE_KEY, "minecraft:chest");
        JsonArray pools = new JsonArray();
        pools.add(pool);
        root.add(LootTableJsonBuilder.POOLS_KEY, pools);
        return root;
    }

    private static JsonObject renderEntry(LootServiceImpl.ChestLootEntry entry) {
        JsonObject rendered = new JsonObject();
        rendered.addProperty(LootTableJsonBuilder.TYPE_KEY, LootTableJsonBuilder.ITEM_TYPE);
        rendered.addProperty(LootTableJsonBuilder.NAME_KEY, entry.itemId());
        if (entry.minCount() != LootTableJsonBuilder.SINGLE_COUNT
            || entry.maxCount() != LootTableJsonBuilder.SINGLE_COUNT) {
            JsonObject count = new JsonObject();
            count.addProperty(LootTableJsonBuilder.TYPE_KEY, LootTableJsonBuilder.UNIFORM_TYPE);
            count.addProperty(LootTableJsonBuilder.MIN_KEY, entry.minCount());
            count.addProperty(LootTableJsonBuilder.MAX_KEY, entry.maxCount());
            JsonObject setCount = new JsonObject();
            setCount.addProperty(LootTableJsonBuilder.FUNCTION_KEY, LootTableJsonBuilder.SET_COUNT_FUNCTION);
            setCount.add(LootTableJsonBuilder.COUNT_KEY, count);
            JsonArray functions = new JsonArray();
            functions.add(setCount);
            rendered.add(LootTableJsonBuilder.FUNCTIONS_KEY, functions);
        }
        if (entry.chance() < LootTableJsonBuilder.FULL_CHANCE) {
            rendered.add(LootTableJsonBuilder.CONDITIONS_KEY, LootConditions.randomChance(entry.chance()));
        }
        return rendered;
    }
}

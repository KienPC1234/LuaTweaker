package com.luatweaker.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/** Entity loot tables: one pool per registered mob drop. */
final class LootEntityJson {

    private LootEntityJson() {
    }

    static JsonObject build(List<LootServiceImpl.MobDropEntry> drops) {
        JsonArray pools = new JsonArray();
        for (LootServiceImpl.MobDropEntry drop : drops) {
            pools.add(renderPool(drop));
        }
        JsonObject root = new JsonObject();
        root.addProperty(LootTableJsonBuilder.TYPE_KEY, "minecraft:entity");
        root.add(LootTableJsonBuilder.POOLS_KEY, pools);
        return root;
    }

    private static JsonObject renderPool(LootServiceImpl.MobDropEntry drop) {
        JsonObject entry = new JsonObject();
        entry.addProperty(LootTableJsonBuilder.TYPE_KEY, LootTableJsonBuilder.ITEM_TYPE);
        entry.addProperty(LootTableJsonBuilder.NAME_KEY, drop.itemId());
        JsonArray functions = renderFunctions(drop);
        if (!functions.isEmpty()) {
            entry.add(LootTableJsonBuilder.FUNCTIONS_KEY, functions);
        }
        if (drop.chance() < LootTableJsonBuilder.FULL_CHANCE) {
            entry.add(LootTableJsonBuilder.CONDITIONS_KEY, LootConditions.randomChance(drop.chance()));
        }
        JsonObject pool = new JsonObject();
        pool.addProperty(LootTableJsonBuilder.ROLLS_KEY, LootTableJsonBuilder.SINGLE_COUNT);
        JsonArray entries = new JsonArray();
        entries.add(entry);
        pool.add(LootTableJsonBuilder.ENTRIES_KEY, entries);
        return pool;
    }

    private static JsonArray renderFunctions(LootServiceImpl.MobDropEntry drop) {
        JsonArray functions = new JsonArray();
        if (drop.minCount() != LootTableJsonBuilder.SINGLE_COUNT
            || drop.maxCount() != LootTableJsonBuilder.SINGLE_COUNT) {
            functions.add(LootConditions.createSetCount(drop.minCount(), drop.maxCount()));
        }
        if (drop.lootingBonus() > 0) {
            JsonObject count = new JsonObject();
            count.addProperty(LootTableJsonBuilder.TYPE_KEY, LootTableJsonBuilder.UNIFORM_TYPE);
            count.addProperty(LootTableJsonBuilder.MIN_KEY, 0.0);
            count.addProperty(LootTableJsonBuilder.MAX_KEY, (double) drop.lootingBonus());
            JsonObject enchanted = new JsonObject();
            enchanted.addProperty(LootTableJsonBuilder.FUNCTION_KEY, "minecraft:enchanted_count_increase");
            enchanted.addProperty("enchantment", "minecraft:looting");
            enchanted.add("count", count);
            functions.add(enchanted);
        }
        return functions;
    }
}

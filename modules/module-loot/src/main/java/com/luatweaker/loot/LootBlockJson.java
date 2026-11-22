package com.luatweaker.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Block loot tables: silk-touch alternatives plus a simple count-based form. */
final class LootBlockJson {

    private static final String PARAMETERS_KEY = "parameters";
    private static final String CHILDREN_KEY = "children";

    private LootBlockJson() {
    }

    static JsonObject build(LootServiceImpl.BlockDropEntry drop) {
        JsonObject mainEntry = new JsonObject();
        mainEntry.addProperty(LootTableJsonBuilder.TYPE_KEY, LootTableJsonBuilder.ITEM_TYPE);
        mainEntry.addProperty(LootTableJsonBuilder.NAME_KEY, drop.itemId());
        if (drop.fortuneBonus() > 0) {
            JsonObject parameters = new JsonObject();
            parameters.addProperty("bonusMultiplier", drop.fortuneBonus());
            JsonObject fortune = new JsonObject();
            fortune.addProperty(LootTableJsonBuilder.FUNCTION_KEY, "minecraft:apply_bonus");
            fortune.addProperty("enchantment", "minecraft:fortune");
            fortune.addProperty("formula", "minecraft:uniform_bonus_count");
            fortune.add(PARAMETERS_KEY, parameters);
            JsonArray functions = new JsonArray();
            functions.add(fortune);
            mainEntry.add(LootTableJsonBuilder.FUNCTIONS_KEY, functions);
        }

        JsonArray entries = new JsonArray();
        if (drop.silkTouchDrop() != null) {
            JsonObject silkEntry = new JsonObject();
            silkEntry.addProperty(LootTableJsonBuilder.TYPE_KEY, LootTableJsonBuilder.ITEM_TYPE);
            silkEntry.add(LootTableJsonBuilder.CONDITIONS_KEY, LootConditions.matchTool("minecraft:silk_touch"));
            silkEntry.addProperty(LootTableJsonBuilder.NAME_KEY, drop.silkTouchDrop());
            JsonObject alternatives = new JsonObject();
            alternatives.addProperty(LootTableJsonBuilder.TYPE_KEY, "minecraft:alternatives");
            JsonArray children = new JsonArray();
            children.add(silkEntry);
            children.add(mainEntry);
            alternatives.add(CHILDREN_KEY, children);
            entries.add(alternatives);
        } else {
            entries.add(mainEntry);
        }

        JsonObject pool = new JsonObject();
        pool.addProperty(LootTableJsonBuilder.ROLLS_KEY, LootTableJsonBuilder.SINGLE_COUNT);
        pool.add(LootTableJsonBuilder.CONDITIONS_KEY,
            LootConditions.singleCondition(LootTableJsonBuilder.SURVIVES_EXPLOSION));
        pool.add(LootTableJsonBuilder.ENTRIES_KEY, entries);
        JsonObject root = new JsonObject();
        root.addProperty(LootTableJsonBuilder.TYPE_KEY, "minecraft:block");
        JsonArray pools = new JsonArray();
        pools.add(pool);
        root.add(LootTableJsonBuilder.POOLS_KEY, pools);
        return root;
    }

    static JsonObject buildSimple(String itemId, int minCount, int maxCount) {
        JsonObject entry = new JsonObject();
        entry.addProperty(LootTableJsonBuilder.TYPE_KEY, LootTableJsonBuilder.ITEM_TYPE);
        entry.addProperty(LootTableJsonBuilder.NAME_KEY, itemId);
        if (minCount != LootTableJsonBuilder.SINGLE_COUNT || maxCount != LootTableJsonBuilder.SINGLE_COUNT) {
            JsonArray functions = new JsonArray();
            functions.add(LootConditions.createSetCount(minCount, maxCount));
            entry.add(LootTableJsonBuilder.FUNCTIONS_KEY, functions);
        }

        JsonObject pool = new JsonObject();
        pool.addProperty(LootTableJsonBuilder.ROLLS_KEY, LootTableJsonBuilder.SINGLE_COUNT);
        pool.add(LootTableJsonBuilder.CONDITIONS_KEY,
            LootConditions.singleCondition(LootTableJsonBuilder.SURVIVES_EXPLOSION));
        JsonArray entries = new JsonArray();
        entries.add(entry);
        pool.add(LootTableJsonBuilder.ENTRIES_KEY, entries);
        JsonObject root = new JsonObject();
        root.addProperty(LootTableJsonBuilder.TYPE_KEY, "minecraft:block");
        JsonArray pools = new JsonArray();
        pools.add(pool);
        root.add(LootTableJsonBuilder.POOLS_KEY, pools);
        return root;
    }
}

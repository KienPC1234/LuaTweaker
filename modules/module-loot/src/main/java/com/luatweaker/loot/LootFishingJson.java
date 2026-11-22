package com.luatweaker.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/** Fishing category loot tables: pool with item weights and random_sequence. */
final class LootFishingJson {

    private static final double PERCENT_SCALE = 100.0;
    private static final double DEFAULT_ROLLS = 1.0;
    private static final double DEFAULT_BONUS_ROLLS = 0.0;

    private LootFishingJson() {
    }

    static JsonObject build(List<LootServiceImpl.FishingLootEntry> entries, String randomSequence) {
        if (randomSequence == null || randomSequence.isBlank()) {
            throw new IllegalArgumentException("randomSequence must not be blank");
        }
        JsonArray poolEntries = new JsonArray();
        for (LootServiceImpl.FishingLootEntry entry : entries) {
            poolEntries.add(renderEntry(entry));
        }
        JsonObject pool = new JsonObject();
        pool.addProperty("bonus_rolls", DEFAULT_BONUS_ROLLS);
        pool.add(LootTableJsonBuilder.ENTRIES_KEY, poolEntries);
        pool.addProperty("rolls", DEFAULT_ROLLS);

        JsonObject root = new JsonObject();
        root.addProperty(LootTableJsonBuilder.TYPE_KEY, "minecraft:fishing");
        JsonArray pools = new JsonArray();
        pools.add(pool);
        root.add(LootTableJsonBuilder.POOLS_KEY, pools);
        root.addProperty("random_sequence", randomSequence);
        return root;
    }

    private static JsonObject renderEntry(LootServiceImpl.FishingLootEntry entry) {
        JsonObject rendered = new JsonObject();
        rendered.addProperty(LootTableJsonBuilder.TYPE_KEY, LootTableJsonBuilder.ITEM_TYPE);
        rendered.addProperty(LootTableJsonBuilder.NAME_KEY, entry.itemId());
        rendered.addProperty(LootTableJsonBuilder.WEIGHT_KEY, (int) (entry.chance() * PERCENT_SCALE));
        return rendered;
    }
}

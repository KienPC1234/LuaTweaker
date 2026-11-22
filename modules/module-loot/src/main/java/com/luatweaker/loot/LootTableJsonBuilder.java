package com.luatweaker.loot;

import com.google.gson.Gson;

import java.util.List;

/**
 * Gson-built JSON for the loot tables injected into the virtual datapack.
 *
 * <p>No Minecraft/NeoForge classes are referenced so this output is unit-testable
 * without a game runtime. The function/condition ids and field names mirror the
 * vanilla 1.21.1 format. Domain rendering lives in focused package-private
 * builders; this class stays a thin facade over them.
 */
public final class LootTableJsonBuilder {

    static final Gson GSON = new Gson();
    static final int SINGLE_COUNT = 1;
    static final double FULL_CHANCE = 1.0;
    static final String TYPE_KEY = "type";
    static final String NAME_KEY = "name";
    static final String POOLS_KEY = "pools";
    static final String ENTRIES_KEY = "entries";
    static final String FUNCTIONS_KEY = "functions";
    static final String CONDITIONS_KEY = "conditions";
    static final String CONDITION_KEY = "condition";
    static final String CHANCE_KEY = "chance";
    static final String ENCHANTMENT_KEY = "enchantment";
    static final String PREDICATE_KEY = "predicate";
    static final String WEIGHT_KEY = "weight";
    static final String ROLLS_KEY = "rolls";
    static final String MIN_KEY = "min";
    static final String MAX_KEY = "max";
    static final String FUNCTION_KEY = "function";
    static final String COUNT_KEY = "count";
    static final String ITEM_TYPE = "minecraft:item";
    static final String UNIFORM_TYPE = "minecraft:uniform";
    static final String SET_COUNT_FUNCTION = "minecraft:set_count";
    static final String SURVIVES_EXPLOSION = "minecraft:survives_explosion";

    private LootTableJsonBuilder() {
    }

    public static String buildEntityLootTableJson(List<LootServiceImpl.MobDropEntry> drops) {
        return GSON.toJson(LootEntityJson.build(drops));
    }

    public static String buildChestLootTableJson(List<LootServiceImpl.ChestLootEntry> entries) {
        return GSON.toJson(LootChestJson.build(entries));
    }

    public static String buildBlockLootTableJson(LootServiceImpl.BlockDropEntry drop) {
        return GSON.toJson(LootBlockJson.build(drop));
    }

    /**
     * Builds a vanilla-shaped block loot table for a single item with a count
     * range (used for builder-declared block drops).
     */
    public static String buildSimpleBlockLootTableJson(String itemId, int minCount, int maxCount) {
        return GSON.toJson(LootBlockJson.buildSimple(itemId, minCount, maxCount));
    }

    /**
     * Builds one vanilla-shaped fishing category subtable
     * ({@code gameplay/fishing/fish|junk|treasure}). The envelope mirrors the
     * vanilla subtables exactly ({@code type/bonus_rolls/rolls/random_sequence})
     * so the untouched root router ({@code gameplay/fishing}) keeps its
     * junk/treasure/fish weights, quality values and open-water condition.
     *
     * <p>Replacing (not merging): the Lua API owns the named category pool,
     * so vanilla items of that category are replaced by the registered entries.
     */
    public static String buildFishingLootTableJson(List<LootServiceImpl.FishingLootEntry> entries,
                                                   String randomSequence) {
        return GSON.toJson(LootFishingJson.build(entries, randomSequence));
    }

    /**
     * Chance (0.0-1.0) as integer percentage points, truncating toward zero
     * (so 0.999 never becomes 100).
     */
    static int percentWeight(double chance) {
        return Double.valueOf(chance * 100.0).intValue();
    }
}

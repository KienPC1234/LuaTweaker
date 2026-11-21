package com.luatweaker.loot;

import java.util.List;

/**
 * Pure, dependency-free serializer for the loot table JSON emitted into the virtual datapack.
 *
 * <p>No Minecraft/NeoForge classes are referenced so this output is unit-testable without a game
 * runtime. The emitted function/condition ids and field names mirror the vanilla 1.21.1 format
 * (verified against {@code data/minecraft/loot_table/entities/*.json} of the game jar):
 * <ul>
 *     <li>{@code minecraft:set_count} with a {@code minecraft:uniform} number provider</li>
 *     <li>{@code minecraft:enchanted_count_increase} with {@code enchantment: minecraft:looting}
 *         and a {@code minecraft:uniform} count provider (the 1.21.1 id for the old
 *         {@code looting_enchant} function)</li>
 *     <li>{@code minecraft:random_chance} with a {@code chance} field</li>
 * </ul>
 */
public final class LootTableJsonBuilder {

    private static final String NAME_FIELD = "\"name\":\"";

    private LootTableJsonBuilder() {
    }

    public static String buildEntityLootTableJson(List<LootServiceImpl.MobDropEntry> drops) {
        StringBuilder pools = new StringBuilder(512);
        for (int i = 0; i < drops.size(); i++) {
            if (i > 0) {
                pools.append(',');
            }
            pools.append(renderEntityEntry(drops.get(i)));
        }

        return "{\"type\":\"minecraft:entity\",\"pools\":[" + pools + "]}";
    }

    private static String renderEntityEntry(LootServiceImpl.MobDropEntry drop) {
        StringBuilder entry = new StringBuilder(256);
        entry.append("{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",")
                .append(NAME_FIELD).append(drop.itemId()).append('\"');
        LootJsonFragments.appendFunctions(entry, renderEntityFunctions(drop));
        LootJsonFragments.appendRandomChance(entry, drop.chance());
        entry.append("}]}");
        return entry.toString();
    }

    private static String renderEntityFunctions(LootServiceImpl.MobDropEntry drop) {
        StringBuilder functions = new StringBuilder(256);
        if (drop.minCount() != 1 || drop.maxCount() != 1) {
            functions.append(",{\"function\":\"minecraft:set_count\","
                    + "\"count\":{\"type\":\"minecraft:uniform\",\"min\":")
                    .append(drop.minCount())
                    .append(",\"max\":").append(drop.maxCount()).append("}}");
        }
        if (drop.lootingBonus() > 0) {
            // Vanilla shape: uniform(0, bonus) extra items per Looting level, bounded by bonus.
            functions.append(",{\"function\":\"minecraft:enchanted_count_increase\","
                    + "\"enchantment\":\"minecraft:looting\","
                    + "\"count\":{\"type\":\"minecraft:uniform\",\"min\":0.0,\"max\":")
                    .append(drop.lootingBonus()).append(".0}}");
        }
        return LootJsonFragments.stripLeadingComma(functions);
    }

    public static String buildChestLootTableJson(List<LootServiceImpl.ChestLootEntry> entries) {
        StringBuilder poolEntries = new StringBuilder(512);
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                poolEntries.append(',');
            }
            poolEntries.append(renderChestEntry(entries.get(i)));
        }

        return "{\"type\":\"minecraft:chest\",\"pools\":[{\"rolls\":1,\"entries\":["
            + poolEntries + "]}]}";
    }

    private static String renderChestEntry(LootServiceImpl.ChestLootEntry entry) {
        StringBuilder rendered = new StringBuilder(256);
        rendered.append("{\"type\":\"minecraft:item\",")
                .append(NAME_FIELD).append(entry.itemId()).append('\"');
        if (entry.minCount() != 1 || entry.maxCount() != 1) {
            StringBuilder functions = new StringBuilder(128);
            functions.append("{\"function\":\"minecraft:set_count\","
                    + "\"count\":{\"type\":\"minecraft:uniform\",\"min\":")
                    .append(entry.minCount())
                    .append(",\"max\":").append(entry.maxCount()).append("}}");
            LootJsonFragments.appendFunctions(rendered, functions.toString());
        }
        LootJsonFragments.appendRandomChance(rendered, entry.chance());
        rendered.append('}');
        return rendered.toString();
    }

    public static String buildBlockLootTableJson(LootServiceImpl.BlockDropEntry drop) {
        StringBuilder pool = new StringBuilder(256);
        pool.append("{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",")
                .append(NAME_FIELD).append(drop.itemId()).append('\"');

        if (drop.fortuneBonus() > 0) {
            StringBuilder functions = new StringBuilder(192);
            functions.append("{\"function\":\"minecraft:apply_bonus\","
                    + "\"enchantment\":\"minecraft:fortune\","
                    + "\"formula\":\"minecraft:uniform_bonus_count\","
                    + "\"parameters\":{\"bonusMultiplier\":")
                    .append(drop.fortuneBonus()).append("}}");
            LootJsonFragments.appendFunctions(pool, functions.toString());
        }
        if (drop.silkTouchDrop() != null) {
            pool.append(",\"conditions\":[{\"condition\":\"minecraft:match_tool\","
                    + "\"predicate\":{\"enchantments\":[{"
                    + "\"enchantment\":\"minecraft:silk_touch\","
                    + "\"levels\":{\"min\":1}}]}}]");
        }
        pool.append("}]}");

        return "{\"type\":\"minecraft:block\",\"pools\":[" + pool + "]}";
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
        if (randomSequence == null || randomSequence.isBlank()) {
            throw new IllegalArgumentException("randomSequence must not be blank");
        }
        StringBuilder poolEntries = new StringBuilder(512);
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                poolEntries.append(',');
            }
            poolEntries.append(renderFishingEntry(entries.get(i)));
        }

        return "{\"type\":\"minecraft:fishing\",\"pools\":[{\"bonus_rolls\":0.0,\"entries\":["
            + poolEntries + "],\"rolls\":1.0}],\"random_sequence\":\"" + randomSequence + "\"}";
    }

    private static String renderFishingEntry(LootServiceImpl.FishingLootEntry entry) {
        return "{\"type\":\"minecraft:item\"," + NAME_FIELD + entry.itemId() + "\","
                + "\"weight\":" + LootJsonFragments.percentWeight(entry.chance()) + "}";
    }
}

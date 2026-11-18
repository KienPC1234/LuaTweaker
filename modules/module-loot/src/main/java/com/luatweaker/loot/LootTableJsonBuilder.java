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

    private LootTableJsonBuilder() {
    }

    public static String buildEntityLootTableJson(List<LootServiceImpl.MobDropEntry> drops) {
        StringBuilder pools = new StringBuilder();
        for (int i = 0; i < drops.size(); i++) {
            LootServiceImpl.MobDropEntry drop = drops.get(i);
            if (i > 0) {
                pools.append(",");
            }

            StringBuilder functions = new StringBuilder();
            if (drop.minCount() != 1 || drop.maxCount() != 1) {
                functions.append(",{\"function\":\"minecraft:set_count\",")
                    .append("\"count\":{\"type\":\"minecraft:uniform\",")
                    .append("\"min\":").append(drop.minCount()).append(",")
                    .append("\"max\":").append(drop.maxCount()).append("}}");
            }
            if (drop.lootingBonus() > 0) {
                // Vanilla shape: uniform(0, bonus) extra items per Looting level, bounded by bonus.
                functions.append(",{\"function\":\"minecraft:enchanted_count_increase\",")
                    .append("\"enchantment\":\"minecraft:looting\",")
                    .append("\"count\":{\"type\":\"minecraft:uniform\",")
                    .append("\"min\":0.0,\"max\":").append(drop.lootingBonus()).append(".0}}");
            }

            pools.append("{\"rolls\":1,\"entries\":[{")
                .append("\"type\":\"minecraft:item\",")
                .append("\"name\":\"").append(drop.itemId()).append("\"");
            appendFunctions(pools, functions);
            appendRandomChance(pools, drop.chance());
            pools.append("}]}");
        }

        return "{\"type\":\"minecraft:entity\",\"pools\":[" + pools + "]}";
    }

    public static String buildChestLootTableJson(List<LootServiceImpl.ChestLootEntry> entries) {
        StringBuilder poolEntries = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            LootServiceImpl.ChestLootEntry entry = entries.get(i);
            if (i > 0) {
                poolEntries.append(",");
            }

            StringBuilder functions = new StringBuilder();
            if (entry.minCount() != 1 || entry.maxCount() != 1) {
                functions.append(",{\"function\":\"minecraft:set_count\",")
                    .append("\"count\":{\"type\":\"minecraft:uniform\",")
                    .append("\"min\":").append(entry.minCount()).append(",")
                    .append("\"max\":").append(entry.maxCount()).append("}}");
            }

            poolEntries.append("{\"type\":\"minecraft:item\",")
                .append("\"name\":\"").append(entry.itemId()).append("\"");
            appendFunctions(poolEntries, functions);
            appendRandomChance(poolEntries, entry.chance());
            poolEntries.append("}");
        }

        return "{\"type\":\"minecraft:chest\",\"pools\":[{\"rolls\":1,\"entries\":["
            + poolEntries + "]}]}";
    }

    public static String buildBlockLootTableJson(LootServiceImpl.BlockDropEntry drop) {
        StringBuilder pool = new StringBuilder();
        pool.append("{\"rolls\":1,\"entries\":[{")
            .append("\"type\":\"minecraft:item\",")
            .append("\"name\":\"").append(drop.itemId()).append("\"");

        StringBuilder functions = new StringBuilder();
        if (drop.fortuneBonus() > 0) {
            functions.append(",{\"function\":\"minecraft:apply_bonus\",")
                .append("\"enchantment\":\"minecraft:fortune\",")
                .append("\"formula\":\"minecraft:uniform_bonus_count\",")
                .append("\"parameters\":{\"bonusMultiplier\":").append(drop.fortuneBonus()).append("}}");
        }
        appendFunctions(pool, functions);
        if (drop.silkTouchDrop() != null) {
            pool.append(",\"conditions\":[{")
                .append("\"condition\":\"minecraft:match_tool\",")
                .append("\"predicate\":{\"enchantments\":[{")
                .append("\"enchantment\":\"minecraft:silk_touch\",")
                .append("\"levels\":{\"min\":1}}]}}]");
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
        StringBuilder poolEntries = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            LootServiceImpl.FishingLootEntry entry = entries.get(i);
            if (i > 0) {
                poolEntries.append(",");
            }

            poolEntries.append("{\"type\":\"minecraft:item\",")
                .append("\"name\":\"").append(entry.itemId()).append("\",")
                .append("\"weight\":").append((int) (entry.chance() * 100))
                .append("}");
        }

        return "{\"type\":\"minecraft:fishing\",\"pools\":[{\"bonus_rolls\":0.0,\"entries\":["
            + poolEntries + "],\"rolls\":1.0}],\"random_sequence\":\"" + randomSequence + "\"}";
    }

    private static void appendFunctions(StringBuilder out, StringBuilder functions) {
        if (functions.length() > 0) {
            out.append(",\"functions\":[").append(functions.substring(1)).append("]");
        }
    }

    private static void appendRandomChance(StringBuilder out, double chance) {
        if (chance < 1.0) {
            out.append(",\"conditions\":[{")
                .append("\"condition\":\"minecraft:random_chance\",")
                .append("\"chance\":").append(chance)
                .append("}]");
        }
    }
}

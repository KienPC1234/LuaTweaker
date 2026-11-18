package com.luatweaker.loot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LootTableJsonBuilderTest {

    @Test
    void testEntityTable_ContainsItemChanceCountAndLootingBonus() {
        LootServiceImpl.MobDropEntry drop =
            new LootServiceImpl.MobDropEntry("minecraft:zombie", "minecraft:diamond", 0.1, 1, 3, 2);

        String json = LootTableJsonBuilder.buildEntityLootTableJson(List.of(drop));

        assertValidJsonStructure(json);
        assertTrue(json.contains("\"name\":\"minecraft:diamond\""));
        assertTrue(json.contains("\"chance\":0.1"));
        assertTrue(json.contains("\"condition\":\"minecraft:random_chance\""));
        assertTrue(json.contains("\"function\":\"minecraft:set_count\""));
        assertTrue(json.contains("\"min\":1"));
        assertTrue(json.contains("\"max\":3"));
        assertTrue(json.contains("\"function\":\"minecraft:enchanted_count_increase\""));
        assertTrue(json.contains("\"enchantment\":\"minecraft:looting\""));
        assertTrue(json.contains("\"count\":{\"type\":\"minecraft:uniform\",\"min\":0.0,\"max\":2.0}"));
    }

    @Test
    void testEntityTable_OmitsLootingFunctionWhenBonusIsZero() {
        LootServiceImpl.MobDropEntry drop =
            new LootServiceImpl.MobDropEntry("minecraft:zombie", "minecraft:bone", 1.0, 1, 1, 0);

        String json = LootTableJsonBuilder.buildEntityLootTableJson(List.of(drop));

        assertValidJsonStructure(json);
        assertFalse(json.contains("enchanted_count_increase"));
        assertFalse(json.contains("random_chance"));
        assertFalse(json.contains("set_count"));
    }

    @Test
    void testEntityTable_AfterRemoval_ExcludesRemovedItem() {
        LootServiceImpl service = new LootServiceImpl();
        service.addMobDrop("minecraft:zombie", "minecraft:diamond", 0.1, 1, 2, 1);
        service.addMobDrop("minecraft:zombie", "minecraft:rotten_flesh", 0.5, 1, 1, 0);
        service.removeMobDrop("minecraft:zombie", "minecraft:diamond");

        String json = LootTableJsonBuilder.buildEntityLootTableJson(
            service.getPendingMobDrops().get("minecraft:zombie"));

        assertValidJsonStructure(json);
        assertFalse(json.contains("minecraft:diamond"));
        assertTrue(json.contains("minecraft:rotten_flesh"));
    }

    @Test
    void testChestTable_ContainsItemChanceAndCount() {
        LootServiceImpl.ChestLootEntry entry =
            new LootServiceImpl.ChestLootEntry("minecraft:chests/simple_dungeon", "minecraft:emerald", 0.05, 2, 4);

        String json = LootTableJsonBuilder.buildChestLootTableJson(List.of(entry));

        assertValidJsonStructure(json);
        assertTrue(json.contains("\"type\":\"minecraft:chest\""));
        assertTrue(json.contains("\"name\":\"minecraft:emerald\""));
        assertTrue(json.contains("\"chance\":0.05"));
        assertTrue(json.contains("\"function\":\"minecraft:set_count\""));
        assertTrue(json.contains("\"min\":2"));
        assertTrue(json.contains("\"max\":4"));
    }

    @Test
    void testChestTable_AfterRemoval_ExcludesRemovedItem() {
        LootServiceImpl service = new LootServiceImpl();
        service.addChestLoot("minecraft:chests/simple_dungeon", "minecraft:diamond", 0.1, 1, 1);
        service.addChestLoot("minecraft:chests/simple_dungeon", "minecraft:emerald", 0.1, 1, 1);
        service.removeChestLoot("minecraft:chests/simple_dungeon", "minecraft:diamond");

        String json = LootTableJsonBuilder.buildChestLootTableJson(
            service.getPendingChestLoot().get("minecraft:chests/simple_dungeon"));

        assertValidJsonStructure(json);
        assertFalse(json.contains("minecraft:diamond"));
        assertTrue(json.contains("minecraft:emerald"));
    }

    @Test
    void testBlockTable_EmitsFortuneAndSilkTouchValidStructure() {
        LootServiceImpl.BlockDropEntry drop =
            new LootServiceImpl.BlockDropEntry("mymod:ruby_ore", "mymod:ruby", 2, "mymod:ruby_ore");

        String json = LootTableJsonBuilder.buildBlockLootTableJson(drop);

        assertValidJsonStructure(json);
        assertTrue(json.contains("\"function\":\"minecraft:apply_bonus\""));
        assertTrue(json.contains("\"bonusMultiplier\":2"));
        assertTrue(json.contains("\"condition\":\"minecraft:match_tool\""));
    }

    @Test
    void testFishingTable_UsesVanillaSubtableEnvelope() {
        LootServiceImpl.FishingLootEntry entry =
            new LootServiceImpl.FishingLootEntry("minecraft:cod", 0.5, "FISH");

        String json = LootTableJsonBuilder.buildFishingLootTableJson(
            List.of(entry), "minecraft:gameplay/fishing/fish");

        assertValidJsonStructure(json);
        assertTrue(json.contains("\"type\":\"minecraft:fishing\""));
        assertTrue(json.contains("\"bonus_rolls\":0.0"));
        assertTrue(json.contains("\"rolls\":1.0"));
        assertTrue(json.contains("\"random_sequence\":\"minecraft:gameplay/fishing/fish\""));
        assertTrue(json.contains("\"name\":\"minecraft:cod\""));
        assertTrue(json.contains("\"weight\":50"));
    }

    @Test
    void testFishingTable_RejectsBlankRandomSequence() {
        LootServiceImpl.FishingLootEntry entry =
            new LootServiceImpl.FishingLootEntry("minecraft:cod", 0.5, "FISH");

        assertThrows(IllegalArgumentException.class, () ->
            LootTableJsonBuilder.buildFishingLootTableJson(List.of(entry), "  "));
    }

    private static void assertValidJsonStructure(String json) {
        int braces = 0;
        int brackets = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            switch (c) {
                case '{' -> braces++;
                case '}' -> braces--;
                case '[' -> brackets++;
                case ']' -> brackets--;
                default -> { }
            }
            assertTrue(braces >= 0 && brackets >= 0, "Unbalanced closing bracket at index " + i + ": " + json);
        }
        assertFalse(inString, "Unterminated JSON string: " + json);
        assertEquals(0, braces, "Unbalanced braces: " + json);
        assertEquals(0, brackets, "Unbalanced brackets: " + json);
    }
}

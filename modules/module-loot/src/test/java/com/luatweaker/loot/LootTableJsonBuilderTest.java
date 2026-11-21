package com.luatweaker.loot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootTableJsonBuilderTest {

    private static final String ZOMBIE = "minecraft:zombie";
    private static final String DIAMOND = "minecraft:diamond";
    private static final String BONE = "minecraft:bone";
    private static final String FLESH = "minecraft:rotten_flesh";

    private static final String DIAMOND_NAME = "\"name\":\"minecraft:diamond\"";
    private static final String COUNT_CHANCE = "\"chance\":0.1";
    private static final String RANDOM_CHANCE = "\"condition\":\"minecraft:random_chance\"";
    private static final String SET_COUNT = "\"function\":\"minecraft:set_count\"";
    private static final String MIN_COUNT = "\"min\":1";
    private static final String MAX_COUNT = "\"max\":3";
    private static final String ENCHANTED_INCREASE = "\"function\":\"minecraft:enchanted_count_increase\"";
    private static final String LOOTING = "\"enchantment\":\"minecraft:looting\"";
    private static final String LOOTING_COUNT = "\"count\":{\"type\":\"minecraft:uniform\",\"min\":0.0,\"max\":2.0}";

    private String entityJson;
    private String zeroBonusJson;
    private String afterRemovalJson;

    @BeforeEach
    void setup() {
        LootServiceImpl bonusService = new LootServiceImpl();
        bonusService.addMobDrop(ZOMBIE, DIAMOND, 0.1, 1, 3, 2);
        entityJson = LootTableJsonBuilder.buildEntityLootTableJson(
            bonusService.getPendingMobDrops().get(ZOMBIE));

        LootServiceImpl plainService = new LootServiceImpl();
        plainService.addMobDrop(ZOMBIE, BONE, 1.0, 1, 1, 0);
        zeroBonusJson = LootTableJsonBuilder.buildEntityLootTableJson(
            plainService.getPendingMobDrops().get(ZOMBIE));

        LootServiceImpl removalService = new LootServiceImpl();
        removalService.addMobDrop(ZOMBIE, DIAMOND, 0.1, 1, 2, 1);
        removalService.addMobDrop(ZOMBIE, FLESH, 0.5, 1, 1, 0);
        removalService.removeMobDrop(ZOMBIE, DIAMOND);
        afterRemovalJson = LootTableJsonBuilder.buildEntityLootTableJson(
            removalService.getPendingMobDrops().get(ZOMBIE));
    }

    @Test
    void entityTableIsStructurallyValid() {
        assertDoesNotThrow(() -> JsonAssertions.assertValid(entityJson),
            "the generated entity loot table must be structurally valid JSON");
    }

    @Test
    void entityTableDescribesAddedItem() {
        assertTrue(JsonAssertions.containsAll(entityJson, DIAMOND_NAME, COUNT_CHANCE, RANDOM_CHANCE,
                SET_COUNT, MIN_COUNT, MAX_COUNT),
            "the entity entry must pin item name, chance and the uniform count functions");
    }

    @Test
    void entityTableAppliesLootingBonus() {
        assertTrue(JsonAssertions.containsAll(entityJson, ENCHANTED_INCREASE, LOOTING, LOOTING_COUNT),
            "a positive looting bonus must emit the looting count increase");
    }

    @Test
    void zeroBonusTableIsStructurallyValid() {
        assertDoesNotThrow(() -> JsonAssertions.assertValid(zeroBonusJson),
            "an entity table without a looting bonus must still be valid JSON");
    }

    @Test
    void zeroBonusTableOmitsBonusFunctions() {
        assertTrue(JsonAssertions.containsNone(zeroBonusJson, ENCHANTED_INCREASE, RANDOM_CHANCE, SET_COUNT),
            "a zero looting bonus must omit looting, random_chance and set_count");
    }

    @Test
    void entityTableAfterRemovalIsStructurallyValid() {
        assertDoesNotThrow(() -> JsonAssertions.assertValid(afterRemovalJson),
            "the entity table must stay valid JSON after applying a removal");
    }

    @Test
    void entityTableAfterRemovalExcludesRemovedItem() {
        assertTrue(JsonAssertions.containsNone(afterRemovalJson, DIAMOND_NAME),
            "the removed item must not appear in the emitted entity table");
    }

    @Test
    void entityTableAfterRemovalKeepsOtherItem() {
        assertTrue(JsonAssertions.containsAll(afterRemovalJson, "\"name\":\"minecraft:rotten_flesh\""),
            "the entity's other drop must survive the removal");
    }
}

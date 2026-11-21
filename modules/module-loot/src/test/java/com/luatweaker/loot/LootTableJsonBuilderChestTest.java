package com.luatweaker.loot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootTableJsonBuilderChestTest {

    private static final String CHEST_TABLE = "minecraft:chests/simple_dungeon";
    private static final String DIAMOND = "minecraft:diamond";
    private static final String EMERALD = "minecraft:emerald";

    private static final String EMERALD_NAME = "\"name\":\"minecraft:emerald\"";
    private static final String DIAMOND_NAME = "\"name\":\"minecraft:diamond\"";
    private static final String CHEST_TYPE = "\"type\":\"minecraft:chest\"";
    private static final String EMERALD_CHANCE = "\"chance\":0.05";
    private static final String SET_COUNT = "\"function\":\"minecraft:set_count\"";
    private static final String MIN_COUNT = "\"min\":2";
    private static final String MAX_COUNT = "\"max\":4";

    private String chestJson;
    private String afterRemovalJson;

    @BeforeEach
    void setup() {
        LootServiceImpl service = new LootServiceImpl();
        service.addChestLoot(CHEST_TABLE, EMERALD, 0.05, 2, 4);
        chestJson = LootTableJsonBuilder.buildChestLootTableJson(
            service.getPendingChestLoot().get(CHEST_TABLE));

        LootServiceImpl removalService = new LootServiceImpl();
        removalService.addChestLoot(CHEST_TABLE, DIAMOND, 0.1, 1, 1);
        removalService.addChestLoot(CHEST_TABLE, EMERALD, 0.1, 1, 1);
        removalService.removeChestLoot(CHEST_TABLE, DIAMOND);
        afterRemovalJson = LootTableJsonBuilder.buildChestLootTableJson(
            removalService.getPendingChestLoot().get(CHEST_TABLE));
    }

    @Test
    void chestTableIsStructurallyValid() {
        assertDoesNotThrow(() -> JsonAssertions.assertValid(chestJson),
            "the generated chest loot table must be structurally valid JSON");
    }

    @Test
    void chestTableDescribesEntry() {
        assertTrue(JsonAssertions.containsAll(chestJson, CHEST_TYPE, EMERALD_NAME, EMERALD_CHANCE,
                SET_COUNT, MIN_COUNT, MAX_COUNT),
            "the chest entry must pin the chest type, item, chance and count range");
    }

    @Test
    void chestTableAfterRemovalIsStructurallyValid() {
        assertDoesNotThrow(() -> JsonAssertions.assertValid(afterRemovalJson),
            "the chest table must stay valid JSON after applying a removal");
    }

    @Test
    void chestTableAfterRemovalExcludesRemovedItem() {
        assertTrue(JsonAssertions.containsNone(afterRemovalJson, DIAMOND_NAME),
            "the removed item must not appear in the emitted chest table");
    }

    @Test
    void chestTableAfterRemovalKeepsOtherItem() {
        assertTrue(JsonAssertions.containsAll(afterRemovalJson, EMERALD_NAME),
            "the chest table's other entry must survive the removal");
    }
}

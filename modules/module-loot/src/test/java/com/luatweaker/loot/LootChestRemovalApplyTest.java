package com.luatweaker.loot;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootChestRemovalApplyTest {

    private static final String CHEST_TABLE = "minecraft:chests/simple_dungeon";
    private static final String ZOMBIE = "minecraft:zombie";
    private static final String DIAMOND = "minecraft:diamond";
    private static final String EMERALD = "minecraft:emerald";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private LootServiceImpl lootService;

    @BeforeEach
    void setup() {
        lootService = new LootServiceImpl();
    }

    private List<String> chestItemIds(String tableId) {
        return lootService.getPendingChestLoot().get(tableId).stream()
            .map(LootServiceImpl.ChestLootEntry::itemId)
            .toList();
    }

    private List<String> mobItemIds(String entityId) {
        return lootService.getPendingMobDrops().get(entityId).stream()
            .map(LootServiceImpl.MobDropEntry::itemId)
            .toList();
    }

    @Test
    void removeChestLootReturnsTrue() {
        lootService.addChestLoot(CHEST_TABLE, DIAMOND, 0.05, 1, 2);

        assertTrue(lootService.removeChestLoot(CHEST_TABLE, DIAMOND),
            "removing a pending chest entry must report true");
    }

    @Test
    void removeChestLootDropsOnlyMatchingItem() {
        lootService.addChestLoot(CHEST_TABLE, DIAMOND, 0.05, 1, 2);
        lootService.addChestLoot(CHEST_TABLE, EMERALD, 0.2, 1, 1);

        lootService.removeChestLoot(CHEST_TABLE, DIAMOND);

        assertEquals(List.of(EMERALD), chestItemIds(CHEST_TABLE),
            "only the removed item may disappear from the chest table");
    }

    @Test
    void removeChestLootCountsRemovalInSummary() {
        lootService.addChestLoot(CHEST_TABLE, DIAMOND, 0.05, 1, 2);
        lootService.addChestLoot(CHEST_TABLE, EMERALD, 0.2, 1, 1);

        lootService.removeChestLoot(CHEST_TABLE, DIAMOND);

        assertEquals(List.of(1, 1),
            List.of(lootService.getModifications().get("chestLoot"),
                lootService.getModifications().get("removals")),
            "removing a pending chest entry must decrement chest loot and add one removal");
    }

    @Test
    void removalKeepsOtherEntityDrops() {
        lootService.addMobDrop(ZOMBIE, DIAMOND, 0.1, 1, 1, 0);
        lootService.addChestLoot(CHEST_TABLE, EMERALD, 0.1, 1, 1);

        lootService.removeChestLoot(CHEST_TABLE, EMERALD);

        assertEquals(List.of(DIAMOND), mobItemIds(ZOMBIE),
            "a chest removal must not touch mob drops for the same item id");
    }

    @Test
    void removalKeepsChestLootWhenRemovingMobDrop() {
        lootService.addChestLoot(CHEST_TABLE, DIAMOND, 0.1, 1, 1);
        lootService.addMobDrop(ZOMBIE, DIAMOND, 0.1, 1, 1, 0);

        lootService.removeMobDrop(ZOMBIE, DIAMOND);

        assertEquals(List.of(DIAMOND), chestItemIds(CHEST_TABLE),
            "a mob removal must not touch chest loot for the same item id");
    }
}

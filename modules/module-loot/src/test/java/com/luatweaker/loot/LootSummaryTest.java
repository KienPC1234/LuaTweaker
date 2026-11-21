package com.luatweaker.loot;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootSummaryTest {

    private static final String ZOMBIE = "minecraft:zombie";
    private static final String SKELETON = "minecraft:skeleton";
    private static final String DIAMOND = "minecraft:diamond";
    private static final String ARROW = "minecraft:arrow";
    private static final String GOLD = "minecraft:gold_ingot";
    private static final String CHEST_TABLE = "minecraft:chests/simple_dungeon";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private LootServiceImpl lootService;

    @BeforeEach
    void setup() {
        lootService = new LootServiceImpl();
    }

    private void populateEveryKind() {
        lootService.addMobDrop(ZOMBIE, DIAMOND, 0.1, 1, 1, 0);
        lootService.addMobDrop(SKELETON, ARROW, 0.5, 1, 3, 0);
        lootService.addChestLoot(CHEST_TABLE, GOLD, 0.2, 1, 4);
        lootService.setBlockDrop("mymod:ore", "mymod:gem", 1, null);
        lootService.addFishingLoot("minecraft:stick", 0.1, "JUNK");
        lootService.removeMobDrop("minecraft:creeper", "minecraft:gunpowder");
    }

    @Test
    void modificationsCountEveryKind() {
        populateEveryKind();

        assertEquals(Map.of("mobDrops", 2, "chestLoot", 1, "blockDrops", 1,
                "fishingLoot", 1, "removals", 1),
            lootService.getModifications(),
            "the summary must count mob drops, chest loot, block drops, fishing loot and removals");
    }

    @Test
    void clearAllEmptiesEveryPendingCollection() {
        populateEveryKind();

        lootService.clearAll();

        assertEquals(List.of(true, true, true, true, true),
            List.of(
                lootService.getPendingMobDrops().isEmpty(),
                lootService.getPendingChestLoot().isEmpty(),
                lootService.getPendingBlockDrops().isEmpty(),
                lootService.getPendingFishingLoot().isEmpty(),
                lootService.getPendingRemovals().isEmpty()),
            "clearAll must empty every pending collection");
    }
}

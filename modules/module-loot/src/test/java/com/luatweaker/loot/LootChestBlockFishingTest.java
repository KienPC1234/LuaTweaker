package com.luatweaker.loot;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LootChestBlockFishingTest {

    private static final String CHEST_TABLE = "minecraft:chests/simple_dungeon";
    private static final String DIAMOND = "minecraft:diamond";
    private static final String EMERALD = "minecraft:emerald";
    private static final String RUBY_ORE = "mymod:ruby_ore";
    private static final String RUBY = "mymod:ruby";
    private static final String FISH = "minecraft:fish";
    private static final String TREASURE_CHEST = "mymod:treasure_chest";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private LootServiceImpl lootService;

    @BeforeEach
    void setup() {
        lootService = new LootServiceImpl();
    }

    @Test
    void addChestLootStoresEntry() {
        lootService.addChestLoot(CHEST_TABLE, EMERALD, 0.05, 2, 4);

        LootServiceImpl.ChestLootEntry expected =
            new LootServiceImpl.ChestLootEntry(CHEST_TABLE, EMERALD, 0.05, 2, 4);

        assertEquals(Map.of(CHEST_TABLE, List.of(expected)), lootService.getPendingChestLoot(),
            "addChestLoot must store the exact entry under the loot table id");
    }

    @Test
    void addChestLootRejectsBlankTableId() {
        assertThrows(IllegalArgumentException.class,
            () -> lootService.addChestLoot("", DIAMOND, 0.1, 1, 1),
            "a blank loot table id must be rejected");
    }

    @Test
    void setBlockDropStoresEntry() {
        lootService.setBlockDrop(RUBY_ORE, RUBY, 2, RUBY_ORE);

        LootServiceImpl.BlockDropEntry expected =
            new LootServiceImpl.BlockDropEntry(RUBY_ORE, RUBY, 2, RUBY_ORE);

        assertEquals(Map.of(RUBY_ORE, expected), lootService.getPendingBlockDrops(),
            "setBlockDrop must store the exact entry under the block id");
    }

    @Test
    void setBlockDropAllowsNullSilkTouchDrop() {
        lootService.setBlockDrop("minecraft:stone", "minecraft:cobblestone", 0, null);

        LootServiceImpl.BlockDropEntry entry = lootService.getPendingBlockDrops().get("minecraft:stone");

        assertNull(entry.silkTouchDrop(), "a null silk-touch drop must be stored as null, not invented");
    }

    @Test
    void addFishingLootStoresEntry() {
        lootService.addFishingLoot(TREASURE_CHEST, 0.01, "TREASURE");

        assertEquals(List.of(new LootServiceImpl.FishingLootEntry(TREASURE_CHEST, 0.01, "TREASURE")),
            lootService.getPendingFishingLoot(),
            "addFishingLoot must store the exact entry in registration order");
    }

    @Test
    void addFishingLootNormalizesCategory() {
        lootService.addFishingLoot(FISH, 0.5, "fish");

        assertEquals("FISH", lootService.getPendingFishingLoot().get(0).category(),
            "the fishing category must be normalized to upper case");
    }

    @Test
    void addFishingLootRejectsInvalidCategory() {
        assertThrows(IllegalArgumentException.class,
            () -> lootService.addFishingLoot(FISH, 0.5, "INVALID"),
            "an unknown fishing category must be rejected");
    }

    @Test
    void addFishingLootRejectsInvalidChance() {
        assertThrows(IllegalArgumentException.class,
            () -> lootService.addFishingLoot(FISH, 2.0, "FISH"),
            "a fishing chance above 1.0 must be rejected");
    }
}

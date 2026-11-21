package com.luatweaker.loot;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LootRemovalTest {

    private static final String ZOMBIE = "minecraft:zombie";
    private static final String DIAMOND = "minecraft:diamond";
    private static final String FLESH = "minecraft:rotten_flesh";
    private static final String CHEST_TABLE = "minecraft:chests/simple_dungeon";
    private static final String IRON = "minecraft:iron_ingot";

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
    void removeMobDropStoresRemoval() {
        lootService.removeMobDrop(ZOMBIE, FLESH);

        assertEquals(List.of(new LootServiceImpl.RemovalEntry("mob:" + ZOMBIE, FLESH)),
            lootService.getPendingRemovals(),
            "a mob removal must be recorded with the mob: target prefix");
    }

    @Test
    void removeChestLootStoresRemoval() {
        lootService.removeChestLoot(CHEST_TABLE, IRON);

        assertEquals(List.of(new LootServiceImpl.RemovalEntry("chest:" + CHEST_TABLE, IRON)),
            lootService.getPendingRemovals(),
            "a chest removal must be recorded with the chest: target prefix");
    }

    @Test
    void removeMobDropRejectsBlankEntityId() {
        assertThrows(IllegalArgumentException.class,
            () -> lootService.removeMobDrop("", DIAMOND),
            "a mob removal with a blank entity id must be rejected");
    }

    @Test
    void removeMobDropRejectsBlankItemId() {
        assertThrows(IllegalArgumentException.class,
            () -> lootService.removeMobDrop(ZOMBIE, ""),
            "a mob removal with a blank item id must be rejected");
    }

    @Test
    void removeChestLootRejectsBlankTableId() {
        assertThrows(IllegalArgumentException.class,
            () -> lootService.removeChestLoot("", DIAMOND),
            "a chest removal with a blank table id must be rejected");
    }

    @Test
    void removeChestLootRejectsBlankItemId() {
        assertThrows(IllegalArgumentException.class,
            () -> lootService.removeChestLoot(CHEST_TABLE, ""),
            "a chest removal with a blank item id must be rejected");
    }
}

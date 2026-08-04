package com.luatweaker.loot;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LootServiceImplTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private LootServiceImpl lootService;

    @BeforeEach
    void setup() {
        lootService = new LootServiceImpl();
    }

    @Test
    void testAddMobDrop_StoresEntry() {
        lootService.addMobDrop("minecraft:zombie", "minecraft:diamond", 0.1, 1, 3, 1);

        Map<String, List<LootServiceImpl.MobDropEntry>> drops = lootService.getPendingMobDrops();
        assertEquals(1, drops.size());
        assertTrue(drops.containsKey("minecraft:zombie"));

        LootServiceImpl.MobDropEntry entry = drops.get("minecraft:zombie").get(0);
        assertEquals("minecraft:zombie", entry.entityId());
        assertEquals("minecraft:diamond", entry.itemId());
        assertEquals(0.1, entry.chance());
        assertEquals(1, entry.minCount());
        assertEquals(3, entry.maxCount());
        assertEquals(1, entry.lootingBonus());
    }

    @Test
    void testAddMobDrop_MultipleDropsSameEntity() {
        lootService.addMobDrop("minecraft:zombie", "minecraft:diamond", 0.1, 1, 1, 0);
        lootService.addMobDrop("minecraft:zombie", "minecraft:iron_ingot", 0.5, 2, 4, 1);

        Map<String, List<LootServiceImpl.MobDropEntry>> drops = lootService.getPendingMobDrops();
        assertEquals(2, drops.get("minecraft:zombie").size());
    }

    @Test
    void testAddMobDrop_RejectsBlankEntityId() {
        assertThrows(IllegalArgumentException.class, () ->
            lootService.addMobDrop("", "minecraft:diamond", 0.1, 1, 1, 0));
    }

    @Test
    void testAddMobDrop_RejectsBlankItemId() {
        assertThrows(IllegalArgumentException.class, () ->
            lootService.addMobDrop("minecraft:zombie", "", 0.1, 1, 1, 0));
    }

    @Test
    void testAddMobDrop_RejectsNegativeChance() {
        assertThrows(IllegalArgumentException.class, () ->
            lootService.addMobDrop("minecraft:zombie", "minecraft:diamond", -0.1, 1, 1, 0));
    }

    @Test
    void testAddMobDrop_RejectsChanceAboveOne() {
        assertThrows(IllegalArgumentException.class, () ->
            lootService.addMobDrop("minecraft:zombie", "minecraft:diamond", 1.5, 1, 1, 0));
    }

    @Test
    void testAddMobDrop_RejectsInvalidCountRange() {
        assertThrows(IllegalArgumentException.class, () ->
            lootService.addMobDrop("minecraft:zombie", "minecraft:diamond", 0.5, 5, 2, 0));
    }

    @Test
    void testRemoveMobDrop_StoresRemoval() {
        lootService.removeMobDrop("minecraft:zombie", "minecraft:rotten_flesh");

        List<LootServiceImpl.RemovalEntry> removals = lootService.getPendingRemovals();
        assertEquals(1, removals.size());
        assertEquals("mob:minecraft:zombie", removals.get(0).targetId());
        assertEquals("minecraft:rotten_flesh", removals.get(0).itemId());
    }

    @Test
    void testAddChestLoot_StoresEntry() {
        lootService.addChestLoot("minecraft:chests/simple_dungeon", "minecraft:diamond", 0.05, 1, 2);

        Map<String, List<LootServiceImpl.ChestLootEntry>> loot = lootService.getPendingChestLoot();
        assertEquals(1, loot.size());
        assertTrue(loot.containsKey("minecraft:chests/simple_dungeon"));

        LootServiceImpl.ChestLootEntry entry = loot.get("minecraft:chests/simple_dungeon").get(0);
        assertEquals("minecraft:diamond", entry.itemId());
        assertEquals(0.05, entry.chance());
    }

    @Test
    void testAddChestLoot_RejectsBlankTableId() {
        assertThrows(IllegalArgumentException.class, () ->
            lootService.addChestLoot("", "minecraft:diamond", 0.1, 1, 1));
    }

    @Test
    void testRemoveChestLoot_StoresRemoval() {
        lootService.removeChestLoot("minecraft:chests/simple_dungeon", "minecraft:iron_ingot");

        List<LootServiceImpl.RemovalEntry> removals = lootService.getPendingRemovals();
        assertEquals(1, removals.size());
        assertEquals("chest:minecraft:chests/simple_dungeon", removals.get(0).targetId());
    }

    @Test
    void testSetBlockDrop_StoresEntry() {
        lootService.setBlockDrop("mymod:ruby_ore", "mymod:ruby", 2, "mymod:ruby_ore");

        Map<String, LootServiceImpl.BlockDropEntry> drops = lootService.getPendingBlockDrops();
        assertEquals(1, drops.size());

        LootServiceImpl.BlockDropEntry entry = drops.get("mymod:ruby_ore");
        assertEquals("mymod:ruby", entry.itemId());
        assertEquals(2, entry.fortuneBonus());
        assertEquals("mymod:ruby_ore", entry.silkTouchDrop());
    }

    @Test
    void testSetBlockDrop_NullSilkTouchDrop() {
        lootService.setBlockDrop("minecraft:stone", "minecraft:cobblestone", 0, null);

        LootServiceImpl.BlockDropEntry entry = lootService.getPendingBlockDrops().get("minecraft:stone");
        assertNull(entry.silkTouchDrop());
    }

    @Test
    void testAddFishingLoot_StoresEntry() {
        lootService.addFishingLoot("mymod:treasure_chest", 0.01, "TREASURE");

        List<LootServiceImpl.FishingLootEntry> entries = lootService.getPendingFishingLoot();
        assertEquals(1, entries.size());
        assertEquals("mymod:treasure_chest", entries.get(0).itemId());
        assertEquals("TREASURE", entries.get(0).category());
    }

    @Test
    void testAddFishingLoot_NormalizesCategory() {
        lootService.addFishingLoot("minecraft:fish", 0.5, "fish");

        assertEquals("FISH", lootService.getPendingFishingLoot().get(0).category());
    }

    @Test
    void testAddFishingLoot_RejectsInvalidCategory() {
        assertThrows(IllegalArgumentException.class, () ->
            lootService.addFishingLoot("minecraft:fish", 0.5, "INVALID"));
    }

    @Test
    void testAddFishingLoot_RejectsInvalidChance() {
        assertThrows(IllegalArgumentException.class, () ->
            lootService.addFishingLoot("minecraft:fish", 2.0, "FISH"));
    }

    @Test
    void testGetModifications_ReturnsCorrectSummary() {
        lootService.addMobDrop("minecraft:zombie", "minecraft:diamond", 0.1, 1, 1, 0);
        lootService.addMobDrop("minecraft:skeleton", "minecraft:arrow", 0.5, 1, 3, 0);
        lootService.addChestLoot("minecraft:chests/simple_dungeon", "minecraft:gold_ingot", 0.2, 1, 4);
        lootService.setBlockDrop("mymod:ore", "mymod:gem", 1, null);
        lootService.addFishingLoot("minecraft:stick", 0.1, "JUNK");
        lootService.removeMobDrop("minecraft:creeper", "minecraft:gunpowder");

        Map<String, Object> summary = lootService.getModifications();
        assertEquals(2, summary.get("mobDrops"));
        assertEquals(1, summary.get("chestLoot"));
        assertEquals(1, summary.get("blockDrops"));
        assertEquals(1, summary.get("fishingLoot"));
        assertEquals(1, summary.get("removals"));
    }

    @Test
    void testClearAll_RemovesEverything() {
        lootService.addMobDrop("minecraft:zombie", "minecraft:diamond", 0.1, 1, 1, 0);
        lootService.addChestLoot("minecraft:chests/simple_dungeon", "minecraft:gold", 0.2, 1, 1);
        lootService.setBlockDrop("mymod:ore", "mymod:gem", 0, null);
        lootService.addFishingLoot("minecraft:stick", 0.1, "JUNK");
        lootService.removeMobDrop("minecraft:creeper", "minecraft:gunpowder");

        lootService.clearAll();

        assertTrue(lootService.getPendingMobDrops().isEmpty());
        assertTrue(lootService.getPendingChestLoot().isEmpty());
        assertTrue(lootService.getPendingBlockDrops().isEmpty());
        assertTrue(lootService.getPendingFishingLoot().isEmpty());
        assertTrue(lootService.getPendingRemovals().isEmpty());
    }

    @Test
    void testGetTable_ReturnsNull() {
        assertNull(lootService.getTable("minecraft:chests/simple_dungeon"));
    }
}

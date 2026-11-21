package com.luatweaker.loot;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootMobRemovalApplyTest {

    private static final String ZOMBIE = "minecraft:zombie";
    private static final String DIAMOND = "minecraft:diamond";
    private static final String IRON = "minecraft:iron_ingot";
    private static final String BONE = "minecraft:bone";
    private static final String ARROW = "minecraft:arrow";
    private static final String SKELETON = "minecraft:skeleton";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private LootServiceImpl lootService;

    @BeforeEach
    void setup() {
        lootService = new LootServiceImpl();
    }

    private List<String> itemIds(String entityId) {
        return lootService.getPendingMobDrops().get(entityId).stream()
            .map(LootServiceImpl.MobDropEntry::itemId)
            .toList();
    }

    @Test
    void removeMobDropReturnsTrue() {
        lootService.addMobDrop(ZOMBIE, DIAMOND, 0.1, 1, 3, 1);

        assertTrue(lootService.removeMobDrop(ZOMBIE, DIAMOND),
            "removing a pending mob drop must report true");
    }

    @Test
    void removeMobDropDropsOnlyMatchingItem() {
        lootService.addMobDrop(ZOMBIE, DIAMOND, 0.1, 1, 3, 1);
        lootService.addMobDrop(ZOMBIE, IRON, 0.5, 1, 1, 0);

        lootService.removeMobDrop(ZOMBIE, DIAMOND);

        assertEquals(List.of(IRON), itemIds(ZOMBIE),
            "only the removed item may disappear; the sibling drop must survive");
    }

    @Test
    void removeMobDropCountsRemovalInSummary() {
        lootService.addMobDrop(ZOMBIE, DIAMOND, 0.1, 1, 3, 1);
        lootService.addMobDrop(ZOMBIE, IRON, 0.5, 1, 1, 0);

        lootService.removeMobDrop(ZOMBIE, DIAMOND);

        assertEquals(List.of(1, 1),
            List.of(lootService.getModifications().get("mobDrops"),
                lootService.getModifications().get("removals")),
            "removing a pending drop must decrement mob drops and add one removal");
    }

    @Test
    void removalBeforeAddAppliesAtApplyTime() {
        lootService.removeMobDrop(SKELETON, BONE);
        lootService.addMobDrop(SKELETON, BONE, 0.5, 1, 1, 0);
        lootService.addMobDrop(SKELETON, ARROW, 0.5, 1, 1, 0);

        lootService.applyRemovalsToPending();

        assertEquals(List.of(ARROW), itemIds(SKELETON),
            "a removal registered before the add must still win at apply time");
    }

    @Test
    void removalKeepsUnrelatedItemOfSameEntity() {
        lootService.addMobDrop(ZOMBIE, DIAMOND, 0.1, 1, 1, 0);
        lootService.addMobDrop(ZOMBIE, BONE, 0.2, 1, 1, 0);

        lootService.removeMobDrop(ZOMBIE, DIAMOND);

        assertEquals(1, lootService.getPendingMobDrops().get(ZOMBIE).size(),
            "the entity's drop list must survive with only the removed item gone");
    }
}

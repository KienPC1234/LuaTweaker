package com.luatweaker.loot;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LootServiceImplTest {

    private static final String ZOMBIE = "minecraft:zombie";
    private static final String DIAMOND = "minecraft:diamond";
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
    void addMobDropStoresEntry() {
        lootService.addMobDrop(ZOMBIE, DIAMOND, 0.1, 1, 3, 1);

        LootServiceImpl.MobDropEntry expected =
            new LootServiceImpl.MobDropEntry(ZOMBIE, DIAMOND, 0.1, 1, 3, 1);

        assertEquals(Map.of(ZOMBIE, List.of(expected)), lootService.getPendingMobDrops(),
            "addMobDrop must store the exact entry under the entity id");
    }

    @Test
    void addMobDropKeepsMultipleDropsPerEntity() {
        lootService.addMobDrop(ZOMBIE, DIAMOND, 0.1, 1, 1, 0);
        lootService.addMobDrop(ZOMBIE, IRON, 0.5, 2, 4, 1);

        assertEquals(2, lootService.getPendingMobDrops().get(ZOMBIE).size(),
            "several drops for one entity must coexist in registration order");
    }

    @Test
    void addMobDropRejectsBlankEntityId() {
        assertThrows(IllegalArgumentException.class,
            () -> lootService.addMobDrop("", DIAMOND, 0.1, 1, 1, 0),
            "a blank entity id must be rejected");
    }

    @Test
    void addMobDropRejectsBlankItemId() {
        assertThrows(IllegalArgumentException.class,
            () -> lootService.addMobDrop(ZOMBIE, "", 0.1, 1, 1, 0),
            "a blank item id must be rejected");
    }

    @Test
    void addMobDropRejectsNegativeChance() {
        assertThrows(IllegalArgumentException.class,
            () -> lootService.addMobDrop(ZOMBIE, DIAMOND, -0.1, 1, 1, 0),
            "a negative drop chance must be rejected");
    }

    @Test
    void addMobDropRejectsChanceAboveOne() {
        assertThrows(IllegalArgumentException.class,
            () -> lootService.addMobDrop(ZOMBIE, DIAMOND, 1.5, 1, 1, 0),
            "a drop chance above 1.0 must be rejected");
    }

    @Test
    void addMobDropRejectsInvalidCountRange() {
        assertThrows(IllegalArgumentException.class,
            () -> lootService.addMobDrop(ZOMBIE, DIAMOND, 0.5, 5, 2, 0),
            "a min count above the max count must be rejected");
    }

    @Test
    void addMobDropRejectsNegativeLootingBonus() {
        assertThrows(IllegalArgumentException.class,
            () -> lootService.addMobDrop(ZOMBIE, DIAMOND, 0.5, 1, 1, -1),
            "a negative looting bonus must be rejected");
    }
}

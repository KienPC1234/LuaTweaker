package com.luatweaker.platform.loot;

import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.loot.LootServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fishing categories must route to their own vanilla subtables. */
class FishingLootTest {

    private static Map<String, String> fishingFiles() {
        LootServiceImpl loot = new LootServiceImpl();
        loot.addFishingLoot("minecraft:cod", 0.5, "FISH");
        loot.addFishingLoot("minecraft:name_tag", 0.05, "TREASURE");

        DatapackServiceImpl datapack = new DatapackServiceImpl();
        new NeoForgeLootProvider(loot, datapack).applyAll();
        return datapack.getVirtualFiles();
    }

    @Test
    void fishEntriesRouteToTheFishSubtable() {
        String fish = fishingFiles().get("data/minecraft/loot_table/gameplay/fishing/fish.json");

        assertTrue(fish != null && fish.contains("\"name\":\"minecraft:cod\""),
                "FISH entries belong in the fish subtable: " + fishingFiles().keySet());
    }

    @Test
    void treasureEntriesRouteToTheTreasureSubtable() {
        String treasure = fishingFiles().get("data/minecraft/loot_table/gameplay/fishing/treasure.json");

        assertTrue(treasure != null && treasure.contains("\"name\":\"minecraft:name_tag\""),
                "TREASURE entries belong in the treasure subtable: " + fishingFiles().keySet());
    }

    @Test
    void treasureSubtableCarriesItsRandomSequence() {
        String treasure = fishingFiles().get("data/minecraft/loot_table/gameplay/fishing/treasure.json");

        assertTrue(treasure.contains("\"random_sequence\":\"minecraft:gameplay/fishing/treasure\""),
                "subtable must carry its vanilla random_sequence: " + treasure);
    }
}

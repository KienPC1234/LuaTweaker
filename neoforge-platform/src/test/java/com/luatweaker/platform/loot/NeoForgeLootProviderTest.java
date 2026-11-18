package com.luatweaker.platform.loot;

import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.loot.LootServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for loot files being written under a namespace the game never reads. */
public class NeoForgeLootProviderTest {

    @Test
    void writesEveryLootTableUnderTheNamespaceTheGameLoads() {
        LootServiceImpl loot = new LootServiceImpl();
        loot.addMobDrop("minecraft:zombie", "minecraft:diamond", 0.1, 1, 1, 0);
        loot.addChestLoot("minecraft:chests/simple_dungeon", "minecraft:diamond", 0.1, 1, 1);
        loot.setBlockDrop("minecraft:stone", "minecraft:cobblestone", 0, null);
        loot.addFishingLoot("minecraft:cod", 0.5, "FISH");

        DatapackServiceImpl datapack = new DatapackServiceImpl();
        new NeoForgeLootProvider(loot, datapack).applyAll();

        Map<String, String> files = datapack.getVirtualFiles();
        assertTrue(files.containsKey("data/minecraft/loot_table/entities/zombie.json"),
                "mob loot must be emitted under data/minecraft: " + files.keySet());
        assertTrue(files.containsKey("data/minecraft/loot_table/chests/simple_dungeon.json"),
                "chest loot must be emitted under data/minecraft: " + files.keySet());
        assertTrue(files.containsKey("data/minecraft/loot_table/blocks/stone.json"),
                "block loot must be emitted under data/minecraft: " + files.keySet());
        assertTrue(files.containsKey("data/minecraft/loot_table/gameplay/fishing/fish.json"),
                "fishing loot must be emitted as the vanilla fish subtable, never by overwriting "
                + "the gameplay/fishing root router: " + files.keySet());
        assertTrue(files.keySet().stream().noneMatch(k -> k.endsWith("gameplay/fishing.json")),
                "the vanilla fishing root router (weights/quality/open-water condition) must stay "
                + "untouched: " + files.keySet());
    }

    @Test
    void fishingCategoriesRouteToTheirOwnSubtables() {
        LootServiceImpl loot = new LootServiceImpl();
        loot.addFishingLoot("minecraft:cod", 0.5, "FISH");
        loot.addFishingLoot("minecraft:name_tag", 0.05, "TREASURE");

        DatapackServiceImpl datapack = new DatapackServiceImpl();
        new NeoForgeLootProvider(loot, datapack).applyAll();

        Map<String, String> files = datapack.getVirtualFiles();
        String fish = files.get("data/minecraft/loot_table/gameplay/fishing/fish.json");
        String treasure = files.get("data/minecraft/loot_table/gameplay/fishing/treasure.json");
        assertTrue(fish != null && fish.contains("\"name\":\"minecraft:cod\""),
                "FISH entries belong in the fish subtable: " + files.keySet());
        assertTrue(treasure != null && treasure.contains("\"name\":\"minecraft:name_tag\""),
                "TREASURE entries belong in the treasure subtable: " + files.keySet());
        assertTrue(treasure.contains("\"random_sequence\":\"minecraft:gameplay/fishing/treasure\""),
                "subtable must carry its vanilla random_sequence: " + treasure);
    }

    @Test
    void keepsANonDefaultNamespace() {
        LootServiceImpl loot = new LootServiceImpl();
        loot.addMobDrop("examplemod:crystal_golem", "examplemod:shard", 1.0, 1, 1, 0);

        DatapackServiceImpl datapack = new DatapackServiceImpl();
        new NeoForgeLootProvider(loot, datapack).applyAll();

        assertTrue(datapack.getVirtualFiles().containsKey(
                        "data/examplemod/loot_table/entities/crystal_golem.json"),
                "a namespaced id must keep its namespace: " + datapack.getVirtualFiles().keySet());
    }
}

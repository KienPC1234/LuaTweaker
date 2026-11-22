package com.luatweaker.common.loot;

import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.loot.LootServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for loot files being written under a namespace the game never reads. */
class MinecraftLootProviderTest {

    private static Map<String, String> appliedFiles(Consumer<LootServiceImpl> setup) {
        LootServiceImpl loot = new LootServiceImpl();
        setup.accept(loot);
        DatapackServiceImpl datapack = new DatapackServiceImpl();
        new MinecraftLootProvider(loot, datapack).applyAll();
        return datapack.getVirtualFiles();
    }

    private static Map<String, String> everyTableFiles() {
        return appliedFiles(loot -> {
            loot.addMobDrop("minecraft:zombie", "minecraft:diamond", 0.1, 1, 1, 0);
            loot.addChestLoot("minecraft:chests/simple_dungeon", "minecraft:diamond", 0.1, 1, 1);
            loot.setBlockDrop("minecraft:stone", "minecraft:cobblestone", 0, null);
            loot.addFishingLoot("minecraft:cod", 0.5, "FISH");
        });
    }

    @Test
    void mobLootUsesTheGameNamespace() {
        assertTrue(everyTableFiles().containsKey("data/minecraft/loot_table/entities/zombie.json"),
                "mob loot must be emitted under data/minecraft: " + everyTableFiles().keySet());
    }

    @Test
    void chestLootUsesTheGameNamespace() {
        assertTrue(everyTableFiles().containsKey("data/minecraft/loot_table/chests/simple_dungeon.json"),
                "chest loot must be emitted under data/minecraft: " + everyTableFiles().keySet());
    }

    @Test
    void blockLootUsesTheGameNamespace() {
        assertTrue(everyTableFiles().containsKey("data/minecraft/loot_table/blocks/stone.json"),
                "block loot must be emitted under data/minecraft: " + everyTableFiles().keySet());
    }

    @Test
    void fishingLootUsesTheFishSubtable() {
        assertTrue(everyTableFiles().containsKey("data/minecraft/loot_table/gameplay/fishing/fish.json"),
                "fishing loot must be emitted as the vanilla fish subtable, never by overwriting "
                        + "the gameplay/fishing root router: " + everyTableFiles().keySet());
    }

    @Test
    void fishingRootRouterStaysUntouched() {
        assertTrue(everyTableFiles().keySet().stream().noneMatch(key -> key.endsWith("gameplay/fishing.json")),
                "the vanilla fishing root router (weights/quality/open-water condition) must stay "
                        + "untouched: " + everyTableFiles().keySet());
    }

    @Test
    void keepsANonDefaultNamespace() {
        Map<String, String> files = appliedFiles(loot ->
                loot.addMobDrop("examplemod:crystal_golem", "examplemod:shard", 1.0, 1, 1, 0));

        assertTrue(files.containsKey("data/examplemod/loot_table/entities/crystal_golem.json"),
                "a namespaced id must keep its namespace: " + files.keySet());
    }
}

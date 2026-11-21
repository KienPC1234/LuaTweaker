package com.luatweaker.loot;

import java.util.List;
import java.util.Map;

/**
 * Applies recorded {@link LootServiceImpl.RemovalEntry removals} to the pending
 * additions so the emitted datapack JSON no longer contains the removed item.
 * Idempotent: safe to call both from the removal methods and again at apply
 * time (which also covers remove-before-add orderings).
 */
final class LootRemovalApplier {

    private static final String MOB_PREFIX = "mob:";
    private static final String CHEST_PREFIX = "chest:";

    private LootRemovalApplier() {}

    static void applyRemovals(List<LootServiceImpl.RemovalEntry> removals,
                              Map<String, List<LootServiceImpl.MobDropEntry>> mobDrops,
                              Map<String, List<LootServiceImpl.ChestLootEntry>> chestLoot) {
        for (LootServiceImpl.RemovalEntry removal : removals) {
            if (removal.targetId().startsWith(MOB_PREFIX)) {
                removeMobEntries(removal, mobDrops);
            } else if (removal.targetId().startsWith(CHEST_PREFIX)) {
                removeChestEntries(removal, chestLoot);
            }
        }
    }

    private static void removeMobEntries(LootServiceImpl.RemovalEntry removal,
                                         Map<String, List<LootServiceImpl.MobDropEntry>> mobDrops) {
        String entityId = removal.targetId().substring(MOB_PREFIX.length());
        List<LootServiceImpl.MobDropEntry> drops = mobDrops.get(entityId);
        if (drops != null) {
            drops.removeIf(drop -> drop.itemId().equals(removal.itemId()));
        }
    }

    private static void removeChestEntries(LootServiceImpl.RemovalEntry removal,
                                           Map<String, List<LootServiceImpl.ChestLootEntry>> chestLoot) {
        String tableId = removal.targetId().substring(CHEST_PREFIX.length());
        List<LootServiceImpl.ChestLootEntry> entries = chestLoot.get(tableId);
        if (entries != null) {
            entries.removeIf(entry -> entry.itemId().equals(removal.itemId()));
        }
    }
}

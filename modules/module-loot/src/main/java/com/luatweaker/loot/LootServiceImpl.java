package com.luatweaker.loot;

import com.luatweaker.api.loot.ILootService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LootServiceImpl implements ILootService {

    public record MobDropEntry(String entityId, String itemId, double chance,
                               int minCount, int maxCount, int lootingBonus) {}

    public record ChestLootEntry(String tableId, String itemId, double chance,
                                 int minCount, int maxCount) {}

    public record BlockDropEntry(String blockId, String itemId, int fortuneBonus,
                                 @Nullable String silkTouchDrop) {}

    public record FishingLootEntry(String itemId, double chance, String category) {}

    public record RemovalEntry(String targetId, String itemId) {}

    private final Map<String, List<MobDropEntry>> pendingMobDrops = new ConcurrentHashMap<>();
    private final Map<String, List<ChestLootEntry>> pendingChestLoot = new ConcurrentHashMap<>();
    private final Map<String, BlockDropEntry> pendingBlockDrops = new ConcurrentHashMap<>();
    private final List<FishingLootEntry> pendingFishingLoot = Collections.synchronizedList(new ArrayList<>());
    private final List<RemovalEntry> pendingRemovals = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void addMobDrop(@NotNull String entityId, @NotNull String itemId,
                           double chance, int minCount, int maxCount, int lootingBonus) {
        if (entityId.isBlank() || itemId.isBlank()) {
            throw new IllegalArgumentException("entityId and itemId must not be blank");
        }
        if (chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("chance must be between 0.0 and 1.0, got: " + chance);
        }
        if (minCount < 0 || maxCount < minCount) {
            throw new IllegalArgumentException("Invalid count range: min=" + minCount + ", max=" + maxCount);
        }

        pendingMobDrops.computeIfAbsent(entityId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new MobDropEntry(entityId, itemId, chance, minCount, maxCount, lootingBonus));
    }

    @Override
    public boolean removeMobDrop(@NotNull String entityId, @NotNull String itemId) {
        if (entityId.isBlank() || itemId.isBlank()) {
            throw new IllegalArgumentException("entityId and itemId must not be blank");
        }
        pendingRemovals.add(new RemovalEntry("mob:" + entityId, itemId));
        return true;
    }

    @Override
    public void addChestLoot(@NotNull String tableId, @NotNull String itemId,
                             double chance, int minCount, int maxCount) {
        if (tableId.isBlank() || itemId.isBlank()) {
            throw new IllegalArgumentException("tableId and itemId must not be blank");
        }
        if (chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("chance must be between 0.0 and 1.0, got: " + chance);
        }
        if (minCount < 0 || maxCount < minCount) {
            throw new IllegalArgumentException("Invalid count range: min=" + minCount + ", max=" + maxCount);
        }

        pendingChestLoot.computeIfAbsent(tableId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new ChestLootEntry(tableId, itemId, chance, minCount, maxCount));
    }

    @Override
    public boolean removeChestLoot(@NotNull String tableId, @NotNull String itemId) {
        if (tableId.isBlank() || itemId.isBlank()) {
            throw new IllegalArgumentException("tableId and itemId must not be blank");
        }
        pendingRemovals.add(new RemovalEntry("chest:" + tableId, itemId));
        return true;
    }

    @Override
    public void setBlockDrop(@NotNull String blockId, @NotNull String itemId,
                             int fortuneBonus, @Nullable String silkTouchDrop) {
        if (blockId.isBlank() || itemId.isBlank()) {
            throw new IllegalArgumentException("blockId and itemId must not be blank");
        }
        pendingBlockDrops.put(blockId, new BlockDropEntry(blockId, itemId, fortuneBonus, silkTouchDrop));
    }

    @Override
    public void addFishingLoot(@NotNull String itemId, double chance, @NotNull String category) {
        if (itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("chance must be between 0.0 and 1.0, got: " + chance);
        }
        String normalizedCategory = category.toUpperCase(Locale.ROOT);
        if (!normalizedCategory.equals("FISH") && !normalizedCategory.equals("JUNK")
                && !normalizedCategory.equals("TREASURE")) {
            throw new IllegalArgumentException("category must be FISH, JUNK, or TREASURE, got: " + category);
        }

        pendingFishingLoot.add(new FishingLootEntry(itemId, chance, normalizedCategory));
    }

    @Override
    @Nullable
    public Object getTable(@NotNull String tableId) {
        return null;
    }

    @Override
    @NotNull
    public Map<String, Object> getModifications() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mobDrops", pendingMobDrops.values().stream().mapToInt(List::size).sum());
        summary.put("chestLoot", pendingChestLoot.values().stream().mapToInt(List::size).sum());
        summary.put("blockDrops", pendingBlockDrops.size());
        summary.put("fishingLoot", pendingFishingLoot.size());
        summary.put("removals", pendingRemovals.size());
        return summary;
    }

    @Override
    public void clearAll() {
        pendingMobDrops.clear();
        pendingChestLoot.clear();
        pendingBlockDrops.clear();
        pendingFishingLoot.clear();
        pendingRemovals.clear();
    }

    public Map<String, List<MobDropEntry>> getPendingMobDrops() {
        return Collections.unmodifiableMap(pendingMobDrops);
    }

    public Map<String, List<ChestLootEntry>> getPendingChestLoot() {
        return Collections.unmodifiableMap(pendingChestLoot);
    }

    public Map<String, BlockDropEntry> getPendingBlockDrops() {
        return Collections.unmodifiableMap(pendingBlockDrops);
    }

    public List<FishingLootEntry> getPendingFishingLoot() {
        return Collections.unmodifiableList(pendingFishingLoot);
    }

    public List<RemovalEntry> getPendingRemovals() {
        return Collections.unmodifiableList(pendingRemovals);
    }
}

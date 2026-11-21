package com.luatweaker.loot;

import com.luatweaker.api.loot.ILootService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private final List<FishingLootEntry> pendingFishingLoot = new CopyOnWriteArrayList<>();
    private final List<RemovalEntry> pendingRemovals = new CopyOnWriteArrayList<>();

    @Override
    public void addMobDrop(@NotNull String entityId, @NotNull String itemId,
                           double chance, int minCount, int maxCount, int lootingBonus) {
        LootValidators.requireNonBlankIds(entityId, itemId, "entityId and itemId");
        LootValidators.requireChance(chance);
        LootValidators.requireCountRange(minCount, maxCount);
        if (lootingBonus < 0) {
            throw new IllegalArgumentException("lootingBonus must not be negative, got: " + lootingBonus);
        }

        pendingMobDrops.computeIfAbsent(entityId, k -> new CopyOnWriteArrayList<>())
                .add(new MobDropEntry(entityId, itemId, chance, minCount, maxCount, lootingBonus));
    }

    @Override
    public boolean removeMobDrop(@NotNull String entityId, @NotNull String itemId) {
        LootValidators.requireNonBlankIds(entityId, itemId, "entityId and itemId");
        pendingRemovals.add(new RemovalEntry("mob:" + entityId, itemId));
        applyRemovalsToPending();
        return true;
    }

    @Override
    public void addChestLoot(@NotNull String tableId, @NotNull String itemId,
                             double chance, int minCount, int maxCount) {
        LootValidators.requireNonBlankIds(tableId, itemId, "tableId and itemId");
        LootValidators.requireChance(chance);
        LootValidators.requireCountRange(minCount, maxCount);

        pendingChestLoot.computeIfAbsent(tableId, k -> new CopyOnWriteArrayList<>())
                .add(new ChestLootEntry(tableId, itemId, chance, minCount, maxCount));
    }

    @Override
    public boolean removeChestLoot(@NotNull String tableId, @NotNull String itemId) {
        LootValidators.requireNonBlankIds(tableId, itemId, "tableId and itemId");
        pendingRemovals.add(new RemovalEntry("chest:" + tableId, itemId));
        applyRemovalsToPending();
        return true;
    }

    @Override
    public void setBlockDrop(@NotNull String blockId, @NotNull String itemId,
                             int fortuneBonus, @Nullable String silkTouchDrop) {
        LootValidators.requireNonBlankIds(blockId, itemId, "blockId and itemId");
        pendingBlockDrops.put(blockId, new BlockDropEntry(blockId, itemId, fortuneBonus, silkTouchDrop));
    }

    @Override
    public void addFishingLoot(@NotNull String itemId, double chance, @NotNull String category) {
        if (itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        LootValidators.requireChance(chance);
        String normalizedCategory = LootValidators.requireFishingCategory(category);

        pendingFishingLoot.add(new FishingLootEntry(itemId, chance, normalizedCategory));
    }

    /**
     * Applies every recorded removal to the pending additions so the emitted datapack JSON no
     * longer contains the removed item. Idempotent: safe to call both from the removal methods and
     * again at apply time (which also covers remove-before-add orderings).
     *
     * <p>Limitation: this only removes entries registered through this API. Removing a vanilla
     * drop that was never added here has no effect on the vanilla table; reading/rewriting the
     * original table would require a verified resource-manager PAL capability.
     */
    public void applyRemovalsToPending() {
        LootRemovalApplier.applyRemovals(pendingRemovals, pendingMobDrops, pendingChestLoot);
    }

    @Override
    @NotNull
    public Map<String, Object> getModifications() {
        return Map.of("mobDrops", pendingMobDrops.values().stream().mapToInt(List::size).sum(),
                "chestLoot", pendingChestLoot.values().stream().mapToInt(List::size).sum(),
                "blockDrops", pendingBlockDrops.size(),
                "fishingLoot", pendingFishingLoot.size(),
                "removals", pendingRemovals.size());
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

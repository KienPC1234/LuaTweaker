package com.luatweaker.neoforge.loot;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.loot.LootServiceImpl;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Real-time runtime loot interceptor.
 * Directly intercepts LivingDropsEvent and BlockDropsEvent to guarantee that
 * Loot:AddMobDrop, Loot:RemoveMobDrop, and Loot:SetBlockDrop modify actual
 * vanilla and modded entity/block drops in the world without requiring datapack reloading.
 */
public final class NeoForgeRuntimeLootHandler {

    private static final Random RANDOM = new Random();
    private static final String MOB_TARGET_PREFIX = "mob:";
    private static final String BLOCK_TARGET_PREFIX = "block:";
    /** Drops spawn at the block centre; a block occupies exactly one unit. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    private static final int SINGLE_DROP_COUNT = 1;

    private NeoForgeRuntimeLootHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingDropsEvent.class, NeoForgeRuntimeLootHandler::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockDropsEvent.class, NeoForgeRuntimeLootHandler::onBlockDrops);
    }

    private static @Nullable LootServiceImpl resolveLootService() {
        Object serviceObj = LuaServiceRegistry.get("LootServiceImpl");
        return serviceObj instanceof LootServiceImpl lootService ? lootService : null;
    }

    private static void onLivingDrops(LivingDropsEvent event) {
        LootServiceImpl lootService = resolveLootService();
        if (lootService == null) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        String entityId = entityId(entity);
        if (entityId == null) {
            return;
        }

        Collection<ItemEntity> drops = event.getDrops();
        removeMatchingDrops(drops, MOB_TARGET_PREFIX + entityId, lootService.getPendingRemovals());
        addMobDrops(drops, entity, lootService.getPendingMobDrops().get(entityId));
    }

    private static @Nullable String entityId(@NotNull LivingEntity entity) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key == null ? null : key.toString();
    }

    private static void onBlockDrops(BlockDropsEvent event) {
        LootServiceImpl lootService = resolveLootService();
        if (lootService == null) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            return;
        }
        ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
        if (blockKey == null) {
            return;
        }
        String blockId = blockKey.toString();

        List<ItemEntity> drops = event.getDrops();
        removeMatchingDrops(drops, BLOCK_TARGET_PREFIX + blockId, lootService.getPendingRemovals());

        LootServiceImpl.BlockDropEntry override = lootService.getPendingBlockDrops().get(blockId);
        if (override == null) {
            return;
        }
        drops.clear();
        BlockPos pos = event.getPos();
        ItemEntity drop = createDrop(event.getLevel(),
                pos.getX() + BLOCK_CENTER_OFFSET,
                pos.getY() + BLOCK_CENTER_OFFSET,
                pos.getZ() + BLOCK_CENTER_OFFSET,
                override.itemId(), SINGLE_DROP_COUNT);
        if (drop != null) {
            drops.add(drop);
        }
    }

    /**
     * Removes every already-dropped item that a {@code Loot:RemoveMobDrop}/{@code Loot:RemoveBlockDrop}
     * registration targets.
     */
    private static void removeMatchingDrops(@NotNull Collection<ItemEntity> drops, @NotNull String targetId,
                                            @NotNull List<LootServiceImpl.RemovalEntry> removals) {
        if (removals.isEmpty()) {
            return;
        }
        drops.removeIf(itemEntity -> matchesRemoval(itemEntity, targetId, removals));
    }

    private static boolean matchesRemoval(@NotNull ItemEntity itemEntity, @NotNull String targetId,
                                          @NotNull List<LootServiceImpl.RemovalEntry> removals) {
        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
        if (itemKey == null) {
            return false;
        }
        String itemId = itemKey.toString();
        return removals.stream()
                .anyMatch(removal -> removal.targetId().equals(targetId) && removal.itemId().equals(itemId));
    }

    /**
     * Rolls the Lua-registered drops for one mob and spawns the ones that pass their chance.
     * The chance roll happens before the count roll so a script that only sets a chance keeps
     * consuming the shared {@link Random} in the same order as before.
     */
    private static void addMobDrops(@NotNull Collection<ItemEntity> drops, @NotNull LivingEntity entity,
                                    @Nullable List<LootServiceImpl.MobDropEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (LootServiceImpl.MobDropEntry entry : entries) {
            if (RANDOM.nextDouble() > entry.chance()) {
                continue;
            }
            ItemEntity drop = createDrop(entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                    entry.itemId(), rollCount(entry));
            if (drop != null) {
                drops.add(drop);
            }
        }
    }

    private static int rollCount(@NotNull LootServiceImpl.MobDropEntry entry) {
        int count = entry.minCount();
        if (entry.maxCount() > entry.minCount()) {
            count += RANDOM.nextInt(entry.maxCount() - entry.minCount() + 1);
        }
        return count;
    }

    /**
     * Materialises one item entity, or {@code null} when the id is not a registered item.
     * An unparseable or unknown id is logged instead of silently aborting the whole pass.
     */
    private static @Nullable ItemEntity createDrop(@NotNull Level level, double x, double y, double z,
                                                   @NotNull String itemId, int count) {
        if (count <= 0) {
            return null;
        }
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item == null || item == Items.AIR) {
                return null;
            }
            ItemEntity drop = new ItemEntity(level, x, y, z, new ItemStack(item, count));
            drop.setDefaultPickUpDelay();
            return drop;
        } catch (ResourceLocationException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Loot] Skipped drop for invalid item id '" + itemId + "': " + e.getMessage());
            return null;
        }
    }
}

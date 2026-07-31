package com.luatweaker.api.pal;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.api.objects.IRecipe;
import com.luatweaker.api.objects.IWorldBlock;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Set;

public interface IPlatformHelper {
    default IItem createItem(String itemId, int count) { return null; }
    default boolean itemExists(String itemId) { return false; }
    default boolean blockExists(String blockId) { return false; }
    default boolean fluidExists(String fluidId) { return false; }
    default boolean tagExists(String tagId) { return false; }
    default boolean isModLoaded(String modId) { return false; }
    default boolean isClient() { return true; }
    default boolean isDedicatedServer() { return false; }
    default String getPlatformName() { return "Unknown"; }
    default Set<String> getSupportedMobParents() { return Set.of(); }
    default List<IRecipe> getAllRecipes() { return java.util.Collections.emptyList(); }

    default void addCustomGoal(@NotNull IEntity entity, int priority, @NotNull ILuaTable goalTable, @NotNull ILuaEngine engine, boolean isTargetSelector) {}
    default void removeCustomGoal(@NotNull IEntity entity, @NotNull ILuaTable goalTable) {}
    default void clearCustomGoals(@NotNull IEntity entity) {}

    default void addMeleeAttackGoal(@NotNull IEntity entity, int priority, double speed, boolean pauseWhenMobIdle) {}
    default void addHurtByTargetGoal(@NotNull IEntity entity, int priority) {}
    default void addNearestAttackableTargetGoal(@NotNull IEntity entity, int priority, @NotNull String targetType) {}
    default void shootProjectile(@NotNull IEntity shooter, @NotNull String projectileType, double speed, double inaccuracy) {}
    default void shootProjectileAt(@NotNull IEntity shooter, @NotNull String projectileType, @NotNull IEntity target, double speed) {}
    default void playAnimation(@NotNull IEntity entity, @NotNull String animName, double speed, double transition) {}
    default boolean performBlockBreak(@NotNull IEntity actor, int x, int y, int z) { return false; }
    default boolean performBlockPlace(@NotNull IEntity actor, int x, int y, int z, @NotNull String blockId) { return false; }
    default boolean performBlockUse(@NotNull IEntity actor, int x, int y, int z) { return false; }
    default boolean performItemUse(@NotNull IEntity actor, int slot) { return false; }
    default void lookAt(@NotNull IEntity actor, double x, double y, double z) {}
    default void lookAt(@NotNull IEntity actor, @NotNull IEntity target) {}
    default boolean moveInventoryItem(@NotNull IEntity actor, int fromSlot, int toSlot) { return false; }
    default boolean dropInventoryItem(@NotNull IEntity actor, int slot, int count) { return false; }
    @NotNull
    default List<IWorldBlock> getNearbyBlocks(@NotNull IEntity entity, int radius) { return List.of(); }
    @NotNull
    default List<ILocatedItem> getInventoryItems(@NotNull IEntity entity) { return List.of(); }

    @Nullable
    default com.luatweaker.api.interaction.IInteractableBlock getInteractableBlock(@NotNull String dimension, int x, int y, int z) { return null; }
    @Nullable
    default com.luatweaker.api.interaction.IInteractableItem getInteractableItem(@NotNull Object entityOrBlock, int slot) { return null; }
    @Nullable
    default com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(@NotNull String uuid) { return null; }
    @Nullable
    default com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(@NotNull Object rawEntity) { return null; }

    @Nullable
    default com.luatweaker.api.entity.IPlayer getPlayer(@NotNull String uuid) { return null; }

    default java.util.List<com.luatweaker.api.entity.IPlayer> getAllPlayers() { return java.util.List.of(); }

    @Nullable
    default Object spawnEntity(@NotNull String entityId, double x, double y, double z) { return null; }

    default java.io.File getStorageDirectory() { return new java.io.File("luatweaker/storage"); }
    default void sendPayloadPacket(String playerUuid, String channelName, String dataJson) {}
    default void broadcastPayloadPacket(String channelName, String dataJson) {}
    default void sendPayloadPacketToServer(String channelName, String dataJson) {}
}

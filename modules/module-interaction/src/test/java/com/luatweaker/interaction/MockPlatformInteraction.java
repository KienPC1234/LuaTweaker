package com.luatweaker.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.interaction.IInteractableBlock;
import com.luatweaker.api.interaction.IInteractableEntity;
import com.luatweaker.api.interaction.IInteractableItem;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.api.objects.IWorldBlock;
import com.luatweaker.api.pal.IPlatformInteraction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Safe no-op defaults for {@link IPlatformInteraction}, so a test double only overrides the calls
 * it actually exercises.
 *
 * <p>The PAL surface requires 27 primitives, so the defaults are grouped by concern instead of
 * living in one 27-method interface: {@link MockPlatformEntityDefaults} (entity actions),
 * {@link MockPlatformWorldDefaults} (inventory/world edits) and {@link MockPlatformLookupDefaults}
 * (block/entity lookups). Test doubles implement {@link MockPlatformInteraction} and inherit all
 * three groups.
 */
interface MockPlatformInteraction extends MockPlatformEntityDefaults, MockPlatformWorldDefaults,
        MockPlatformLookupDefaults {
}

/** Entity-facing primitives that tests rarely exercise. */
interface MockPlatformEntityDefaults extends IPlatformInteraction {

    @Override
    default void shootProjectile(@NotNull IEntity shooter, @NotNull String projectileType,
                                 double speed, double inaccuracy) {
    }

    @Nullable
    @Override
    default IEntity shootProjectileAt(@NotNull IEntity shooter, @NotNull String projectileType,
                                      @NotNull IEntity target, double speed) {
        return null;
    }

    @Override
    default void playAnimation(@NotNull IEntity entity, @NotNull String animName, double speed,
                               double transition) {
    }

    @Override
    default boolean performBlockBreak(@NotNull IEntity actor, int x, int y, int z) {
        return false;
    }

    @Override
    default boolean performBlockPlace(@NotNull IEntity actor, int x, int y, int z,
                                      @NotNull String blockId) {
        return false;
    }

    @Override
    default boolean performBlockUse(@NotNull IEntity actor, int x, int y, int z) {
        return false;
    }

    @Override
    default boolean performItemUse(@NotNull IEntity actor, int slot) {
        return false;
    }

    @Override
    default void lookAt(@NotNull IEntity actor, double x, double y, double z) {
    }

    @Override
    default void lookAt(@NotNull IEntity actor, @NotNull IEntity target) {
    }
}

/** Inventory and world-edit primitives that tests rarely exercise. */
interface MockPlatformWorldDefaults extends IPlatformInteraction {

    @Override
    default boolean moveInventoryItem(@NotNull IEntity actor, int fromSlot, int toSlot) {
        return false;
    }

    @Override
    default boolean dropInventoryItem(@NotNull IEntity actor, int slot, int count) {
        return false;
    }

    @Override
    default boolean ejectContainerItem(@NotNull String dimension, int x, int y, int z, int slot,
                                       int count) {
        return false;
    }

    @Override
    default long fillBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2,
                            int z2, @NotNull String blockId,
                            @Nullable Map<String, Object> properties) {
        return 0;
    }

    @Override
    default long replaceBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2,
                               int z2, @NotNull String fromId, @NotNull String toId) {
        return 0;
    }

    @Override
    default boolean placeStructure(@NotNull String dimension, @NotNull String templateId, int x,
                                   int y, int z, int rotationDegrees) {
        return false;
    }

    @Override
    default boolean executeCommand(@NotNull String command) {
        return false;
    }
}

/** Block/entity lookup primitives that tests rarely exercise. */
interface MockPlatformLookupDefaults extends IPlatformInteraction {

    @NotNull
    @Override
    default List<IWorldBlock> getNearbyBlocks(@NotNull IEntity entity, int radius) {
        return List.of();
    }

    @NotNull
    @Override
    default List<ILocatedItem> getInventoryItems(@NotNull IEntity entity) {
        return List.of();
    }

    @NotNull
    @Override
    default List<IEntity> getNearbyEntities(@NotNull IEntity center, double radius) {
        return List.of();
    }

    @Nullable
    @Override
    default IInteractableBlock getInteractableBlock(@NotNull String dimension, int x, int y, int z) {
        return null;
    }

    @Nullable
    @Override
    default IInteractableItem getInteractableItem(@NotNull Object entityOrBlock, int slot) {
        return null;
    }

    @Nullable
    @Override
    default IInteractableEntity getInteractableEntity(@NotNull String uuid) {
        return null;
    }

    @Nullable
    @Override
    default IInteractableEntity getInteractableEntity(@NotNull Object rawEntity) {
        return null;
    }

    @Nullable
    @Override
    default Map<String, Object> getBlockState(@NotNull String dimension, int x, int y, int z) {
        return null;
    }

    @Override
    default boolean setBlockState(@NotNull String dimension, int x, int y, int z,
                                  @NotNull String blockId, @Nullable Map<String, Object> properties) {
        return false;
    }

    @Nullable
    @Override
    default Map<String, Object> getBlockEntityData(@NotNull String dimension, int x, int y, int z) {
        return null;
    }

    @Override
    default boolean setBlockEntityData(@NotNull String dimension, int x, int y, int z,
                                       @NotNull Map<String, Object> data) {
        return false;
    }
}

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
 * Reusable base mock for IPlatformInteraction in module-interaction tests.
 * Provides safe no-op defaults so individual tests only override the primitives they exercise.
 */
public class BaseMockPlatformInteraction implements IPlatformInteraction {

    @Override public void shootProjectile(@NotNull IEntity shooter, @NotNull String projectileType, double speed, double inaccuracy) {}
    @Nullable @Override public IEntity shootProjectileAt(@NotNull IEntity shooter, @NotNull String projectileType, @NotNull IEntity target, double speed) { return null; }
    @Override public void playAnimation(@NotNull IEntity entity, @NotNull String animName, double speed, double transition) {}
    @Override public boolean performBlockBreak(@NotNull IEntity actor, int x, int y, int z) { return false; }
    @Override public boolean performBlockPlace(@NotNull IEntity actor, int x, int y, int z, @NotNull String blockId) { return false; }
    @Override public boolean performBlockUse(@NotNull IEntity actor, int x, int y, int z) { return false; }
    @Override public boolean performItemUse(@NotNull IEntity actor, int slot) { return false; }
    @Override public void lookAt(@NotNull IEntity actor, double x, double y, double z) {}
    @Override public void lookAt(@NotNull IEntity actor, @NotNull IEntity target) {}
    @Override public boolean moveInventoryItem(@NotNull IEntity actor, int fromSlot, int toSlot) { return false; }
    @Override public boolean dropInventoryItem(@NotNull IEntity actor, int slot, int count) { return false; }
    @NotNull @Override public List<IWorldBlock> getNearbyBlocks(@NotNull IEntity entity, int radius) { return List.of(); }
    @NotNull @Override public List<ILocatedItem> getInventoryItems(@NotNull IEntity entity) { return List.of(); }
    @NotNull @Override public List<IEntity> getNearbyEntities(@NotNull IEntity center, double radius) { return List.of(); }
    @Nullable @Override public IInteractableBlock getInteractableBlock(@NotNull String dimension, int x, int y, int z) { return null; }
    @Nullable @Override public IInteractableItem getInteractableItem(@NotNull Object entityOrBlock, int slot) { return null; }
    @Nullable @Override public IInteractableEntity getInteractableEntity(@NotNull String uuid) { return null; }
    @Nullable @Override public IInteractableEntity getInteractableEntity(@NotNull Object rawEntity) { return null; }
    @Nullable @Override public Map<String, Object> getBlockState(@NotNull String dimension, int x, int y, int z) { return null; }
    @Override public boolean setBlockState(@NotNull String dimension, int x, int y, int z, @NotNull String blockId, @Nullable Map<String, Object> properties) { return false; }
    @Nullable @Override public Map<String, Object> getBlockEntityData(@NotNull String dimension, int x, int y, int z) { return null; }
    @Override public boolean setBlockEntityData(@NotNull String dimension, int x, int y, int z, @NotNull Map<String, Object> data) { return false; }
    @Override public boolean ejectContainerItem(@NotNull String dimension, int x, int y, int z, int slot, int count) { return false; }
    @Override public long fillBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2, @NotNull String blockId, @Nullable Map<String, Object> properties) { return 0; }
    @Override public long replaceBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2, @NotNull String fromId, @NotNull String toId) { return 0; }
    @Override public boolean placeStructure(@NotNull String dimension, @NotNull String templateId, int x, int y, int z, int rotationDegrees) { return false; }
    @Override public boolean executeCommand(@NotNull String command) { return false; }
}

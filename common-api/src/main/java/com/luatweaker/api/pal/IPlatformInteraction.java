package com.luatweaker.api.pal;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.api.objects.IWorldBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public interface IPlatformInteraction {
    void shootProjectile(@NotNull IEntity shooter, @NotNull String projectileType, double speed, double inaccuracy);

    /** Fires a projectile toward a target entity and returns the spawned projectile (null if it could not fire). */
    @Nullable
    IEntity shootProjectileAt(@NotNull IEntity shooter, @NotNull String projectileType, @NotNull IEntity target, double speed);
    void playAnimation(@NotNull IEntity entity, @NotNull String animName, double speed, double transition);
    
    boolean performBlockBreak(@NotNull IEntity actor, int x, int y, int z);
    boolean performBlockPlace(@NotNull IEntity actor, int x, int y, int z, @NotNull String blockId);
    boolean performBlockUse(@NotNull IEntity actor, int x, int y, int z);
    boolean performItemUse(@NotNull IEntity actor, int slot);
    
    void lookAt(@NotNull IEntity actor, double x, double y, double z);
    void lookAt(@NotNull IEntity actor, @NotNull IEntity target);
    
    boolean moveInventoryItem(@NotNull IEntity actor, int fromSlot, int toSlot);
    boolean dropInventoryItem(@NotNull IEntity actor, int slot, int count);
    
    @NotNull
    List<IWorldBlock> getNearbyBlocks(@NotNull IEntity entity, int radius);
    @NotNull
    List<ILocatedItem> getInventoryItems(@NotNull IEntity entity);

    @NotNull
    List<IEntity> getNearbyEntities(@NotNull IEntity center, double radius);

    @Nullable
    com.luatweaker.api.interaction.IInteractableBlock getInteractableBlock(@NotNull String dimension, int x, int y, int z);
    @Nullable
    com.luatweaker.api.interaction.IInteractableItem getInteractableItem(@NotNull Object entityOrBlock, int slot);
    @Nullable
    com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(@NotNull String uuid);
    @Nullable
    com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(@NotNull Object rawEntity);
}

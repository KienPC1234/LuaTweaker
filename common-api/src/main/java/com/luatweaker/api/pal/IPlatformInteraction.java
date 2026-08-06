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

    /** Reads a block state as {Id, Properties:{name=value}} (null if dimension/pos invalid). */
    @Nullable
    java.util.Map<String, Object> getBlockState(@NotNull String dimension, int x, int y, int z);

    /** Replaces the block state at pos, applying the given property values (e.g. open=true). */
    boolean setBlockState(@NotNull String dimension, int x, int y, int z, @NotNull String blockId,
                          @Nullable java.util.Map<String, Object> properties);

    /** Reads the block entity's NBT (save-with-full-metadata) as a nested Lua-friendly map. */
    @Nullable
    java.util.Map<String, Object> getBlockEntityData(@NotNull String dimension, int x, int y, int z);

    /** Writes the given NBT map onto the block entity (empty map = default save format). */
    boolean setBlockEntityData(@NotNull String dimension, int x, int y, int z,
                               @NotNull java.util.Map<String, Object> data);

    /** Ejects 'count' items from a container block entity slot into the world (false if no container/slot). */
    boolean ejectContainerItem(@NotNull String dimension, int x, int y, int z, int slot, int count);

    /**
     * Fills the axis-aligned box between the two corners (inclusive) with the
     * given block state. The platform enforces a hard volume cap to protect the
     * server from lag-bomb scripts; the calling Lua mod should additionally
     * enforce its own config-driven limit for user feedback.
     *
     * @return the number of blocks actually set (>= 0), or -1 when the dimension
     *         or block id is invalid or the volume exceeds the platform cap
     */
    long fillBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                    @NotNull String blockId, @Nullable java.util.Map<String, Object> properties);

    /**
     * Replaces every block of the given id inside the box with another block id.
     *
     * @return the number of blocks replaced (>= 0), or -1 on invalid input
     */
    long replaceBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                       @NotNull String fromId, @NotNull String toId);

    /**
     * Loads an NBT structure template (from the datapack, e.g.
     * {@code data/mymod/structures/shrine.nbt} -> {@code mymod:shrine}) and
     * places it in the world with the given rotation (0/90/180/270). The mod
     * author decides when and where - this is a low-level tool, not a
     * structure placement system.
     *
     * @return true when the template was found and placed
     */
    boolean placeStructure(@NotNull String dimension, @NotNull String templateId,
                           int x, int y, int z, int rotationDegrees);

    /** Executes a command as if typed in the server console (false when no server is running). */
    boolean executeCommand(@NotNull String command);
}

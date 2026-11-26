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
     * {@code data/mymod/structure/shrine.nbt} -> {@code mymod:shrine}) and
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

    @NotNull
    default com.luatweaker.api.spatial.RaycastResult raycast(
            @NotNull String dimension,
            double originX, double originY, double originZ,
            double dirX, double dirY, double dirZ,
            @NotNull com.luatweaker.api.spatial.RaycastParams params) {
        return com.luatweaker.api.spatial.RaycastResult.miss(
                originX + dirX * params.maxDistance(),
                originY + dirY * params.maxDistance(),
                originZ + dirZ * params.maxDistance());
    }

    default void spawnParticle(@NotNull String dimension, @NotNull String particleId,
                               double x, double y, double z,
                               double vx, double vy, double vz,
                               int count, double speed) {}

    /**
     * Spawns a particle that requires options (dust/block/item). Platforms that do not
     * implement option particles fail loudly instead of silently dropping the request.
     */
    default void spawnParticleOptions(@NotNull String dimension, @NotNull String particleId,
                                      @NotNull com.luatweaker.api.particle.ParticleSpawn spawn,
                                      @NotNull com.luatweaker.api.particle.ParticleOptionsSpec options) {
        throw new UnsupportedOperationException(
                "This platform does not support particle options (requested '" + particleId + "')");
    }

    /**
     * Plays a sound for every player in the dimension.
     *
     * <p>Default is a no-op for platforms/test doubles without audio; the NeoForge
     * implementation overrides it and reports unknown sounds.</p>
     *
     * @param source Minecraft sound category (PLAYERS, BLOCKS, HOSTILE, ...)
     */
    default void playSound(@NotNull String dimension, @NotNull String soundId,
                           double x, double y, double z,
                           float volume, float pitch, @NotNull String source) {
    }

    /**
     * Stops every instance of a sound for every player in the dimension.
     *
     * @return whether the stop packet was delivered to at least one player
     */
    default boolean stopSound(@NotNull String dimension, @NotNull String soundId, @NotNull String source) {
        return false;
    }

    default int getRedstoneSignal(@NotNull String dimension, int x, int y, int z, @Nullable String direction) {
        return 0;
    }

    default boolean checkPattern(@NotNull String dimension, int originX, int originY, int originZ,
                                @NotNull List<java.util.Map<String, Object>> pattern) {
        return false;
    }

    default int transferEnergy(@NotNull String dimension, int fromX, int fromY, int fromZ,
                              int toX, int toY, int toZ, int maxAmount, boolean simulate) {
        return 0;
    }

    default int transferFluid(@NotNull String dimension, int fromX, int fromY, int fromZ,
                             int toX, int toY, int toZ, int maxAmount, boolean simulate) {
        return 0;
    }

    default int transferItem(@NotNull String dimension, int fromX, int fromY, int fromZ, int fromSlot,
                            int toX, int toY, int toZ, int toSlot, int maxCount, boolean simulate) {
        return 0;
    }

    default @NotNull java.util.Map<String, Object> validateCuboid(
            @NotNull String dimension,
            int x1, int y1, int z1,
            int x2, int y2, int z2,
            @NotNull java.util.Map<String, Object> spec) {
        return java.util.Map.of("valid", false, "error", "Not implemented on platform");
    }

    default int setBlockStatesInBox(
            @NotNull String dimension,
            int x1, int y1, int z1,
            int x2, int y2, int z2,
            @Nullable String filterBlockId,
            @NotNull java.util.Map<String, Object> properties) {
        return 0;
    }

    @NotNull
    default List<java.util.Map<String, Object>> findBlocks(
            @NotNull String dimension,
            int x, int y, int z,
            int radius,
            @NotNull String filter,
            int maxResults) {
        return List.of();
    }

    default boolean setEnergy(@NotNull String dimension, int x, int y, int z, int amount) {
        return false;
    }

    default int produceEnergy(@NotNull String dimension, int x, int y, int z, int amount) {
        return 0;
    }

    default boolean setProgress(@NotNull String dimension, int x, int y, int z, float progress) {
        return false;
    }

    default float getProgress(@NotNull String dimension, int x, int y, int z) {
        return 0.0f;
    }

    /**
     * Triggers a skeletal animation via GeckoLib on an Entity, BlockEntity, or Item.
     *
     * @param target The target instance (IEntity, IInteractableBlock, IInteractableItem, BlockPos/BlockEntity)
     * @param controllerName Name of the animation controller (default "main")
     * @param animName Name of the animation from the .animation.json file
     * @param speed Playback speed multiplier (1.0 = normal)
     * @param loop True to loop the animation continuously, false to play once
     * @return True if the animation was successfully dispatched, false otherwise
     */
    default boolean triggerGeoAnimation(@NotNull Object target, @NotNull String controllerName, @NotNull String animName, double speed, boolean loop) {
        return false;
    }

    /**
     * Stops a triggered skeletal animation via GeckoLib on an Entity, BlockEntity, or Item.
     *
     * @param target The target instance (IEntity, IInteractableBlock, IInteractableItem, BlockPos/BlockEntity)
     * @param controllerName Name of the animation controller (default "controller")
     * @param animName Name of the animation from the .animation.json file
     * @return True if the stop was successfully dispatched, false otherwise
     */
    default boolean stopGeoAnimation(@NotNull Object target, @NotNull String controllerName, @NotNull String animName) {
        return false;
    }

    default long getDayTime(@NotNull String dimension) {
        return -1L;
    }

    default boolean setDayTime(@NotNull String dimension, long dayTime) {
        return false;
    }

    default long getGameTime(@NotNull String dimension) {
        return -1L;
    }

    @Nullable
    default java.util.Map<String, Object> getWeather(@NotNull String dimension) {
        return null;
    }

    default boolean setWeather(@NotNull String dimension, @NotNull String kind, int durationTicks) {
        return false;
    }

    @Nullable
    default String getBiome(@NotNull String dimension, int x, int y, int z) {
        return null;
    }

    default int getLightLevel(@NotNull String dimension, int x, int y, int z, @NotNull String kind) {
        return -1;
    }

    default boolean canSeeSky(@NotNull String dimension, int x, int y, int z) {
        return false;
    }

    default int getHighestBlockY(@NotNull String dimension, int x, int z, @NotNull String kind) {
        return Integer.MIN_VALUE;
    }

    default boolean isChunkLoaded(@NotNull String dimension, int x, int z) {
        return false;
    }

    @Nullable
    default String getDifficulty(@NotNull String dimension) {
        return null;
    }

    default long getSeed(@NotNull String dimension) {
        return 0L;
    }

    @Nullable
    default java.util.Map<String, Object> getWorldSpawn(@NotNull String dimension) {
        return null;
    }

    default boolean setWorldSpawn(@NotNull String dimension, int x, int y, int z, float angle) {
        return false;
    }

    @Nullable
    default java.util.Map<String, Object> getWorldBorder(@NotNull String dimension) {
        return null;
    }

    default boolean setWorldBorder(@NotNull String dimension, double centerX, double centerZ,
                                   double size, long lerpMillis) {
        return false;
    }

    @Nullable
    default Object getGameRule(@NotNull String ruleName) {
        return null;
    }

    default boolean setGameRule(@NotNull String ruleName, @NotNull Object value) {
        return false;
    }
}

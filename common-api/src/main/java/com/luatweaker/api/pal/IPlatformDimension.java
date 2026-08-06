package com.luatweaker.api.pal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Platform abstraction for custom dimension runtime operations
 * (implemented by the NeoForge platform).
 */
public interface IPlatformDimension {

    /**
     * Teleports the raw platform player entity into the dimension level.
     *
     * @param rawPlayerEntity the platform entity (e.g. {@code ServerPlayer})
     * @param dimensionId     resource location of the dimension level
     * @return true when the level exists and the player was teleported
     */
    boolean teleportToDimension(@NotNull Object rawPlayerEntity, @NotNull String dimensionId);

    /**
     * Returns the platform level object (e.g. {@code ServerLevel}) for the
     * dimension, or null when the level is not loaded.
     */
    @Nullable
    Object getLevel(@NotNull String dimensionId);
}

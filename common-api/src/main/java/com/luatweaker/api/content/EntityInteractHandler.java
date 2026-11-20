package com.luatweaker.api.content;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.IPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Right-click hook for a custom entity type. Runs server-side only.
 *
 * @return {@code true} to consume the interaction (blocks further handling)
 */
@FunctionalInterface
public interface EntityInteractHandler {
    boolean handle(@NotNull IEntity entity, @NotNull IPlayer player);
}

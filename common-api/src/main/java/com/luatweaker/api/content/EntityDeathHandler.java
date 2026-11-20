package com.luatweaker.api.content;

import com.luatweaker.api.entity.IEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Death hook for a custom entity type. Runs server-side.
 *
 * @return {@code true} to cancel the death (the entity survives)
 */
@FunctionalInterface
public interface EntityDeathHandler {
    boolean handle(@NotNull IEntity entity, @Nullable IEntity killer);
}

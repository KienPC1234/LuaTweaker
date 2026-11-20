package com.luatweaker.api.content;

import com.luatweaker.api.entity.IEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Hurt hook for a custom entity type. Runs server-side before damage applies.
 *
 * @return replacement damage, or {@code null} to keep the original amount
 */
@FunctionalInterface
public interface EntityHurtHandler {
    @Nullable
    Float handle(@NotNull IEntity entity, float amount, @Nullable IEntity attacker);
}

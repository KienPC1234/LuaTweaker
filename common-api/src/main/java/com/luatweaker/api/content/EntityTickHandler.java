package com.luatweaker.api.content;

import com.luatweaker.api.entity.IEntity;
import org.jetbrains.annotations.NotNull;

/** Per-tick hook for a custom entity type. Runs server-side only. */
@FunctionalInterface
public interface EntityTickHandler {
    void handle(@NotNull IEntity entity);
}

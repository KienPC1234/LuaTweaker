package com.luatweaker.api.interaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IInteractionService {
    @Nullable
    IInteractableBlock GetBlock(@NotNull String dimension, int x, int y, int z);

    @Nullable
    IInteractableItem GetItem(@NotNull Object entityOrBlock, int slot);

    @Nullable
    IInteractableEntity GetEntity(@NotNull String uuid);

    @Nullable
    IInteractableEntity GetEntity(@NotNull Object rawEntity);
}

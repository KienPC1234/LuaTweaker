package com.luatweaker.interaction;

import com.luatweaker.api.interaction.*;
import com.luatweaker.api.pal.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InteractionServiceImpl implements IInteractionService {
    @Override
    @Nullable
    public IInteractableBlock GetBlock(@NotNull String dimension, int x, int y, int z) {
        return Platform.getInteraction().getInteractableBlock(dimension, x, y, z);
    }

    @Override
    @Nullable
    public IInteractableItem GetItem(@NotNull Object entityOrBlock, int slot) {
        return Platform.getInteraction().getInteractableItem(entityOrBlock, slot);
    }

    @Override
    @Nullable
    public IInteractableEntity GetEntity(@NotNull String uuid) {
        return Platform.getInteraction().getInteractableEntity(uuid);
    }

    @Override
    @Nullable
    public IInteractableEntity GetEntity(@NotNull Object rawEntity) {
        return Platform.getInteraction().getInteractableEntity(rawEntity);
    }
}

package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.ai.IWorldActionService;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.api.objects.IWorldBlock;
import com.luatweaker.api.pal.Platform;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class WorldActionServiceImpl implements IWorldActionService {
    @Override
    public boolean breakBlock(@NotNull IEntity actor, int x, int y, int z) {
        return Platform.getInteraction().performBlockBreak(actor, x, y, z);
    }

    @Override
    public boolean placeBlock(@NotNull IEntity actor, int x, int y, int z, @NotNull String blockId) {
        return Platform.getInteraction().performBlockPlace(actor, x, y, z, blockId);
    }

    @Override
    public boolean useBlock(@NotNull IEntity actor, int x, int y, int z) {
        return Platform.getInteraction().performBlockUse(actor, x, y, z);
    }

    @Override
    public boolean useItem(@NotNull IEntity actor, int slot) {
        return Platform.getInteraction().performItemUse(actor, slot);
    }

    @Override
    public void lookAt(@NotNull IEntity actor, double x, double y, double z) {
        Platform.getInteraction().lookAt(actor, x, y, z);
    }

    @Override
    public void lookAt(@NotNull IEntity actor, @NotNull IEntity target) {
        Platform.getInteraction().lookAt(actor, target);
    }

    @Override
    public boolean moveInventoryItem(@NotNull IEntity actor, int fromSlot, int toSlot) {
        return Platform.getInteraction().moveInventoryItem(actor, fromSlot, toSlot);
    }

    @Override
    public boolean dropInventoryItem(@NotNull IEntity actor, int slot, int count) {
        return Platform.getInteraction().dropInventoryItem(actor, slot, count);
    }

    @Override
    @NotNull
    public List<IWorldBlock> getNearbyBlocks(@NotNull IEntity entity, int radius) {
        return Platform.getInteraction().getNearbyBlocks(entity, radius);
    }

    @Override
    @NotNull
    public List<ILocatedItem> getInventoryItems(@NotNull IEntity entity) {
        return Platform.getInteraction().getInventoryItems(entity);
    }
}

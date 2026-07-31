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
        return Platform.get().performBlockBreak(actor, x, y, z);
    }

    @Override
    public boolean placeBlock(@NotNull IEntity actor, int x, int y, int z, @NotNull String blockId) {
        return Platform.get().performBlockPlace(actor, x, y, z, blockId);
    }

    @Override
    public boolean useBlock(@NotNull IEntity actor, int x, int y, int z) {
        return Platform.get().performBlockUse(actor, x, y, z);
    }

    @Override
    public boolean useItem(@NotNull IEntity actor, int slot) {
        return Platform.get().performItemUse(actor, slot);
    }

    @Override
    public void lookAt(@NotNull IEntity actor, double x, double y, double z) {
        Platform.get().lookAt(actor, x, y, z);
    }

    @Override
    public void lookAt(@NotNull IEntity actor, @NotNull IEntity target) {
        Platform.get().lookAt(actor, target);
    }

    @Override
    public boolean moveInventoryItem(@NotNull IEntity actor, int fromSlot, int toSlot) {
        return Platform.get().moveInventoryItem(actor, fromSlot, toSlot);
    }

    @Override
    public boolean dropInventoryItem(@NotNull IEntity actor, int slot, int count) {
        return Platform.get().dropInventoryItem(actor, slot, count);
    }

    @Override
    @NotNull
    public List<IWorldBlock> getNearbyBlocks(@NotNull IEntity entity, int radius) {
        return Platform.get().getNearbyBlocks(entity, radius);
    }

    @Override
    @NotNull
    public List<ILocatedItem> getInventoryItems(@NotNull IEntity entity) {
        return Platform.get().getInventoryItems(entity);
    }
}

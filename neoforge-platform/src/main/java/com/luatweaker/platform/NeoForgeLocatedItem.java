package com.luatweaker.platform;

import com.luatweaker.api.objects.ILocatedItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record NeoForgeLocatedItem(
        @NotNull String id,
        int count,
        int slot,
        @Nullable String ownerUuid,
        @Nullable Integer blockX,
        @Nullable Integer blockY,
        @Nullable Integer blockZ,
        @NotNull Object rawItemStack
) implements ILocatedItem {
    @Override
    @NotNull
    public String getId() {
        return id;
    }

    @Override
    public int getCount() {
        return count;
    }

    @Override
    public int getSlot() {
        return slot;
    }

    @Override
    @Nullable
    public String getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    @Nullable
    public Integer getBlockX() {
        return blockX;
    }

    @Override
    @Nullable
    public Integer getBlockY() {
        return blockY;
    }

    @Override
    @Nullable
    public Integer getBlockZ() {
        return blockZ;
    }

    @Override
    @NotNull
    public Object getRawItemStack() {
        return rawItemStack;
    }
}

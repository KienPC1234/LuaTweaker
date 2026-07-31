package com.luatweaker.platform;

import com.luatweaker.api.objects.IWorldBlock;
import org.jetbrains.annotations.NotNull;

public record NeoForgeWorldBlock(
        @NotNull String id,
        int x,
        int y,
        int z,
        @NotNull String dimension,
        @NotNull Object rawBlockState
) implements IWorldBlock {
    @Override
    @NotNull
    public String getId() {
        return id;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getZ() {
        return z;
    }

    @Override
    @NotNull
    public String getDimension() {
        return dimension;
    }

    @Override
    @NotNull
    public Object getRawBlockState() {
        return rawBlockState;
    }
}

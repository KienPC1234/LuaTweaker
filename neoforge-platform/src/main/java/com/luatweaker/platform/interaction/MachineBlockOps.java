package com.luatweaker.platform.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

/** Energy/progress accessors for custom container block entities. */
final class MachineBlockOps {

    private MachineBlockOps() {
    }

    static boolean writeEnergy(@NotNull String dimension, int x, int y, int z, int amount) {
        return ServerLevels.map(ServerLevels.resolve(dimension), false,
                level -> applyEnergy(level, WorldBlockOps.pos(x, y, z), amount));
    }

    private static boolean applyEnergy(ServerLevel level, BlockPos pos, int amount) {
        if (!(level.getBlockEntity(pos) instanceof com.luatweaker.platform.container.CustomContainerBlockEntity container)) {
            return false;
        }
        container.setEnergyStored(amount);
        return true;
    }

    static int produceEnergy(@NotNull String dimension, int x, int y, int z, int amount) {
        if (amount <= 0) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0,
                level -> addEnergy(level, WorldBlockOps.pos(x, y, z), amount));
    }

    private static int addEnergy(ServerLevel level, BlockPos pos, int amount) {
        if (!(level.getBlockEntity(pos) instanceof com.luatweaker.platform.container.CustomContainerBlockEntity container)) {
            return 0;
        }
        int current = container.getEnergyStored();
        int capacity = container.getEnergyCapacity();
        int toAdd = Math.min(amount, Math.max(0, capacity - current));
        if (toAdd > 0) {
            container.setEnergyStored(current + toAdd);
        }
        return toAdd;
    }

    static boolean writeProgress(@NotNull String dimension, int x, int y, int z, float progress) {
        return ServerLevels.map(ServerLevels.resolve(dimension), false,
                level -> storeProgress(level, WorldBlockOps.pos(x, y, z), progress));
    }

    private static boolean storeProgress(ServerLevel level, BlockPos pos, float progress) {
        if (!(level.getBlockEntity(pos) instanceof com.luatweaker.platform.container.CustomContainerBlockEntity container)) {
            return false;
        }
        container.setProgress(progress);
        return true;
    }

    static float getProgress(@NotNull String dimension, int x, int y, int z) {
        return ServerLevels.map(ServerLevels.resolve(dimension), 0.0f,
                level -> readProgress(level, WorldBlockOps.pos(x, y, z)));
    }

    private static float readProgress(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof com.luatweaker.platform.container.CustomContainerBlockEntity container)) {
            return 0.0f;
        }
        return container.getProgress();
    }
}


package com.luatweaker.platform.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.objects.IWorldBlock;
import com.luatweaker.platform.NeoForgeWorldBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

public final class BlockInteractionHelper {
    private BlockInteractionHelper() {}

    public static boolean performBlockBreak(@NotNull IEntity actor, int x, int y, int z) {
        if (actor.getRawEntity() instanceof Entity entity) {
            ServerLevel level = (ServerLevel) entity.level();
            BlockPos pos = new BlockPos(x, y, z);
            if (entity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                return serverPlayer.gameMode.destroyBlock(pos);
            } else if (entity instanceof Player player) {
                return level.destroyBlock(pos, true, player);
            } else {
                return level.destroyBlock(pos, true, entity);
            }
        }
        return false;
    }

    public static boolean performBlockPlace(@NotNull IEntity actor, int x, int y, int z, @NotNull String blockId) {
        if (actor.getRawEntity() instanceof Entity entity) {
            ServerLevel level = (ServerLevel) entity.level();
            BlockPos pos = new BlockPos(x, y, z);
            ResourceLocation rl = ResourceLocation.parse(blockId);
            Block block = BuiltInRegistries.BLOCK.get(rl);
            if (block != null && block != Blocks.AIR) {
                return level.setBlockAndUpdate(pos, block.defaultBlockState());
            }
        }
        return false;
    }

    public static boolean performBlockUse(@NotNull IEntity actor, int x, int y, int z) {
        if (actor.getRawEntity() instanceof Entity entity) {
            ServerLevel level = (ServerLevel) entity.level();
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (entity instanceof Player player) {
                BlockHitResult hit = new BlockHitResult(
                        new Vec3(x + 0.5, y + 0.5, z + 0.5),
                        net.minecraft.core.Direction.UP,
                        pos,
                        false
                );
                InteractionResult res = state.useWithoutItem(level, player, hit);
                return res.consumesAction();
            }
        }
        return false;
    }

    @NotNull
    public static List<IWorldBlock> getNearbyBlocks(@NotNull IEntity entity, int radius) {
        List<IWorldBlock> list = new ArrayList<>();
        if (entity.getRawEntity() instanceof Entity mcEntity) {
            ServerLevel level = (ServerLevel) mcEntity.level();
            BlockPos center = mcEntity.blockPosition();
            int r = Math.min(8, radius);
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(pos);
                        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        String dimension = level.dimension().location().toString();
                        list.add(new NeoForgeWorldBlock(blockId, pos.getX(), pos.getY(), pos.getZ(), dimension, state));
                    }
                }
            }
        }
        return list;
    }
}

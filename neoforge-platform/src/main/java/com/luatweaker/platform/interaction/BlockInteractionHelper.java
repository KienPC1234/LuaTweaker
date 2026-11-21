package com.luatweaker.platform.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.objects.IWorldBlock;
import com.luatweaker.platform.NeoForgeWorldBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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

    /**
     * Above this radius the block scan becomes a performance hazard; the radius is
     * still honored, only a loud warning is logged (the Lua caller decides).
     */
    private static final int PERFORMANCE_WARNING_RADIUS = 32;

    public static boolean performBlockBreak(@NotNull IEntity actor, int x, int y, int z) {
        return actor.getRawEntity() instanceof Entity entity && ServerLevels.map(ServerLevels.asServer(entity.level()), false,
                level -> destroyBlock(level, entity, new BlockPos(x, y, z)));
    }

    private static boolean destroyBlock(ServerLevel level, Entity entity, BlockPos pos) {
        if (entity instanceof ServerPlayer serverPlayer) {
            return serverPlayer.gameMode.destroyBlock(pos);
        }
        if (entity instanceof Player player) {
            return level.destroyBlock(pos, true, player);
        }
        return level.destroyBlock(pos, true, entity);
    }

    public static boolean performBlockPlace(@NotNull IEntity actor, int x, int y, int z, @NotNull String blockId) {
        return actor.getRawEntity() instanceof Entity entity && ServerLevels.map(ServerLevels.asServer(entity.level()), false,
                level -> placeBlock(level, new BlockPos(x, y, z), blockId));
    }

    private static boolean placeBlock(ServerLevel level, BlockPos pos, String blockId) {
        // An invalid id must fail loudly: silently returning false is indistinguishable
        // from "no level / no permission" and hides Lua typos (AGENTS 0.12).
        ResourceLocation rl = ResourceLocation.parse(blockId);
        return placeResolved(level, pos, rl);
    }

    private static boolean placeResolved(ServerLevel level, BlockPos pos, ResourceLocation rl) {
        Block block = BuiltInRegistries.BLOCK.get(rl);
        return block != Blocks.AIR && level.setBlockAndUpdate(pos, block.defaultBlockState());
    }

    public static boolean performBlockUse(@NotNull IEntity actor, int x, int y, int z) {
        return actor.getRawEntity() instanceof Entity entity && entity instanceof Player player && ServerLevels.map(ServerLevels.asServer(entity.level()), false,
                level -> useBlock(level, player, new BlockPos(x, y, z)));
    }

    private static boolean useBlock(ServerLevel level, Player player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockHitResult hit = new BlockHitResult(
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                Direction.UP,
                pos,
                false
        );
        InteractionResult result = state.useWithoutItem(level, player, hit);
        return result.consumesAction();
    }

    @NotNull
    public static List<IWorldBlock> getNearbyBlocks(@NotNull IEntity entity, int radius) {
        List<IWorldBlock> list = new ArrayList<>();
        if (entity.getRawEntity() instanceof Entity mcEntity) {
            if (radius > PERFORMANCE_WARNING_RADIUS) {
                com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                        "getNearbyBlocks called with radius " + radius + " (over " + PERFORMANCE_WARNING_RADIUS
                        + "); this scans up to " + (2L * radius + 1) * (2L * radius + 1) * (2L * radius + 1)
                        + " blocks and may lag the server.");
            }
            collectNearbyBlocks(mcEntity.level(), mcEntity.blockPosition(), radius, list);
        }
        return list;
    }

    private static void collectNearbyBlocks(Level level, BlockPos center, int radius, List<IWorldBlock> out) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    String dimension = level.dimension().location().toString();
                    out.add(new NeoForgeWorldBlock(blockId, pos.getX(), pos.getY(), pos.getZ(), dimension, state));
                }
            }
        }
    }
}



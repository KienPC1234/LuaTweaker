package com.luatweaker.platform.interaction;

import com.luatweaker.api.interaction.IInteractableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NeoForgeInteractableBlock implements IInteractableBlock {
    private final String dimension;
    private final int x;
    private final int y;
    private final int z;

    public NeoForgeInteractableBlock(@NotNull String dimension, int x, int y, int z) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Block accessor that runs on the server thread with a loaded chunk. */
    @FunctionalInterface
    private interface BlockRead<T> {
        @Nullable
        T read(@NotNull ServerLevel level, @NotNull BlockPos pos);
    }

    @Nullable
    private ServerLevel getLevel() {
        return levelOf(ServerLifecycleHooks.getCurrentServer(), dimension);
    }

    @Nullable
    private static ServerLevel levelOf(@Nullable MinecraftServer server, String dimensionId) {
        return server == null
                ? null
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimensionId)));
    }

    /**
     * Reads the block at this position, returning {@code fallback} when there is no live
     * server level or the chunk is not loaded (never forces chunk generation).
     */
    @Nullable
    private <T> T readOr(@Nullable T fallback, @NotNull BlockRead<T> reader) {
        return ServerLevels.map(getLevel(), fallback, level -> {
            BlockPos pos = new BlockPos(x, y, z);
            return level.hasChunkAt(pos) ? reader.read(level, pos) : fallback;
        });
    }

    @Override
    @NotNull
    public String getId() {
        return readOr("minecraft:air",
                (level, pos) -> BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
    }

    @Override
    public void setId(@NotNull String blockId) {
        ServerLevels.run(getLevel(), level -> applyId(level, blockId));
    }

    private void applyId(ServerLevel level, String blockId) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.hasChunkAt(pos)) {
            return;
        }
        ResourceLocation rl = ResourceLocation.parse(blockId);
        if (!BuiltInRegistries.BLOCK.containsKey(rl)) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "Cannot set block ID '" + blockId + "': Block does not exist in registry!");
            return;
        }
        Block block = BuiltInRegistries.BLOCK.get(rl);
        level.setBlockAndUpdate(pos, block.defaultBlockState());
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
    public boolean breakBlock() {
        return readOr(false, (level, pos) -> level.destroyBlock(pos, true));
    }

    @Override
    public boolean useBlock(@NotNull Object actorEntity) {
        return actorEntity instanceof Entity mcActor && readOr(false, (level, pos) -> useOn(level, pos, mcActor));
    }

    private static boolean useOn(ServerLevel level, BlockPos pos, Entity actor) {
        if (!(actor instanceof Player player)) {
            return false;
        }
        BlockHitResult hit = new BlockHitResult(
                new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                net.minecraft.core.Direction.UP,
                pos,
                false);
        InteractionResult result = level.getBlockState(pos).useWithoutItem(level, player, hit);
        return result.consumesAction();
    }

    @Override
    public float getHardness() {
        return readOr(0.0f, (level, pos) -> level.getBlockState(pos).getDestroySpeed(level, pos));
    }

    @Override
    public int getLightLevel() {
        return readOr(0, (level, pos) -> level.getBlockState(pos).getLightEmission());
    }

    @Override
    public boolean isAir() {
        return readOr(true, (level, pos) -> level.getBlockState(pos).isAir());
    }

    @Override
    public boolean isSolid() {
        return readOr(false, (level, pos) -> level.getBlockState(pos).isSolid());
    }

    @Override
    public boolean isLiquid() {
        return readOr(false, (level, pos) -> !level.getFluidState(pos).isEmpty());
    }

    @Override
    public @NotNull String getNbt() {
        return readOr("{}", (level, pos) -> {
            var blockEntity = level.getBlockEntity(pos);
            return blockEntity == null ? "{}" : blockEntity.saveWithFullMetadata(level.registryAccess()).toString();
        });
    }

    @Override
    public void setNbt(@NotNull String nbtJson) {
        ServerLevels.run(getLevel(), level -> applyNbt(level, nbtJson));
    }

    private void applyNbt(ServerLevel level, String nbtJson) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.hasChunkAt(pos)) {
            return;
        }
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        try {
            CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(nbtJson);
            blockEntity.loadWithComponents(tag, level.registryAccess());
            blockEntity.setChanged();
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, 3);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "Failed to parse NBT JSON for block at (" + x + "," + y + "," + z + "): " + e.getMessage());
        }
    }

    @Override
    public String getAttribute(@NotNull String key) {
        return readOr(null, (level, pos) -> {
            CompoundTag tag = blockTag(level, pos);
            return tag != null && tag.contains(key) ? tag.get(key).getAsString() : null;
        });
    }

    @Override
    public void setAttribute(@NotNull String key, @NotNull String value) {
        ServerLevels.run(getLevel(), level -> applyAttribute(level, key, value));
    }

    private void applyAttribute(ServerLevel level, String key, String value) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.hasChunkAt(pos)) {
            return;
        }
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
        tag.putString(key, value);
        blockEntity.loadWithComponents(tag, level.registryAccess());
        blockEntity.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }

    @Override
    @NotNull
    public Object getRawBlockState() {
        return readOr(Blocks.AIR.defaultBlockState(), net.minecraft.world.level.Level::getBlockState);
    }

    @Nullable
    private static CompoundTag blockTag(ServerLevel level, BlockPos pos) {
        var blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess());
    }
}


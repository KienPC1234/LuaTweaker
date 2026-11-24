package com.luatweaker.common.interaction;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MinecraftInteractableBlock implements IInteractableBlock {
    private final String dimension;
    private final int x;
    private final int y;
    private final int z;

    public MinecraftInteractableBlock(@NotNull String dimension, int x, int y, int z) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Nullable
    private ServerLevel getLevel() {
        return levelOf(com.luatweaker.common.CommonServerHolder.getServer(), dimension);
    }

    @Nullable
    private static ServerLevel levelOf(@Nullable MinecraftServer server, String dimensionId) {
        return server == null
                ? null
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimensionId)));
    }

    @Nullable
    private <T> T readOr(@Nullable T fallback, @NotNull BlockReadSupport.BlockRead<T> reader) {
        return BlockReadSupport.readOr(getLevel(), new BlockPos(x, y, z), fallback, reader);
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

    @Override
    public int getRedstoneSignal() {
        return readOr(0, net.minecraft.world.level.SignalGetter::getBestNeighborSignal);
    }

    @Override
    public int getDirectSignal(@NotNull String side) {
        net.minecraft.core.Direction dir = net.minecraft.core.Direction.byName(side.toLowerCase(java.util.Locale.ROOT));
        return readOr(0, (level, pos) -> dir != null ? level.getDirectSignal(pos, dir) : 0);
    }

    @Override
    public @Nullable String getProperty(@NotNull String name) {
        return readOr(null, (level, pos) -> {
            BlockState state = level.getBlockState(pos);
            for (net.minecraft.world.level.block.state.properties.Property<?> prop : state.getProperties()) {
                if (prop.getName().equalsIgnoreCase(name)) {
                    return propValueToString(state, prop);
                }
            }
            return null;
        });
    }

    @Override
    public boolean applyProperty(@NotNull String name, @NotNull String value) {
        return Boolean.TRUE.equals(readOr(false, (level, pos) -> {
            BlockState state = level.getBlockState(pos);
            for (net.minecraft.world.level.block.state.properties.Property<?> prop : state.getProperties()) {
                if (prop.getName().equalsIgnoreCase(name)) {
                    BlockState updated = applyPropertyValue(state, prop, value);
                    if (updated != null) {
                        level.setBlockAndUpdate(pos, updated);
                        return true;
                    }
                }
            }
            return false;
        }));
    }

    @Override
    public @NotNull java.util.Map<String, String> getProperties() {
        return readOr(java.util.Map.of(), (level, pos) -> {
            BlockState state = level.getBlockState(pos);
            return state.getValues().entrySet().stream()
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            entry -> entry.getKey().getName(),
                            entry -> propValueToString(state, entry.getKey())));
        });
    }

    @Override
    public @NotNull String getBiome() {
        return readOr("minecraft:plains", (level, pos) -> level.getBiome(pos).unwrapKey()
                .map(k -> k.location().toString()).orElse("minecraft:plains"));
    }

    @Override
    public void explode(float power, boolean causesFire, boolean breakBlocks) {
        ServerLevels.run(getLevel(), level -> {
            BlockPos pos = new BlockPos(x, y, z);
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power, causesFire,
                    breakBlocks ? net.minecraft.world.level.Level.ExplosionInteraction.BLOCK
                            : net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String propValueToString(
            BlockState state, net.minecraft.world.level.block.state.properties.Property<?> prop) {
        return ((net.minecraft.world.level.block.state.properties.Property<T>) prop)
                .getName(state.getValue((net.minecraft.world.level.block.state.properties.Property<T>) prop));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyPropertyValue(
            BlockState state, net.minecraft.world.level.block.state.properties.Property<?> rawProp, String valStr) {
        net.minecraft.world.level.block.state.properties.Property<T> prop =
                (net.minecraft.world.level.block.state.properties.Property<T>) rawProp;
        java.util.Optional<T> opt = prop.getValue(valStr);
        return opt.map(val -> state.setValue(prop, val)).orElse(null);
    }
}


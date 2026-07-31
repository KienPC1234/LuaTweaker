package com.luatweaker.platform.interaction;

import com.luatweaker.api.interaction.IInteractableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

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

    private ServerLevel getLevel() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ResourceLocation rl = ResourceLocation.parse(dimension);
            var key = ResourceKey.create(Registries.DIMENSION, rl);
            return server.getLevel(key);
        }
        return null;
    }

    @Override
    @NotNull
    public String getId() {
        ServerLevel level = getLevel();
        if (level != null) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        }
        return "minecraft:air";
    }

    @Override
    public void setId(@NotNull String blockId) {
        ServerLevel level = getLevel();
        if (level != null) {
            BlockPos pos = new BlockPos(x, y, z);
            ResourceLocation rl = ResourceLocation.parse(blockId);
            Block block = BuiltInRegistries.BLOCK.get(rl);
            if (block != null) {
                level.setBlockAndUpdate(pos, block.defaultBlockState());
            }
        }
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
        ServerLevel level = getLevel();
        if (level != null) {
            BlockPos pos = new BlockPos(x, y, z);
            return level.destroyBlock(pos, true);
        }
        return false;
    }

    @Override
    public boolean useBlock(@NotNull Object actorEntity) {
        ServerLevel level = getLevel();
        if (level != null && actorEntity instanceof Entity mcActor) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (mcActor instanceof Player player) {
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

    @Override
    public float getHardness() {
        ServerLevel level = getLevel();
        if (level != null) {
            BlockPos pos = new BlockPos(x, y, z);
            return level.getBlockState(pos).getDestroySpeed(level, pos);
        }
        return 0.0f;
    }

    @Override
    public int getLightLevel() {
        ServerLevel level = getLevel();
        if (level != null) {
            BlockPos pos = new BlockPos(x, y, z);
            return level.getBlockState(pos).getLightEmission();
        }
        return 0;
    }

    @Override
    public boolean isAir() {
        ServerLevel level = getLevel();
        if (level != null) {
            return level.getBlockState(new BlockPos(x, y, z)).isAir();
        }
        return true;
    }

    @Override
    public boolean isSolid() {
        ServerLevel level = getLevel();
        if (level != null) {
            BlockPos pos = new BlockPos(x, y, z);
            return level.getBlockState(pos).isSolid();
        }
        return false;
    }

    @Override
    public boolean isLiquid() {
        ServerLevel level = getLevel();
        if (level != null) {
            BlockPos pos = new BlockPos(x, y, z);
            return !level.getFluidState(pos).isEmpty();
        }
        return false;
    }

    @Override
    @NotNull
    public String getNbt() {
        ServerLevel level = getLevel();
        if (level != null) {
            BlockPos pos = new BlockPos(x, y, z);
            var be = level.getBlockEntity(pos);
            if (be != null) {
                return be.saveWithFullMetadata(level.registryAccess()).toString();
            }
        }
        return "{}";
    }

    @Override
    public void setNbt(@NotNull String nbtJson) {
        ServerLevel level = getLevel();
        if (level != null) {
            BlockPos pos = new BlockPos(x, y, z);
            var be = level.getBlockEntity(pos);
            if (be != null) {
                try {
                    net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(nbtJson);
                    be.loadWithComponents(tag, level.registryAccess());
                    be.setChanged();
                    BlockState state = level.getBlockState(pos);
                    level.sendBlockUpdated(pos, state, state, 3);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public String getAttribute(@NotNull String key) {
        ServerLevel level = getLevel();
        if (level != null) {
            BlockPos pos = new BlockPos(x, y, z);
            var be = level.getBlockEntity(pos);
            if (be != null) {
                net.minecraft.nbt.CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
                if (tag.contains(key)) {
                    return tag.get(key).getAsString();
                }
            }
        }
        return null;
    }

    @Override
    public void setAttribute(@NotNull String key, @NotNull String value) {
        ServerLevel level = getLevel();
        if (level != null) {
            BlockPos pos = new BlockPos(x, y, z);
            var be = level.getBlockEntity(pos);
            if (be != null) {
                net.minecraft.nbt.CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
                tag.putString(key, value);
                be.loadWithComponents(tag, level.registryAccess());
                be.setChanged();
                BlockState state = level.getBlockState(pos);
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    @Override
    @NotNull
    public Object getRawBlockState() {
        ServerLevel level = getLevel();
        if (level != null) {
            return level.getBlockState(new BlockPos(x, y, z));
        }
        return Blocks.AIR.defaultBlockState();
    }
}

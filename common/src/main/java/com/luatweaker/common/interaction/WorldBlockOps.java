package com.luatweaker.common.interaction;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single-block world operations behind the Lua {@code World} service.
 *
 * <p>Every entry point resolves the dimension and refuses unloaded chunks (never forces
 * generation: a Lua callback holding the execution gate must not block the chunk worker
 * that needs the same gate).
 */
final class WorldBlockOps {

    private WorldBlockOps() {
    }

    @Nullable
    static Map<String, Object> getBlockState(@NotNull String dimension, int x, int y, int z) {
        return ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            // Unloaded chunks read as null: forcing generation here can deadlock the chunk
            // worker against the Lua execution gate this callback already holds.
            if (!BlockStateCodec.isChunkLoaded(level, x, z)) {
                return null;
            }
            BlockState state = level.getBlockState(pos(x, y, z));
            var result = new LinkedHashMap<String, Object>();
            result.put("Id", BlockStateCodec.idOf(state));
            var properties = new LinkedHashMap<String, Object>();
            for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
                properties.put(entry.getKey().getName(), entry.getValue().toString());
            }
            result.put("Properties", properties);
            return result;
        });
    }

    static boolean writeBlockState(@NotNull String dimension, int x, int y, int z, @NotNull String blockId,
                                 @Nullable Map<String, Object> properties) {
        return ServerLevels.map(ServerLevels.resolve(dimension), false,
                level -> writeBlockStateIn(level, dimension, x, y, z, blockId, properties));
    }

    private static boolean writeBlockStateIn(ServerLevel level, String dimension, int x, int y, int z,
                                           String blockId, @Nullable Map<String, Object> properties) {
        BlockState state = BlockStateCodec.resolve(blockId, properties);
        if (state == null) {
            return false;
        }
        // Writing into an unloaded chunk forces generation; refuse it loudly instead of
        // blocking on a chunk future while holding the Lua execution gate.
        if (!BlockStateCodec.isChunkLoaded(level, x, z)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "setBlockState skipped: chunk (" + (x >> 4) + "," + (z >> 4)
                            + ") is not loaded in " + dimension);
            return false;
        }
        return level.setBlock(pos(x, y, z), state, 3);
    }

    @Nullable
    static Map<String, Object> getBlockEntityData(@NotNull String dimension, int x, int y, int z) {
        return ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            BlockEntity blockEntity = level.getBlockEntity(pos(x, y, z));
            return blockEntity == null
                    ? null
                    : NbtCodec.toMap(blockEntity.saveWithFullMetadata(level.registryAccess()));
        });
    }

    static boolean writeBlockEntityData(@NotNull String dimension, int x, int y, int z,
                                      @NotNull Map<String, Object> data) {
        return ServerLevels.map(ServerLevels.resolve(dimension), false,
                level -> applyBlockEntityData(level, pos(x, y, z), data));
    }

    private static boolean applyBlockEntityData(ServerLevel level, BlockPos pos, Map<String, Object> data) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }
        try {
            // Merge: Lua data overrides the given keys, everything else (items,
            // position, id) is preserved so partial writes never wipe contents.
            var merged = blockEntity.saveWithoutMetadata(level.registryAccess());
            var update = NbtCodec.fromMap(data);
            for (String key : update.getAllKeys()) {
                merged.put(key, update.get(key));
            }
            blockEntity.loadWithComponents(merged, level.registryAccess());
            blockEntity.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 2);
            return true;
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Failed to apply block entity data at " + pos + ": " + e.getMessage());
            return false;
        }
    }

    static boolean ejectContainerItem(@NotNull String dimension, int x, int y, int z, int slot, int count) {
        return ServerLevels.map(ServerLevels.resolve(dimension), false,
                level -> ejectFrom(level, pos(x, y, z), slot, count));
    }

    private static boolean ejectFrom(ServerLevel level, BlockPos pos, int slot, int count) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container)
                || slot < 0 || slot >= container.getContainerSize() || count <= 0) {
            return false;
        }
        ItemStack stack = container.removeItem(slot, count);
        if (stack.isEmpty()) {
            return false;
        }
        Block.popResource(level, pos, stack);
        blockEntity.setChanged();
        return true;
    }

    static boolean placeStructure(@NotNull String dimension, @NotNull String templateId,
                                  int x, int y, int z, int rotationDegrees) {
        return ServerLevels.map(ServerLevels.resolve(dimension), false,
                level -> placeIn(level, templateId, pos(x, y, z), rotationDegrees));
    }

    private static boolean placeIn(ServerLevel level, String templateId, BlockPos origin, int rotationDegrees) {
        ResourceLocation rl = ResourceLocation.tryParse(templateId);
        if (rl == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "placeStructure failed: invalid template id '" + templateId + "'");
            return false;
        }
        var template = level.getStructureManager().get(rl);
        if (template.isEmpty()) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "placeStructure failed: template '" + templateId
                            + "' not found (data/" + rl.getNamespace() + "/structure/" + rl.getPath() + ".nbt)");
            return false;
        }
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                .setRotation(net.minecraft.world.level.block.Rotation.values()[
                        Math.floorMod(rotationDegrees / 90, 4)]);
        var random = net.minecraft.util.RandomSource.create();
        settings.setRandom(random);
        template.get().placeInWorld(level, origin, BlockPos.ZERO, settings, random, 2);
        return true;
    }

    static BlockPos pos(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }
}


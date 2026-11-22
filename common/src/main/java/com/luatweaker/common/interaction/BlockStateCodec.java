package com.luatweaker.common.interaction;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Parsing and lookup helpers shared by every world-block operation. */
final class BlockStateCodec {

    private BlockStateCodec() {
    }

    /**
     * Parses a block id plus optional property values into a {@link BlockState}.
     * Returns null for unknown block ids. Property values that do not match the
     * block's definition are skipped with a warning (matching per-block behavior).
     */
    @Nullable
    static BlockState resolve(@NotNull String blockId, @Nullable Map<String, Object> properties) {
        ResourceLocation rl = ResourceLocation.tryParse(blockId);
        if (rl == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(rl);
        if (block == Blocks.AIR && !"minecraft:air".equals(blockId)) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        if (properties == null) {
            return state;
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                continue;
            }
            try {
                state = withProperty(state, property, String.valueOf(entry.getValue()));
            } catch (IllegalArgumentException e) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Invalid value '" + entry.getValue() + "' for blockstate property " + entry.getKey());
            }
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static BlockState withProperty(BlockState state, Property property, String value) {
        for (Comparable possible : (Iterable<Comparable>) property.getPossibleValues()) {
            if (possible.toString().equals(value)) {
                return state.setValue(property, possible);
            }
        }
        return state;
    }

    @Nullable
    static Block parseBlock(@NotNull String blockId) {
        ResourceLocation rl = ResourceLocation.tryParse(blockId);
        return rl == null ? null : BuiltInRegistries.BLOCK.get(rl);
    }

    @NotNull
    static String idOf(@NotNull BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    @NotNull
    static String withNamespace(@NotNull String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    /**
     * True when the chunk is already loaded. Deliberately does not trigger generation: a Lua
     * callback that blocks on a chunk future while holding the execution gate can deadlock with
     * the generation worker that needs that gate for its own Lua terrain callback.
     */
    static boolean isChunkLoaded(@NotNull ServerLevel level, int x, int z) {
        return level.getChunkSource().hasChunk(x >> 4, z >> 4);
    }
}

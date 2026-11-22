package com.luatweaker.common.interaction;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Mutating bulk operations behind the Lua {@code World} service: fill, replace and
 * property patching. Every loop skips unloaded chunks instead of forcing generation
 * (the Lua execution gate is held while these run).
 */
final class WorldBulkOps {

    /** Hard safety cap for a single bulk fill operation. */
    private static final long MAX_FILL_VOLUME = 200_000L;
    private static final String WILDCARD_MATCHER = "*";

    private WorldBulkOps() {
    }

    static long fillBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                           @NotNull String blockId, @Nullable Map<String, Object> properties) {
        return ServerLevels.map(ServerLevels.resolve(dimension), -1L,
                level -> fillIn(level, blockId, properties, x1, y1, z1, x2, y2, z2));
    }

    private static long fillIn(ServerLevel level, String blockId, @Nullable Map<String, Object> properties,
                                int x1, int y1, int z1, int x2, int y2, int z2) {
        BlockState state = BlockStateCodec.resolve(blockId, properties);
        if (state == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "fillBlocks rejected: unknown block id '" + blockId + "'");
            return -1;
        }
        long volume = boxVolume(x1, y1, z1, x2, y2, z2);
        if (volume <= 0) {
            return -1;
        }
        if (volume > MAX_FILL_VOLUME) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "fillBlocks rejected: volume " + volume + " exceeds platform cap " + MAX_FILL_VOLUME);
            return -1;
        }
        Counts counts = scanBox(level, x1, y1, z1, x2, y2, z2, pos -> level.setBlock(pos, state, 3));
        warnSkippedUnloaded("fillBlocks", counts.skipped());
        return counts.applied();
    }

    static long replaceBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                              @NotNull String fromId, @NotNull String toId) {
        return ServerLevels.map(ServerLevels.resolve(dimension), -1L,
                level -> replaceIn(level, fromId, toId, x1, y1, z1, x2, y2, z2));
    }

    private static long replaceIn(ServerLevel level, String fromId, String toId, int x1, int y1, int z1,
                                   int x2, int y2, int z2) {
        BlockState toState = BlockStateCodec.resolve(toId, null);
        if (toState == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "replaceBlocks rejected: unknown target block id '" + toId + "'");
            return -1;
        }
        Block fromBlock = BlockStateCodec.parseBlock(fromId);
        if (fromBlock == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "replaceBlocks rejected: unknown source block id '" + fromId + "'");
            return -1;
        }
        long volume = boxVolume(x1, y1, z1, x2, y2, z2);
        if (volume <= 0) {
            return -1;
        }
        if (volume > MAX_FILL_VOLUME) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "replaceBlocks rejected: volume " + volume + " exceeds platform cap " + MAX_FILL_VOLUME);
            return -1;
        }
        Counts counts = scanBox(level, x1, y1, z1, x2, y2, z2, pos ->
                Objects.equals(level.getBlockState(pos).getBlock(), fromBlock)
                        && level.setBlock(pos, toState, 3));
        warnSkippedUnloaded("replaceBlocks", counts.skipped());
        return counts.applied();
    }

    static int patchBlockStatesInBox(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                                   @Nullable String filterBlockId, @NotNull Map<String, Object> properties) {
        if (properties.isEmpty()) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0,
                level -> patchStatesIn(level, filterBlockId, properties, x1, y1, z1, x2, y2, z2));
    }

    private static int patchStatesIn(ServerLevel level, @Nullable String filterBlockId,
                                     Map<String, Object> properties, int x1, int y1, int z1,
                                     int x2, int y2, int z2) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        int updated = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!BlockStateCodec.isChunkLoaded(level, x, z)) {
                        continue;
                    }
                    if (patchStateAt(level, WorldBlockOps.pos(x, y, z), filterBlockId, properties)) {
                        updated++;
                    }
                }
            }
        }
        return updated;
    }

    private static boolean patchStateAt(ServerLevel level, BlockPos pos, @Nullable String filterBlockId,
                                        Map<String, Object> properties) {
        BlockState state = level.getBlockState(pos);
        if (filterBlockId != null && !filterBlockId.isEmpty() && !WILDCARD_MATCHER.equals(filterBlockId)
                && !BlockStateCodec.idOf(state).equalsIgnoreCase(BlockStateCodec.withNamespace(filterBlockId))) {
            return false;
        }
        BlockState newState = state;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Property<?> property = newState.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property != null) {
                newState = BlockStateCodec.withProperty(newState, property, String.valueOf(entry.getValue()));
            }
        }
        if (Objects.equals(newState, state)) {
            return false;
        }
        level.setBlock(pos, newState, 3);
        return true;
    }

    /** Applies {@code action} to every loaded cell, counting applied/skipped cells. */
    private static Counts scanBox(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2,
                                  CellAction action) {
        long applied = 0;
        long skipped = 0;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    if (!BlockStateCodec.isChunkLoaded(level, x, z)) {
                        skipped++;
                        continue;
                    }
                    if (action.apply(WorldBlockOps.pos(x, y, z))) {
                        applied++;
                    }
                }
            }
        }
        return new Counts(applied, skipped);
    }

    private record Counts(long applied, long skipped) {
    }

    @FunctionalInterface
    private interface CellAction {
        boolean apply(BlockPos pos);
    }

    private static void warnSkippedUnloaded(@NotNull String operation, long skipped) {
        if (skipped > 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    operation + " skipped " + skipped + " block(s) in unloaded chunks");
        }
    }

    private static long boxVolume(int x1, int y1, int z1, int x2, int y2, int z2) {
        long dx = (long) Math.abs(x2 - x1) + 1;
        long dy = (long) Math.abs(y2 - y1) + 1;
        long dz = (long) Math.abs(z2 - z1) + 1;
        return dx * dy * dz;
    }
}


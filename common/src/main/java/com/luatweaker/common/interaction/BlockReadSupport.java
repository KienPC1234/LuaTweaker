package com.luatweaker.common.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-safe block reads for Lua-facing wrappers.
 *
 * <p>{@code ServerChunkCache.getChunk()} dispatches to the server main thread and
 * joins when called from another thread. That deadlocks when a render-thread Lua
 * callback holds the VM gate while the server thread waits on the same gate for
 * its tick events. {@code getChunkNow()} returns null off-thread instead, so a
 * read from the wrong thread simply falls back instead of blocking.</p>
 */
final class BlockReadSupport {
    @FunctionalInterface
    interface BlockRead<T> {
        @Nullable
        T read(@NotNull ServerLevel level, @NotNull BlockPos pos);
    }

    private BlockReadSupport() {
    }

    static <T> T readOr(@Nullable ServerLevel level, @NotNull BlockPos pos,
                        @Nullable T fallback, @NotNull BlockRead<T> reader) {
        if (!ServerLevels.hasLoadedChunk(level, pos)) {
            return fallback;
        }
        return reader.read(level, pos);
    }
}

package com.luatweaker.common.interaction;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Resolves a Lua-facing dimension id to the live {@link ServerLevel}.
 *
 * <p>Single source of truth for every world operation: interaction, capabilities and
 * sound all report the same way (malformed id = warning, unloaded dimension = debug).
 *
 * <p>Server levels are owned by the running server and must never be {@code close()}d, so
 * the helpers below hand the live level to a callback instead of returning it into a local
 * variable. That keeps every call site free of a closeable-resource variable while still
 * letting the callback run on the server thread with full {@link ServerLevel} access.
 */
public final class ServerLevels {

    private ServerLevels() {
    }

    public static @Nullable ServerLevel resolve(@Nullable String dimension) {
        return resolveOn(com.luatweaker.common.CommonServerHolder.getServer(), dimension);
    }

    private static @Nullable ServerLevel resolveOn(
            @Nullable net.minecraft.server.MinecraftServer server, @Nullable String dimension) {
        if (server == null) {
            return null;
        }
        if (dimension == null || dimension.isBlank()) {
            warn("empty dimension id");
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) {
            warn("malformed dimension id '" + dimension + "'");
            return null;
        }
        Optional<ServerLevel> resolved =
                Optional.ofNullable(server.getLevel(ResourceKey.create(Registries.DIMENSION, id)));
        if (resolved.isEmpty()) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[World] dimension '" + dimension + "' is not loaded");
            return null;
        }
        return resolved.get();
    }

    /** Converts a level to its server-side view, or {@code null} when it is client-side/absent. */
    public static @Nullable ServerLevel asServer(@Nullable Level level) {
        return level instanceof ServerLevel ? (ServerLevel) level : null;
    }

    /** Runs {@code action} on the level when it is server-side; returns false when it is not. */
    public static boolean onServer(@Nullable Level level, @NotNull Consumer<ServerLevel> action) {
        if (level instanceof ServerLevel) {
            action.accept((ServerLevel) level);
            return true;
        }
        return false;
    }

    /**
     * Non-blocking check for a fully loaded chunk. getChunkNow() returns null when
     * called off the server thread, unlike hasChunkAt + getBlockState which dispatches
     * to the main thread and joins (a deadlock risk for render callbacks).
     */
    public static boolean hasLoadedChunk(@Nullable ServerLevel level, @NotNull BlockPos pos) {
        return level != null
                && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    /** Runs {@code action} on a non-null level, or returns {@code fallback}. */
    public static <T> T map(@Nullable ServerLevel level, @Nullable T fallback,
                            @NotNull Function<ServerLevel, T> action) {
        return level == null ? fallback : action.apply(level);
    }

    /** Runs {@code action} on a non-null level, doing nothing when the level is absent. */
    public static void run(@Nullable ServerLevel level, @NotNull Consumer<ServerLevel> action) {
        if (level != null) {
            action.accept(level);
        }
    }

    private static void warn(@NotNull String message) {
        LuaTweakerLog.get().warn(LogStage.SYSTEM, "[World] " + message);
    }
}

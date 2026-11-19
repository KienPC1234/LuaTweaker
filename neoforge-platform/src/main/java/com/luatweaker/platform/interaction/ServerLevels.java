package com.luatweaker.platform.interaction;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a Lua-facing dimension id to the live {@link ServerLevel}.
 *
 * <p>Single source of truth for every world operation: interaction, capabilities and
 * sound all report the same way (malformed id = warning, unloaded dimension = debug).
 */
public final class ServerLevels {

    private ServerLevels() {
    }

    public static @Nullable ServerLevel resolve(@Nullable String dimension) {
        var server = ServerLifecycleHooks.getCurrentServer();
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
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[World] dimension '" + dimension + "' is not loaded");
        }
        return level;
    }

    private static void warn(@NotNull String message) {
        LuaTweakerLog.get().warn(LogStage.SYSTEM, "[World] " + message);
    }
}

package com.luatweaker.api.pal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Read-only world-generation queries that need the live server registries.
 *
 * <p>Implemented by the platform layer (core modules must not import Minecraft). The returned
 * maps are plain Java collections so the Lua binder can convert them without leaking
 * platform types into {@code common-api}.</p>
 */
public interface IPlatformWorldgen {

    /**
     * Reads one biome from the server's biome registry.
     *
     * @param biomeId resource location, for example {@code minecraft:plains}
     * @return a map with {@code id}, {@code temperature}, {@code downfall},
     *         {@code hasPrecipitation}, the sky/fog/water colors, optional grass/foliage color
     *         overrides and a per-category {@code spawners} map; or {@code null} when no server
     *         is running or the biome is not registered
     */
    @Nullable Map<String, Object> getBiomeInfo(@NotNull String biomeId);
}

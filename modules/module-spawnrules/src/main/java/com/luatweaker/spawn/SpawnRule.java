package com.luatweaker.spawn;

import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One declarative spawn rule, evaluated by the platform natural spawner for the exact
 * candidate position (block light, sky light, Y, biome, block below, time, weather).
 *
 * <p>Range fields are inclusive. {@code biomes} and {@code blocksBelow} are whitelists:
 * an empty list means "any". {@code requireDay} {@code true} = day only, {@code false} =
 * night only, {@code null} = any time. The same convention applies to
 * {@code requireRaining} / {@code requireThundering}. {@code check} is an optional Lua
 * predicate that can veto the rule; it is the only part that runs Lua at spawn time.</p>
 */
public record SpawnRule(
        @NotNull String entity,
        @Nullable String category,
        int weight,
        int minGroup,
        int maxGroup,
        int minY,
        int maxY,
        int minBlockLight,
        int maxBlockLight,
        int minSkyLight,
        int maxSkyLight,
        @Nullable Boolean requireDay,
        @Nullable Boolean requireRaining,
        @Nullable Boolean requireThundering,
        @NotNull List<String> biomes,
        @NotNull List<String> blocksBelow,
        boolean requireSolidGround,
        @Nullable ILuaValue check
) {

    public SpawnRule {
        biomes = List.copyOf(biomes);
        blocksBelow = List.copyOf(blocksBelow);
    }
}

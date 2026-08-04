package com.luatweaker.api.worldgen;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import org.jetbrains.annotations.NotNull;

@LuaDoc(description = "Service for world generation: ore placement, biome modification, feature placement.")
public interface IWorldgenService {

    @LuaDoc(
        description = "Add ore generation to a dimension.",
        params = {"blockId: string - block to generate (e.g. 'mymod:ruby_ore')",
                  "dimension: string - dimension ID (e.g. 'minecraft:overworld')",
                  "minHeight: number", "maxHeight: number",
                  "clusterSize: number - blocks per cluster",
                  "frequency: number - clusters per chunk"},
        returnType = "void"
    )
    void addOre(
            @NotNull String blockId,
            @NotNull String dimension,
            int minHeight,
            int maxHeight,
            @LuaDefault("8") int clusterSize,
            @LuaDefault("10") int frequency
    );

    @LuaDoc(
        description = "Add ore generation with biome filter.",
        params = {"blockId: string", "dimension: string",
                  "minHeight: number", "maxHeight: number",
                  "clusterSize: number", "frequency: number",
                  "biomes: table - list of biome IDs"},
        returnType = "void"
    )
    void addOreBiomeFiltered(
            @NotNull String blockId,
            @NotNull String dimension,
            int minHeight,
            int maxHeight,
            int clusterSize,
            int frequency,
            @NotNull String[] biomes
    );

    @LuaDoc(
        description = "Add vegetation (flowers, grass) to biomes.",
        params = {"blockId: string", "chance: number - per-chunk chance (0.0-1.0)",
                  "biomes: table - list of biome IDs"},
        returnType = "void"
    )
    void addVegetation(
            @NotNull String blockId,
            double chance,
            @NotNull String[] biomes
    );

    @LuaDoc(
        description = "Remove vanilla ore from a dimension.",
        params = {"blockId: string - ore block to remove", "dimension: string"},
        returnType = "boolean"
    )
    boolean removeOre(@NotNull String blockId, @NotNull String dimension);

    @LuaDoc(
        description = "Get all pending worldgen modifications as a summary.",
        returnType = "table"
    )
    @NotNull
    java.util.Map<String, Object> getModifications();

    @LuaDoc(description = "Clear all pending modifications.", returnType = "void")
    void clearAll();
}

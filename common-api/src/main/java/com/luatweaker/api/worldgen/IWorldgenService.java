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
                  "frequency: number - clusters per chunk"}
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
                  "biomes: table - list of biome IDs"}
    )
    void addOreBiomeFiltered(
            @NotNull String blockId,
            @NotNull String dimension,
            int minHeight,
            int maxHeight,
            int clusterSize,
            int frequency,
            @NotNull String... biomes
    );

    @LuaDoc(
        description = "Add vegetation (flowers, grass) to biomes.",
        params = {"blockId: string", "chance: number - per-chunk chance (0.0-1.0)",
                  "biomes: table - list of biome IDs"}
    )
    void addVegetation(
            @NotNull String blockId,
            double chance,
            @NotNull String... biomes
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

    @LuaDoc(
        description = "Register a raw configured feature from verbatim JSON (any vanilla type: tree, geode, random_patch, ...). The JSON must parse and carry a string 'type'; the type is checked against the 1.21.1 vanilla list so typos fail fast. Reference it as 'luatweaker:<id>' from ModifyBiome add or a biome features list; pair it with AddRawPlacedFeature for a working pipeline.",
        params = {"featureId: string - id (e.g. 'mymod:crystal_tree')",
                  "json: string - verbatim configured-feature JSON"}
    )
    void addRawConfiguredFeature(@NotNull String featureId, @NotNull String json);

    @LuaDoc(
        description = "Register a raw placed feature from verbatim JSON. The JSON must parse and carry a string 'feature' reference. Pair with AddRawConfiguredFeature (or a vanilla configured feature) and reference it as 'luatweaker:<id>' from ModifyBiome add.",
        params = {"featureId: string - id for the placed file (e.g. 'mymod:crystal_tree_patch')",
                  "json: string - verbatim placed-feature JSON"}
    )
    void addRawPlacedFeature(@NotNull String featureId, @NotNull String json);

    @LuaDoc(
        description = "List the placed-feature IDs a removeOre call would strip for an ore block (user overrides first, then vanilla defaults). Empty when the block is unknown.",
        params = {"blockId: string - ore block (e.g. 'minecraft:coal_ore')"},
        returnType = "table"
    )
    @NotNull
    java.util.List<String> getRemovalFeatures(@NotNull String blockId);

    @LuaDoc(
        description = "Get the biome tag (or single biome) a dimension maps to for ore/feature placement, or nil when the dimension is not registered.",
        params = {"dimension: string - dimension ID (e.g. 'minecraft:overworld')"},
        returnType = "string|nil"
    )
    @org.jetbrains.annotations.Nullable
    String getDimensionBiomeTag(@NotNull String dimension);

    @LuaDoc(description = "Clear all pending modifications.")
    void clearAll();
}

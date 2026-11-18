package com.luatweaker.worldgen;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Extended world-generation API: biome modification, custom features,
 * data-driven removal/dimension registries and vegetation variants.
 *
 * <p>Kept separate from {@code IWorldgenService} (common-api, owned by another
 * agent) so this module can grow without cross-module edits. Bound into the
 * same {@code Worldgen} Lua table by {@link WorldgenLuaBinding} via
 * {@code LuaBinder.bindTable} + merge.
 */
@LuaDoc(description = "Extended world generation: biome modifiers, custom features, data-driven registries.")
public interface IWorldgenExtendedService {

    @LuaDoc(
        description = "Add and/or remove placed features on explicit biomes. Emits neoforge biome-modifier JSON "
            + "consumed by the platform provider via IDatapackService (datapack path "
            + "'luatweaker/neoforge/biome_modifier/*.json'). Options table: "
            + "{ biomes = {'minecraft:plains', ...} (or biome = '...'), "
            + "add = {'modid:feature', ...} or {{feature='...', step='...'}, ...}, "
            + "step = 'underground_ores' (default for adds), "
            + "remove = {'modid:feature', ...}, removeSteps = {'underground_ores'} }.",
        params = {"biomeId: string - owning biome, used only for file naming and logs",
                  "options: table - biomes/add/step/remove/removeSteps as described"},
        returnType = "void"
    )
    void modifyBiome(@NotNull String biomeId, @NotNull ILuaTable options);

    @LuaDoc(
        description = "Register a custom ore-like feature: emits configured + placed feature JSON plus an "
            + "add_features biome modifier. Spec table: { block='modid:block' (required), "
            + "biomes={'minecraft:plains', ...} or biome='...' (required), step='underground_ores', "
            + "replace='stone'|'nether'|'end' (target rock, default 'stone'), "
            + "count=10, size=8, minHeight=-64, maxHeight=32 }.",
        params = {"featureId: string - id for the generated feature (e.g. 'mymod:ruby_vein')",
                  "spec: table - block/biomes/step/replace/count/size/minHeight/maxHeight as described"},
        returnType = "void"
    )
    void addFeature(@NotNull String featureId, @NotNull ILuaTable spec);

    @LuaDoc(
        description = "Add grass/bush-like vegetation using the minecraft:random_patch feature type "
            + "(vanilla patch_grass shape) instead of the minecraft:flower type used by addVegetation.",
        params = {"blockId: string - block to scatter (e.g. 'minecraft:short_grass')",
                  "chance: number - per-chunk chance (0.0-1.0)",
                  "biomes: table - list of biome IDs"},
        returnType = "void"
    )
    void addVegetationPatch(@NotNull String blockId, double chance, @NotNull String[] biomes);

    @LuaDoc(
        description = "Register (or override) the placed-feature IDs removed for an ore block. "
            + "Makes ore removal data-driven: modded ores work once registered. "
            + "Defaults cover 10 vanilla ores (verified against the 1.21.1 client jar).",
        params = {"blockId: string - ore block (e.g. 'mymod:ruby_ore')",
                  "features: table - placed-feature IDs (e.g. {'mymod:ruby_ore_upper'})"},
        returnType = "boolean"
    )
    boolean registerRemovalFeatures(@NotNull String blockId, @NotNull String[] features);

    @LuaDoc(
        description = "Register a dimension and its biome tag so ores/removals resolve for dimensions "
            + "beyond the 3 vanilla defaults (e.g. a modded mining dimension). "
            + "Dimension existence itself is verified platform-side; unknown dimensions resolve empty.",
        params = {"dimension: string - dimension ID (e.g. 'twilightforest:twilight_forest')",
                  "biomeTag: string - biome tag or list holder (e.g. '#twilightforest:is_twilight_forest')"},
        returnType = "boolean"
    )
    boolean registerDimensionBiomeTag(@NotNull String dimension, @NotNull String biomeTag);

    @LuaDoc(
        description = "List known dimension IDs and their biome tags (3 vanilla defaults plus registrations).",
        returnType = "table"
    )
    @NotNull
    Map<String, String> getKnownDimensions();

    @LuaDoc(
        description = "NOT SUPPORTED: querying live biome data requires a platform PAL capability that does "
            + "not exist yet. Always throws UnsupportedOperationException (loud STUB); "
            + "recorded as follow-up work. Use modifyBiome with explicit biome IDs instead.",
        params = {"biomeId: string"},
        returnType = "table"
    )
    @NotNull
    Map<String, Object> getBiome(@NotNull String biomeId);
}

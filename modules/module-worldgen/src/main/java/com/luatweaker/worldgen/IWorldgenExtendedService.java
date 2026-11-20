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
            + "count=10, size=8, minHeight=-64, maxHeight=32, "
            + "heightType='uniform'|'trapezoid'|'biased_to_bottom'|'very_biased_to_bottom', "
            + "anchorMin/anchorMax='absolute'|'above_bottom'|'below_top', "
            + "rarity=N (1-in-N chunk chance, mutually exclusive with count), "
            + "discardChance=0.0-1.0 (air-exposure thinning), scattered=true (exposed-vein type), "
            + "inner=8 (biased height inner bound) }. "
            + "Simple veins need only block+biomes; the rest mirrors vanilla ore distributions.",
        params = {"featureId: string - id for the generated feature (e.g. 'mymod:ruby_vein')",
                  "spec: table - block/biomes/placement fields as described"},
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
        description = "Register a GTNH-style mixed vein: one blob mixing several ores by weight "
            + "(custom luatweaker:mixed_vein feature). Spec table: { ores={{block='modid:ore', weight=70}, ...} "
            + "(required, weight defaults to 1), replace='stone'|'nether'|'end', "
            + "biomes={'minecraft:plains', ...} or biome='...' (required), step='underground_ores', "
            + "count=10 (or rarity=N, mutually exclusive), size=24 (1-256), "
            + "minHeight/maxHeight, heightType/anchorMin/anchorMax, discardChance, inner }. "
            + "Placement fields behave exactly like addFeature.",
        params = {"veinId: string - id for the generated vein (e.g. 'mymod:magnetite_vein')",
                  "spec: table - ores/replace/biomes/placement fields as described"},
        returnType = "void"
    )
    void addMixedVein(@NotNull String veinId, @NotNull ILuaTable spec);

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
        description = "Read a registered biome's live data from the server registry: temperature, "
            + "downfall, precipitation, sky/fog/water colors, grass/foliage overrides and "
            + "per-category spawn entries (entity, weight, minGroup, maxGroup). Throws for an "
            + "unknown biome id; works only while a server is running.",
        params = {"biomeId: string"},
        returnType = "table"
    )
    @NotNull
    Map<String, Object> getBiome(@NotNull String biomeId);
}

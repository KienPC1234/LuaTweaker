# Worldgen Service (`Worldgen`)

> **Stage:** `src/startup` (registrations) + `src/server` (runtime)
> **Access:** `local Worldgen = require("LuaTweaker.Worldgen")`
> **Impl:** `modules/module-worldgen` (`WorldgenServiceImpl` + `IWorldgenExtendedService`)

The `Worldgen` service records ore/vegetation/removal/modifier registrations in
pure Java and builds datapack JSON for the platform provider, which feeds each
`relPath -> json` pair to `IDatapackService#addData`
(`luatweaker/neoforge/biome_modifier/*.json`,
`luatweaker/worldgen/(configured|placed)_feature/*.json`).

All biome-modifier field names are verified against NeoForge 21.1.242
(`biomes` / `features` / singular `step` for `add_features`; plural `steps`
list for `remove_features`) and against vanilla 1.21.1 datapack JSON
(ore/flower/patch features, rarity filter, height providers, block predicates).

---

## 1. Ore generation

```lua
Worldgen:AddOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 10)
Worldgen:AddOreBiomeFiltered("mymod:ruby_ore", "minecraft:overworld",
    -64, 32, 8, 10, { "minecraft:plains", "minecraft:forest" })
Worldgen:RemoveOre("minecraft:coal_ore", "minecraft:overworld")
```

`RemoveOre` resolves placed-feature IDs through a data-driven registry:
10 vanilla ores by default, overridable per block:

```lua
Worldgen:RegisterRemovalFeatures("mymod:ruby_ore", { "mymod:ruby_ore_upper" })
```

Dimensions resolve through a second registry (3 vanilla defaults + custom):

```lua
Worldgen:RegisterDimensionBiomeTag("twilightforest:twilight_forest", "#twilightforest:is_twilight_forest")
local dims = Worldgen:GetKnownDimensions() -- dimension -> biome tag/list
```

Dimension *existence* is verified platform-side; unregistered dimensions
resolve empty and the provider skips them loudly.

## 2. Vegetation (flower AND patch)

```lua
Worldgen:AddVegetation("minecraft:poppy", 0.1, { "minecraft:plains" })       -- minecraft:flower
Worldgen:AddVegetationPatch("minecraft:short_grass", 0.2, { "minecraft:plains" }) -- minecraft:random_patch
```

Both shapes match the vanilla JSONs (`tries` 64/32, typed
`simple_state_provider` for `to_place`, `blocks: "minecraft:air"` predicate).

## 3. Biome modification and custom features

```lua
Worldgen:ModifyBiome("minecraft:plains", {
    biomes = { "minecraft:plains", "minecraft:forest" }, -- or biome = "...", "#" tags allowed
    add = { { feature = "mymod:ruby_vein", step = "underground_ores" } },
    step = "underground_ores",        -- default for adds
    remove = { "minecraft:ore_dirt" },
    removeSteps = { "underground_ores" },
})

Worldgen:AddFeature("mymod:ruby_vein", {
    block = "mymod:ruby_ore",         -- required
    biomes = { "minecraft:plains" },  -- or biome = "..."; "#" tags allowed
    step = "underground_ores",        -- verified generation-step name
    replace = "stone",                -- "stone" | "nether" | "end" target rock
    count = 10, size = 8, minHeight = -64, maxHeight = 32,
})

-- Full distribution control (all optional, vanilla-verified shapes).
-- Large rare exposed veins, diamond_large-style:
Worldgen:AddFeature("mymod:deep_ruby", {
    block = "mymod:deep_ruby_ore",
    biomes = { "minecraft:plains" },
    step = "underground_ores",
    size = 12,
    rarity = 9,                       -- 1-in-9 chunk chance INSTEAD of count (mutually exclusive)
    heightType = "trapezoid",          -- uniform | trapezoid | biased_to_bottom | very_biased_to_bottom
    anchorMin = "above_bottom",        -- absolute | above_bottom | below_top (per bound)
    anchorMax = "absolute",
    minHeight = -80, maxHeight = 80,
    discardChance = 0.7,               -- 0.0-1.0, thins veins touching air (diamond_large uses 0.7)
    scattered = true,                  -- scattered_ore type: exposed veins, ancient-debris-style
    -- inner = 8,                      -- only for biased_* height (default 8, like vanilla spring_lava)
})
```

`step` values are validated against the 11 verified
`GenerationStep$Decoration` serialized names; unknown steps fail fast in Lua.
`Worldgen:AddOre` stays the simple path (uniform height + count); `AddFeature`
is the full-power path — every vanilla ore distribution (coal_upper,
iron_middle, copper_large, diamond_large/buried, ancient_debris_large)
is expressible through it.

## 4. Mixed veins (GTNH-style multi-ore blobs)

One vanilla `ore` feature can never mix ores: `OreFeature` places the FIRST
matching target, so a second block for the same rock would silently never spawn.
`AddMixedVein` instead emits a custom `luatweaker:mixed_vein` configured feature
whose generator rolls every candidate block by weight — true per-vein ratios:

```lua
Worldgen:AddMixedVein("mymod:magnetite_vein", {
    ores = {
        { block = "mymod:magnetite_ore",          weight = 70 },
        { block = "mymod:vanadium_magnetite_ore", weight = 20 },
        { block = "mymod:gold_ore",               weight = 10 },
    },
    biomes = { "minecraft:plains" },
    size = 32, count = 4,               -- placement fields work exactly like AddFeature
    minHeight = -32, maxHeight = 64,
})
```

`ores` needs at least one entry (`weight` defaults to 1); `size` caps at 256
(codec bound — vanilla ore caps at 64, veins are meant to be bigger).
Placement (`count`/`rarity`, height providers, `discardChance`) is shared code
with `AddFeature`, so it cannot drift.

`step` values are validated against the 11 verified
`GenerationStep$Decoration` serialized names; unknown steps fail fast in Lua.
Adds are grouped per step (one `add_features` modifier each);
removes emit one `remove_features` modifier with a plural `steps` list.

```lua
local summary = Worldgen:GetModifications()
-- { ores=.., vegetation=.., removals=.., biomeModifiers=.., customFeatures=..,
--   mixedVeins=.., rawConfigured=.., rawPlaced=.. }
Worldgen:ClearAll() -- clears pending entries; registries persist across reloads

-- Low-level queries (no datapack side effects):
local oreFeatures = Worldgen:GetRemovalFeatures("minecraft:coal_ore")
-- { "minecraft:ore_coal_upper", "minecraft:ore_coal_lower" } (overrides first)
local tag = Worldgen:GetDimensionBiomeTag("minecraft:overworld") -- "#minecraft:is_overworld" or nil
```

## 5. Raw configured/placed features (full power, validated)

When no builder fits (trees, geodes, custom distributions), write verbatim
vanilla JSON. The JSON must parse and carry the right `type`/`feature`
field; unknown vanilla types and duplicate ids fail fast in Lua:

```lua
Worldgen:AddRawConfiguredFeature("mymod:crystal_tree", [[
  { "type": "minecraft:tree", "config": { ... } }
]])
Worldgen:AddRawPlacedFeature("mymod:crystal_patch", [[
  { "feature": "luatweaker:mymod_crystal_tree",
    "placement": [ { "type": "minecraft:rarity_filter", "chance": 32 },
                   { "type": "minecraft:in_square" },
                   { "type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING" },
                   { "type": "minecraft:biome" } ] }
]])
Worldgen:ModifyBiome("minecraft:plains", {
    biomes = { "mymod:crystal_meadow" },
    add = { { feature = "luatweaker:mymod_crystal_patch", step = "vegetal_decoration" } },
})
```

Copy shapes from the vanilla 1.21.1 datapack (`data/minecraft/worldgen/`
in the client jar); 55 configured types are accepted.

After every load the provider fires `WorldgenApplied`
(`{ ores, vegetation, removals, biomeModifiers, raw, mixedVeins }` counts):

```lua
local Events = require("LuaTweaker.Events")
Events:Listen("WorldgenApplied", function(payload)
    print("worldgen files: " .. tostring(payload.biomeModifiers))
end)
```

## 6. Touching other mods' ores

You cannot edit another mod's placed-feature JSON, but you can remove it and
add your own — biome modifiers apply at datapack load regardless of mod.
Two steps, both fail-fast in Lua:

1. Find their placed-feature IDs: unzip the mod jar and list
   `data/<ns>/worldgen/placed_feature/*.json`; the matching configured feature
   (same name under `configured_feature/`) shows which block it places.
   Vanilla reference: `Worldgen:GetRemovalFeatures("minecraft:coal_ore")`.
2. Remove theirs, add yours at your own rate:

```lua
-- e.g. halve someone else's tin: remove both placements, re-add one smaller vein
Worldgen:RegisterRemovalFeatures("thermalfoundation:tin_ore",
    { "thermalfoundation:ore_tin_upper", "thermalfoundation:ore_tin_lower" })
Worldgen:RemoveOre("thermalfoundation:tin_ore", "minecraft:overworld")
Worldgen:AddFeature("mymod:tin_patch", {
    block = "thermalfoundation:tin_ore",
    biomes = { "#minecraft:is_overworld" },
    count = 4, size = 6, minHeight = -32, maxHeight = 64,
})
```

`ModifyBiome({ remove = { ... } })` removes arbitrary placed features by ID
without needing a block mapping; `RegisterRemovalFeatures` is only the
`block -> feature IDs` lookup that `RemoveOre` consumes. Overrides persist for
the session — re-register after every reload (startup scripts run each load).

## 7. Explicitly NOT supported

`Worldgen:GetBiome(id)` throws `UnsupportedOperationException` (loud STUB +
error log). Live biome queries need a platform PAL capability that does not
exist yet — follow-up work. Use `ModifyBiome` with explicit biome IDs instead.

## 5. Platform integration notes (provider owner)

- `buildBiomeModifierFiles()` returns every new `relPath -> json` pair;
  the provider feeds them to `IDatapackService#addData` next to its ore files,
  so `ModifyBiome`/`AddFeature` registrations materialize on every load.
- The provider should delegate to `resolveRemovalFeatures`,
  `resolveDimensionBiomeTag`, and `buildVegetationConfiguredJson` instead of
  its hardcoded table / 3-dimension check / flower-only builder.
- `replace = "end"` (`end_stone`) follows provider convention; UNVERIFIED —
  no vanilla end-ore precedent exists in the 1.21.1 datapack.

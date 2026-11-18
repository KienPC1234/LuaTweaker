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
```

`step` values are validated against the 11 verified
`GenerationStep$Decoration` serialized names; unknown steps fail fast in Lua.
Adds are grouped per step (one `add_features` modifier each);
removes emit one `remove_features` modifier with a plural `steps` list.

```lua
local summary = Worldgen:GetModifications()
-- { ores=.., vegetation=.., removals=.., biomeModifiers=.., customFeatures=.. }
Worldgen:ClearAll() -- clears pending entries; registries persist across reloads
```

## 4. Explicitly NOT supported

`Worldgen:GetBiome(id)` throws `UnsupportedOperationException` (loud STUB +
error log). Live biome queries need a platform PAL capability that does not
exist yet — follow-up work. Use `ModifyBiome` with explicit biome IDs instead.

## 5. Platform integration notes (provider owner)

- `buildBiomeModifierFiles()` returns every new `relPath -> json` pair;
  the provider feeds them to `IDatapackService#addData` next to its ore files.
- The provider should delegate to `resolveRemovalFeatures`,
  `resolveDimensionBiomeTag`, and `buildVegetationConfiguredJson` instead of
  its hardcoded table / 3-dimension check / flower-only builder.
- KNOWN DIVERGENCE (jar-verified): the provider emits singular `"step"`
  for `remove_features`, but the 21.1.242 codec requires plural `"steps"`
  (list). This module already emits the correct plural form.
- `replace = "end"` (`end_stone`) follows provider convention; UNVERIFIED —
  no vanilla end-ore precedent exists in the 1.21.1 datapack.

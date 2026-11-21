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

## 7. Live biome queries (`GetBiome`)

`Worldgen:GetBiome(id)` reads the biome from the **running server's registry**, so datapack
biomes (including Lua-defined ones) and biome-modifier changes are visible:

```lua
local plains = Worldgen:GetBiome("minecraft:plains")
print(plains.temperature, plains.downfall, plains.hasPrecipitation)
print(plains.skyColor, plains.waterColor)
for _, spawn in ipairs(plains.spawners.monster or {}) do
    print(spawn.entity, spawn.weight, spawn.minGroup, spawn.maxGroup)
end
```

Returned fields: `id`, `hasPrecipitation`, `temperature`, `downfall`,
`temperatureModifier`, `skyColor`, `fogColor`, `waterColor`, `waterFogColor`, optional
`grassColor`/`foliageColor` overrides, and `spawners` keyed by category
(`monster`, `creature`, ...). An unknown id throws `IllegalArgumentException`; the call
requires a running server.

## 8. Batched noise sampling (`Noise:Fill2D` / `Fill3D`)

Calling `Noise:fBm` once per block is the main cost of a Lua terrain generator: every call
crosses the Lua/Java boundary. `Fill2D`/`Fill3D` sample a whole grid in **one** native call
and return a 1-indexed array:

```lua
-- 16 x 16 chunk height field, one call
local heights = Noise:Fill2D(chunkX * 16, chunkZ * 16, 16, 16, {
    kind = 'warped_fbm', octaves = 6, lacunarity = 2.0, gain = 0.55,
    warpStrength = 40, warpFrequency = 0.01,
})

-- 3D density volume for caves/overhangs: 8 x 8 x 8 samples
local density = Noise:Fill3D(x0, y0, z0, 8, 8, 8, {
    kind = 'fbm3', octaves = 4, gain = 0.5,
})
```

Indexing: `Fill2D` → `index = z * width + x + 1` (x fastest); `Fill3D` →
`index = (y * sizeZ + z) * sizeX + x + 1`.

Spec fields: `kind` (`fbm`, `simplex`, `ridged`, `voronoi`, `warped_fbm`, `fbm3`,
`simplex3`), `octaves`, `lacunarity`, `gain`, `frequency`, `jitter`, `returnType`
(voronoi 0/1/2), `warpStrength`, `warpFrequency`. `frequency` scales world coordinates for
every kind. `warpStrength` is a **world-space displacement in blocks** (internally converted
to noise units, so it does not depend on `frequency`). One call is capped at 1,048,576 samples;
invalid kinds/sizes raise a Lua error instead of silently returning zeros.

`Fill2D` already reads the same noise state as the scalar functions, so it is
seed-reproducible (`Noise:SetSeed`).

**Coarse lattice sampling.** Low-frequency fields have wavelengths of hundreds of blocks, so
sampling them on a 4-block lattice is visually identical to per-block sampling. `Fill2DSmooth`
does the lattice sampling **and** the bilinear expansion natively:

```lua
-- 5 x 5 noise samples expanded to a 16 x 16 field, indices as usual
local continent = Noise:Fill2DSmooth(baseX, baseZ, 16, 16, continentSpec, 4, 4)
-- the optional stepX/stepZ default to 4; width/height must be multiples of them
local coarse = Noise:Fill2D(baseX, baseZ, 4, 4, spec, 4, 4) -- raw lattice, no interpolation
```

The lattice ends exactly on the next chunk's first column, so neighbouring chunks evaluate
the same border samples and join without a seam. Use `step = 4` for continentalness, erosion,
rivers and lakes, `2` for mountain ridges and fine detail.

## 9. Batch terrain per chunk (`Dimensions:SetChunkTerrain`)

The per-column callbacks cost up to 768 Lua calls per chunk: `SetTerrainGenerator` 256,
`SetBlockPicker` 256, `SetStrataPicker` 256, plus 16 biome-provider calls. `SetChunkTerrain`
replaces all three terrain callbacks with **one** call per chunk:

```lua
Dimensions:SetChunkTerrain("mymod:islands", function(chunkX, chunkZ, ctx)
    local baseX, baseZ = chunkX * 16, chunkZ * 16
    local base = Noise:Fill2D(baseX, baseZ, 16, 16, { kind = 'warped_fbm', octaves = 5 })
    local ridge = Noise:Fill2D(baseX, baseZ, 16, 16, { kind = 'ridged', octaves = 4 })

    local heights, surfaces, overrides = {}, {}, {}
    for z = 0, 15 do
        for x = 0, 15 do
            local i = z * 16 + x + 1
            local h = ctx.seaLevel + base[i] * 20 + ridge[i] * 90
            heights[i] = math.floor(h)
            surfaces[i] = h > 200 and 'minecraft:snow_block' or 'mymod:stone'
        end
    end
    -- Sparse 3D escape hatch: column index -> { [y] = blockId }
    overrides[1] = { [ctx.minY + 2] = 'minecraft:lava' }
    return heights, surfaces, overrides
end)
```

Contract:

- `heights`: 256 numbers, `index = z * 16 + x + 1`, **absolute Y**. A value `<= ctx.minY`
  means a void column. Missing or non-numeric entries also generate as void and log once per
  chunk; returning no heights table aborts chunk generation loudly instead of producing a
  flat world.
- `surfaces`: optional 256 block ids for the top block; `nil` or invalid entries fall back to
  the config `surfaceBlock`.
- `overrides`: optional `{[column] = {[y] = blockId}}`, column 1..256. `minecraft:air`
  carves. Y outside the build range is dropped, and one chunk is capped at 262,144 override
  entries (exceeding the cap aborts loudly).
- `ctx`: `chunkX`, `chunkZ`, `minX`, `minZ`, `minY`, `maxY`, `seaLevel`, `biomeSize`.

`SetChunkTerrain` takes precedence over `SetTerrainGenerator`/`SetBlockPicker`/
`SetStrataPicker`: while it is active the per-column callbacks are not called (a warning is
logged when both are registered). Calling `SetTerrainGenerator` again switches the dimension
back to the per-column mode. Columns the callback leaves below `ctx.seaLevel` are still
filled with water, and an explicit `minecraft:air` override wins over that fill.

## 10. Batch biomes per chunk (`Dimensions:SetBiomeBatch`)

One call per chunk instead of one per quart (16 per chunk):

```lua
Dimensions:SetBiomeBatch("mymod:islands", function(chunkX, chunkZ, ctx)
    local bands = Noise:Fill2D(chunkX * 16, chunkZ * 16, 16, 16, {
        kind = 'simplex', frequency = 0.004,
    })
    local biomes = {}
    for qz = 0, 3 do
        for qx = 0, 3 do
            local i = qz * 4 + qx + 1
            local value = bands[(qz * 4) * 16 + (qx * 4) + 1]
            biomes[i] = value > 0.3 and 'mymod:forest'
                or value < -0.3 and 'mymod:wastes'
                or 'mymod:plains'
        end
    end
    return biomes
end)
```

The table holds up to 16 biome ids for the 4 x 4 quart grid of the chunk
(`index = qz * 4 + qx + 1`). A nil or invalid entry falls back to the weighted pick from the
config biomes. Results are cached per chunk and dropped when the dimension is reloaded.
`SetBiomeProvider` and `SetBiomeBatch` are mutually exclusive - the last one registered wins.

## 11. Declarative terrain (`Dimensions:SetTerrainSpec`)

A spec is pure data evaluated natively by the chunk generator: **zero Lua calls per chunk**.

```lua
Dimensions:SetTerrainSpec("mymod:peaks", {
    seaLevel = 70,
    height = {
        { kind = 'fbm',        amp = 30, frequency = 0.001, octaves = 6 },
        { kind = 'ridged',     amp = 70, frequency = 0.004, octaves = 5 },
        { kind = 'warped_fbm', amp = 15, frequency = 0.02, warpStrength = 40 },
    },
    -- optional: maps the normalized sum (-1..1) to an absolute surface Y
    spline = { { -0.6, 30 }, { 0.0, 70 }, { 0.5, 150 }, { 0.9, 260 } },
    surface = {
        { block = 'minecraft:snow_block',   maxDepth = 1, minY = 200 },
        { block = 'mymod:alpine_grass',     maxDepth = 1 },
        { block = 'mymod:cliff_stone',      maxDepth = 4, minSlope = 0.8 },
        { block = 'mymod:rock',             maxDepth = 4 },
    },
})
```

Semantics:

- `height` layers must be 2D kinds. `sum = SUM(amp * noise(x * frequency, z * frequency))`.
  Without a spline, `surfaceY = seaLevel + sum`. With a spline, the surface is the spline
  output for `sum / SUM(|amp|)`; inputs must be strictly increasing and out-of-range values
  clamp to the end points.
- `surface` rules are ordered; the first match wins. `maxDepth` is the deepest offset below
  the column top (`0` = top block only), `minY`/`maxY` are inclusive, and `minSlope` filters
  cliffs (blocks per block). Depths no rule matches fall back to the config
  `surfaceBlock`/`subsurfaceBlock`/`fillerBlock` stack.
- `seaLevel` optionally overrides the dimension config sea level for water fill and spawns.
  `water = false` disables water fill entirely, so void/sky dimensions stay empty below the
  terrain instead of filling up to sea level.
- `platforms = { { x, z, radius, bottomY, topY } }` forces solid cylinders into the terrain
  (spawn islands and pillars). Density mode fills between `bottomY` and `topY`; height mode
  raises the column to at least `topY`.
- Slopes come from an 18 x 18 height grid (the chunk plus a one-block border), so border
  columns see their real neighbours. A chunk costs 324 height samples, not 256 separate Lua
  calls.
- Broken specs fail at registration with the offending field named: unknown noise kind,
  non-ascending spline, invalid block id, `seaLevel` outside the build range, non-numeric
  amplitude, and so on.

Precedence: the last terrain setter called wins. `SetTerrainSpec` replaces the Lua terrain
generator and the per-column pickers for that dimension; it does not touch the biome source.

### 3D density: overhangs, caves and floating islands

Add a `density` section to switch the whole dimension from a heightmap to a real 3D field:

```lua
Dimensions:SetTerrainSpec("mymod:skylands", {
    seaLevel = 0,                       -- water fill stays off for a sky dimension
    water = false,
    density = {                         -- height layer is optional when density is set
        layers = {
            { kind = 'fbm3',     amp = 1.0, frequency = 0.012, octaves = 5 },
            { kind = 'simplex3', amp = 0.35, frequency = 0.04 },
        },
        threshold = 0.15,
        -- Solid only where the threshold dips below zero: a floating band around y = 180.
        thresholdSpline = { { 0, 1.2 }, { 150, 0.6 }, { 180, -0.6 }, { 220, 0.6 }, { 319, 1.2 } },
        cellSize = 4,
        cellHeight = 4,
    },
    -- Guaranteed spawn island: a solid cylinder independent of the noise.
    platforms = { { x = 0, z = 0, radius = 14, bottomY = 168, topY = 182 } },
    surface = {
        { block = 'mymod:sky_grass',  maxDepth = 1 },
        { block = 'mymod:sky_stone',  maxDepth = 4 },
    },
})
```

`density.layers` must be 3D kinds (`fbm3`, `simplex3`). A block is solid when
`SUM(amp * noise3(x, y, z)) > threshold + thresholdSpline(y)`. Raising `threshold` carves more
away (cave systems, broken arches); a `thresholdSpline` that is high away from a Y band and low
inside it yields floating islands or overhangs. Density noise is sampled every `cellSize`
blocks horizontally and `cellHeight` blocks vertically (4 x 4 by default) and interpolated
trilinearly per block, so a 384-high chunk costs about 2,000 noise samples instead of 90,000.

With density the per-column `height` is the highest solid block, so heightmap consumers
(spawn checks, `getBaseHeight`) keep working, and water only fills above that block - caves
stay dry. `surface` rules apply to the top solid block of each column.

`platforms` forces solid cylinders into the terrain
(`{ x, z, radius, bottomY, topY }`). With a density field the cylinder is solid between
`bottomY` and `topY`; in height mode the column is raised to at least `topY`. Use one for a
spawn island in a void dimension, or several for pillars; they are pure data and cost one
distance check per block.

There are still no vanilla-style structure sets or region pre-generation; use `SetCaveSpec`
for caves, `SetChunkTerrain` overrides for local block edits, or the structure APIs.

## 12. Caves and world queries

### Caves (`Dimensions:SetCaveSpec`)

`SetCaveSpec` adds a native 3D carver to **any** terrain mode (legacy callbacks, chunk batch or
declarative spec). The spec is the same density table used by terrain specs; a block becomes
air where `SUM(amp * noise3) > threshold + thresholdSpline(y)`:

```lua
Dimensions:SetCaveSpec("mymod:wilds", {
    density = {
        layers = {
            { kind = 'fbm3',     amp = 1.0, frequency = 0.02, octaves = 3 },
            { kind = 'simplex3', amp = 0.4, frequency = 0.08 },
        },
        threshold = 0.75,
        thresholdSpline = { { -64, 0.0 }, { 64, 0.0 }, { 100, 0.15 },
                            { 160, 0.45 }, { 319, 0.9 } },
        cellSize = 4,
        cellHeight = 8,
    },
})
```

Rules that keep the world usable:

- the top 3 blocks of every column and the bottom 3 blocks above `minHeight` are never carved,
  so `getBaseHeight`/heightmaps and the bedrock floor stay valid;
- caves are dry - water is only filled above the column top, so there are no underground lakes;
- `cellSize = 4, cellHeight = 8` costs ~0.4 ms/chunk (~1,200 noise samples); `cellHeight = 4`
  gives finer caves for ~1.4 ms/chunk.

`SetCaveSpec` never changes the terrain, biome or surface configuration; it only removes blocks.
`Dimensions:GetDimension(id).hasCaveSpec` reports whether a carver is registered.

### World queries

These read the same pipeline that generates chunks (no Minecraft access, so they work from any
Lua thread):

| Function | Result |
|---|---|
| `Dimensions:GetColumnHeight(id, x, z)` | surface Y, `minY - 1` for void columns |
| `Dimensions:GetSurfaceBlock(id, x, z)` | top block id, `nil` for void columns |
| `Dimensions:GetSlope(id, x, z)` | local slope in blocks per block |
| `Dimensions:GetBiomeAt(id, x, z)` | biome id the biome source resolves |
| `Dimensions:PreviewChunk(id, cx, cz)` | `{ heights, surfaces, slopes, biomes }` for one chunk |
| `Dimensions:FindSurface(id, x, z, radius)` | nearest dry `{ x, y, z, block, biome }` or `nil` |

`PreviewChunk` evaluates the terrain and biome pipelines **once** (`heights` = 256 absolute
Y values, `surfaces` = 256 block ids, `slopes` = 256 numbers, `biomes` = 16 quart ids), so use
it for maps, spawn scans and diagnostics instead of 256 `GetColumnHeight` calls. A single
`GetColumnHeight` call in CHUNK mode runs the chunk callback once, and in a density dimension it
samples that chunk's density field once - fine for occasional queries, not for per-tick loops.

`FindSurface` scans chunks by ring (`radius` 0..64) and skips void columns and, when the
dimension fills water, columns below sea level. Use it to place structures, portals or a safe
spawn instead of trusting `(0, 0)`.

## 13. Measuring the Lua pipeline

`/lt dimension bench <id> [radius 0-8]` replays the active terrain pipeline and reports total
milliseconds, milliseconds per chunk and Lua calls per chunk. It is a developer measurement
of the Lua pipeline: block placement, lighting and feature generation are not included.

## 14. Minecraft generator contract (what is and is not implemented)

The Lua chunk generator is a real `ChunkGenerator`, not a wrapper around
`NoiseBasedChunkGenerator`. Audit against Minecraft 1.21.1 / NeoForge 21.1.242:

| Hook | Implementation |
|---|---|
| `fillFromNoise` | Lua batch / declarative spec / 3D density; writes solid blocks only |
| `createBiomes` | inherited: fills biomes from `LuaBiomeSource` |
| `buildSurface` | no-op: surfaces come from the config or spec rules |
| `applyCarvers` | no-op: no vanilla carvers; density threshold carves caves |
| `applyBiomeDecoration` | inherited: vanilla biome features (trees, grass, ores) **do** run |
| `createState` / `createReferences` | overridden: no vanilla structure sets (see below) |
| `spawnOriginalMobs` / `getMobsAt` | config `spawnEntities`; ocean columns are skipped |
| `getBaseHeight` | first free Y above solid/water, per the vanilla heightmap contract |
| `getBaseColumn` | full block-state column from `minY` to `maxY` |
| `getGenDepth` / `getMinY` / `getSeaLevel` | dimension config, with a spec `seaLevel` override |

Dimension type values are validated before the JSON is written, because vanilla
`DimensionType` throws at datapack load otherwise:

- `minHeight` and `maxHeight` must be multiples of 16;
- `height = maxHeight - minHeight` must be 16..2032 and `minHeight + height <= 2032`;
- `logicalHeight <= height`;
- `coordinateScale` must be in `1e-5..3e7`;
- `infiniburn` must be a `#namespace:tag`;
- dimension ids must be namespaced (`mymod:skylands`).

Intentional gaps:

- **Vanilla structure sets are disabled** (`createState` returns an empty set), so villages,
  mineshafts and similar structures do not generate into Lua terrain. Place builds with the
  structure APIs instead.
- No vanilla carvers (`applyCarvers` no-op); use the `density` threshold or chunk overrides.
- No vanilla surface rules, snow or ice (`buildSurface` no-op); use `surface` rules.
- No `NoiseGeneratorSettings`/`NoiseRouter`; `SetTerrainSpec` is the native equivalent.
- Dimension levels are created when the server starts. A dimension added by `/lt reload` is
  written to the virtual datapack but its level is not hot-loaded; restart the server.

## 15. Platform integration notes (provider owner)
- `buildBiomeModifierFiles()` returns every new `relPath -> json` pair;
  the provider feeds them to `IDatapackService#addData` next to its ore files,
  so `ModifyBiome`/`AddFeature` registrations materialize on every load.
- The provider should delegate to `resolveRemovalFeatures`,
  `resolveDimensionBiomeTag`, and `buildVegetationConfiguredJson` instead of
  its hardcoded table / 3-dimension check / flower-only builder.
- `replace = "end"` (`end_stone`) follows provider convention; UNVERIFIED —
  no vanilla end-ore precedent exists in the 1.21.1 datapack.

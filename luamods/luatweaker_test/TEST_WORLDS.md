# Vanilla showcase worlds

`src/startup/vanilla_worlds.lua` registers three dimensions that use **only vanilla blocks and
vanilla biomes**, one per terrain pipeline:

| Dimension | Pipeline | Lua calls / chunk | What it exercises |
|---|---|---|---|
| `luatweaker:grand_wilds` | `SetChunkTerrain` + `SetBiomeBatch` + `SetCaveSpec` | 2 | Mountains, winding rivers, lakes, native caves, snow caps, beaches, biome bands |
| `luatweaker:alpine_spec` | `SetTerrainSpec` (height + spline + surface rules) | 0 | Native height evaluation, slope-aware surface rules, spawn platform |
| `luatweaker:sky_isles` | `SetTerrainSpec` (3D density + platforms) | 0 | Floating islands, 3D density interpolation, guaranteed spawn island |

## How to test

This workspace uses **NeoForge 21.1.242 + ModDevGradle** (not Forge/ForgeGradle). The run tasks
are defined in `neoforge-platform/build.gradle` and both use the shared game directory
`<repo>/run` (`gameDirectory = file("../run")`).

1. Launch the client from the repo root:
   - Windows PowerShell / cmd: `.\gradlew.bat :neoforge-platform:runClient`
   - Git Bash / WSL / Linux / macOS: `./gradlew :neoforge-platform:runClient`
   First launch downloads the Minecraft/NeoForge artifacts (`createMinecraftArtifacts`).
2. Open a world (a **new** world is best - dimension levels are created at server start).
3. `/lt dimension list` - the three ids above must be listed.
4. Enter a world:
   - `/lt dimension tp luatweaker:grand_wilds`
   - `/lt dimension tp luatweaker:alpine_spec`
   - `/lt dimension tp luatweaker:sky_isles`
5. Measure the pipeline (no block writes, Lua cost only):
   - `/lt dimension bench luatweaker:grand_wilds 4`
   - `/lt dimension bench luatweaker:alpine_spec 4`
   - `/lt dimension bench luatweaker:sky_isles 4`
   Expected shape: `chunk-batch` ~2 Lua calls/chunk, `declarative-spec` 0.

If `tp` refuses, the log explains why (unsafe/void spawn); the sky world has a guaranteed
platform at (0, 0, y=182), the other two have spawn platforms too.

Terrain quality targets (measured from the callbacks over 81 chunks spread across +/-1024
blocks, seed default):

- `grand_wilds`: ~19% water columns (rivers + lakes included), median ground ~y106,
  p90 ~y156, peaks up to ~y243, ~35% of columns above y122; rivers are ~13 blocks wide at
  the median (p95 ~21) with bank steps of 1-5 blocks - no one-block chasms.
- `sky_isles`: ~78% void columns with separate islands between y156 and y199, not a slab.

The terrain stack uses domain-warped continentalness at a ~1800-block wavelength,
erosion-modulated ridged ranges at a ~700-block wavelength, rolling 250-block hills,
warped river networks with bounded incision, and shallow smooth-masked lakes. Tune it in
`vanilla_worlds.grand_wilds`: `continent_scale` / `continent_amp` (landmass size and height),
`ridge_scale` / `ridge_amp` (range spacing and height), `erosion_strength` (plains vs jagged),
`hill_amp`, `river_scale` / `river_blend` / `river_max_incise`, `lake_depth`.
`warpStrength` in the noise spec is a block-space displacement (not noise units).

### Performance

Each `grand_wilds` chunk is **two Lua calls** (terrain + biome batch). To keep that cheap:

- Broad noise fields are sampled on a 4-block lattice (2-block for ridges) and expanded
  natively with `Noise:Fill2DSmooth`, so one chunk costs 25-81 samples per field instead of
  256, and the interpolation never runs in Lua.
- The biome batch samples exactly the 4 x 4 quart lattice the biome source asks for
  (`Noise:Fill2D(..., 4, 4, spec, 4, 4)`) instead of a 16 x 16 grid.
- Noise spec tables are built once at registration, not per chunk.

Measured with `/lt dimension bench luatweaker:grand_wilds 2`: ~2.3 ms/chunk (Lua-side
terrain + biome only, no block writes). Sampling every field per block measures ~4 ms/chunk
on the same machine. The cave carver is native and adds ~0.4 ms/chunk at `cellHeight = 8`
(~1.4 ms at `cellHeight = 4`).

### Mobs

`grand_wilds` and `alpine_spec` register declarative spawn rules from the `spawn_rules` array
in their config: cows/sheep by day on grass, zombies underground and at night, bats in dark
caves (`grand_wilds`); mountain goats by day and strays at night (`alpine_spec`). Vanilla
still owns spawn caps, pack sizes, despawn and difficulty; the rules only choose which mob may
spawn where. Inspect them with `/lt spawn list luatweaker:grand_wilds`, check your current
block with `/lt spawn test`, or force one with `/lt spawn minecraft:zombie 3`.

If you tuned `vanilla_worlds` before this update, delete `run/luaconfig/luatweaker_test.json`
(or just its `vanilla_worlds` section) so the new defaults are written again. Existing chunks
keep their old terrain, so test the new formula in a fresh world (or after deleting the
dimension folders).

### Dedicated server

`.\gradlew.bat :neoforge-platform:runServer` uses the same `run/` directory. The first launch
stops until you accept the Minecraft EULA: create `run/eula.txt` containing `eula=true`.
To join from the dev client, also set `online-mode=false` in `run/server.properties` (the
offline dev player cannot authenticate), as described in the NeoForge 1.21.1 docs.

## Tuning

Every value lives in `luaconfig/luatweaker_test.json` under `vanilla_worlds` (the engine
deep-merges new default keys into an existing config, user values win). Examples:

- bigger landmasses / more open plains: `grand_wilds.continent_scale` (lower = larger),
  `continent_amp`, `continent_bias`
- mountains taller/steeper or farther apart: `grand_wilds.ridge_amp`, `ridge_power`,
  `ridge_scale` (lower = wider ranges), `erosion_strength`, `alpine_spec.peak_y`
- wider/narrower rivers: `grand_wilds.river_width`, `river_blend`, `river_depth`,
  `river_max_incise`, `river_scale`
- more/fewer islands: `sky_isles.threshold`, `band_bias`, `island_scale`

After editing: `/lt reload` rebuilds the dimension configs; already-generated chunks keep
their terrain, so test terrain changes in a fresh world.

## Known limits

- No vanilla carvers or structure sets in these Lua dimensions. Caves come from
  `Dimensions:SetCaveSpec` (the native 3D density carver); ores/trees come from the vanilla
  biome features.
- Vanilla feature placement can be sparse because the terrain is Lua-generated; that is
  expected, not an error.

## World queries

The dimension API can be sampled without generating chunks, which is handy for spawn logic,
maps and structures:

```lua
local height = Dimensions:GetColumnHeight("luatweaker:grand_wilds", 120, -40)
local block = Dimensions:GetSurfaceBlock("luatweaker:grand_wilds", 120, -40)
local biome = Dimensions:GetBiomeAt("luatweaker:grand_wilds", 120, -40)
local slope = Dimensions:GetSlope("luatweaker:grand_wilds", 120, -40)
local spot = Dimensions:FindSurface("luatweaker:grand_wilds", 0, 0, 32)
local preview = Dimensions:PreviewChunk("luatweaker:grand_wilds", 0, 0)
```

`PreviewChunk` runs the pipeline once and returns `heights` (256), `surfaces` (256),
`slopes` (256) and `biomes` (16).

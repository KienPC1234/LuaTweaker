-- ================================================================
-- LuaTweaker Test: Vanilla-block showcase worlds
-- Three dimensions that exercise every terrain pipeline mode with
-- vanilla blocks and vanilla biomes only:
--   * grand_wilds  - one Lua call per chunk (mountains + rivers + lakes)
--   * alpine_spec  - declarative spec, zero Lua calls per chunk (spline peaks)
--   * sky_isles    - declarative 3D density, zero Lua calls (floating islands)
-- Every tunable comes from mod:GetConfig().vanilla_worlds.
-- ================================================================
local Dimensions = require("LuaTweaker.Dimensions")
local Noise = require("LuaTweaker.Noise")
local SpawnRules = require("LuaTweaker.SpawnRules")

local cfg = mod and mod:GetConfig() or {}
local worlds = cfg.vanilla_worlds or {}

-- ==== HELPERS ====
local function num(section, key, fallback)
    local value = tonumber(section[key])
    if value == nil then
        return fallback
    end
    return value
end

local function smoothstep(t)
    if t <= 0 then
        return 0
    end
    if t >= 1 then
        return 1
    end
    return t * t * (3 - 2 * t)
end

local function clamp01(t)
    if t < 0 then
        return 0
    end
    if t > 1 then
        return 1
    end
    return t
end

-- A chunk column grid is 16 x 16. Low-frequency noise fields (continents, erosion, rivers)
-- have wavelengths of hundreds of blocks, so Noise:Fill2DSmooth samples them on a coarse
-- lattice and interpolates natively: same shape, one sample per cell instead of per block.
local CHUNK_SIZE = 16
-- Lattice spacing in blocks: broad fields move slowly, ridge crests and fine detail need
-- tighter cells. Both must divide CHUNK_SIZE.
local COARSE_STEP = 4
local FINE_STEP = 2
-- One biome quart is 4 blocks; the biome source asks for CHUNK_SIZE / QUART_CELLS cells per axis.
local QUART_BLOCKS = 4
local BIOME_CELLS = CHUNK_SIZE // QUART_BLOCKS

-- Shared biome list: every id a batch provider can return must be resolved
-- here at world load.
local BIOMES = {
    { id = "minecraft:plains", weight = 5 },
    { id = "minecraft:forest", weight = 4 },
    { id = "minecraft:jagged_peaks", weight = 3 },
    { id = "minecraft:river", weight = 1 },
    { id = "minecraft:snowy_plains", weight = 2 },
}

-- The sky dimension has no rivers, so the weighted fallback must not pick one.
local SKY_BIOMES = {
    { id = "minecraft:plains", weight = 5 },
    { id = "minecraft:forest", weight = 4 },
    { id = "minecraft:snowy_plains", weight = 2 },
}

-- ==== SHARED: CONFIGURED SPAWN RULES ====
---Registers this world's declarative spawn rules from `spawn_rules` in the config.
---Rules are plain data: vanilla's natural spawner evaluates them per candidate position and
---still owns caps, pack sizes, despawn and difficulty.
---@param id string dimension id
---@param c table that dimension's config section
local function addConfiguredSpawnRules(id, c)
    local rules = c.spawn_rules or {}
    for i = 1, #rules do
        SpawnRules:AddRule(id, rules[i])
    end
end

-- ==== SHARED: CAVE CARVER ====
---Carve 3D caves into any terrain mode with the native density carver.
---A block becomes air where the weighted 3D noise passes the threshold; the threshold
---spline raises the bar near the surface so mountains stay solid and caverns stay deep.
---@param id string dimension id
---@param c table that dimension's config section
local function addCaveCarver(id, c)
    Dimensions:SetCaveSpec(id, {
        density = {
            layers = {
                { kind = "fbm3", amp = 1.0, frequency = num(c, "cave_scale", 0.02),
                  octaves = num(c, "cave_octaves", 3) },
                { kind = "simplex3", amp = num(c, "cave_detail_amp", 0.4),
                  frequency = num(c, "cave_detail_scale", 0.08) },
            },
            threshold = num(c, "cave_threshold", 0.75),
            thresholdSpline = {
                { -64, 0.0 },
                { 64, 0.0 },
                { 100, 0.15 },
                { 160, 0.45 },
                { 319, 0.9 },
            },
            cellSize = num(c, "cave_cell_size", 4),
            cellHeight = num(c, "cave_cell_height", 8),
        },
    })
end

-- ==== WORLD 1: GRAND WILDS (chunk batch) ====
local function registerGrandWilds()
    local c = worlds.grand_wilds or {}
    local id = c.dimension_id or "luatweaker:grand_wilds"
    local sea = num(c, "sea_level", 62)

    Dimensions:Create(id, {
        hasSkyLight = true,
        natural = true,
        bedWorks = true,
        skyColor = num(c, "sky_color", 0x87CEEB),
        fogColor = num(c, "fog_color", 0xC8E4FF),
        ambientLight = 0.08,
        terrain = "custom",
        seaLevel = sea,
        minHeight = -64,
        maxHeight = 320,
        surfaceBlock = "minecraft:grass_block",
        subsurfaceBlock = "minecraft:dirt",
        fillerBlock = "minecraft:stone",
        waterBlock = "minecraft:water",
        hasBedrock = true,
        biomeSize = 8,
        spawnX = 0,
        spawnZ = 0,
        biomes = BIOMES,
    })

    -- Terrain scale: continents and mountain ranges are deliberately larger than one screen
    -- so the world reads as open space instead of a field of isolated spikes.
    local continentScale = num(c, "continent_scale", 0.00055)
    local continentAmp = num(c, "continent_amp", 90)
    local continentBias = num(c, "continent_bias", 44)
    local continentWarp = num(c, "continent_warp", 90)
    local continentWarpFreq = num(c, "continent_warp_frequency", 1.0)
    local ridgeScale = num(c, "ridge_scale", 0.0014)
    local ridgeAmp = num(c, "ridge_amp", 175)
    local ridgePower = num(c, "ridge_power", 1.2)
    local mountainBase = num(c, "mountain_base", 28)
    local erosionScale = num(c, "erosion_scale", 0.0011)
    local erosionStrength = num(c, "erosion_strength", 0.75)
    local hillScale = num(c, "hill_scale", 0.004)
    local hillAmp = num(c, "hill_amp", 16)
    local detailScale = num(c, "detail_scale", 0.02)
    local detailAmp = num(c, "detail_amp", 3.5)
    local riverScale = num(c, "river_scale", 0.0014)
    local riverWidth = num(c, "river_width", 0.012)
    local riverBlend = num(c, "river_blend", 0.12)
    local riverDepth = num(c, "river_depth", 4)
    local riverMaxHeight = num(c, "river_max_height", 18)
    local riverMaxIncise = num(c, "river_max_incise", 16)
    local riverWarp = num(c, "river_warp", 26)
    local riverWarpFreq = num(c, "river_warp_frequency", 1.0)
    local lakeScale = num(c, "lake_scale", 0.0025)
    local lakeThreshold = num(c, "lake_threshold", 0.70)
    local lakeFalloff = num(c, "lake_falloff", 0.18)
    local lakeDepth = num(c, "lake_depth", 3)
    local lakeMaxHeight = num(c, "lake_max_height", 8)
    local moistureScale = num(c, "moisture_scale", 0.0025)
    local snowLine = num(c, "snow_line", 185)
    local rockLine = num(c, "rock_line", 125)
    local beachBand = num(c, "beach_band", 3)
    local spawnRadius = num(c, "spawn_radius", 24)
    local spawnLift = num(c, "spawn_lift", 4)

    -- Noise specs are built ONCE. Noise:Fill2D parses the spec table on every call, so a
    -- table literal inside the callback would allocate and parse seven specs per chunk.
    local continentSpec = {
        kind = "warped_fbm", frequency = continentScale, octaves = 5, gain = 0.5,
        warpStrength = continentWarp, warpFrequency = continentWarpFreq,
    }
    local ridgeSpec = {
        kind = "ridged", frequency = ridgeScale, octaves = 5, lacunarity = 2.0,
    }
    local erosionSpec = {
        kind = "fbm", frequency = erosionScale, octaves = 4,
    }
    local hillSpec = {
        kind = "fbm", frequency = hillScale, octaves = 3,
    }
    local detailSpec = {
        kind = "fbm", frequency = detailScale, octaves = 3,
    }
    local riverSpec = {
        kind = "warped_fbm", frequency = riverScale, octaves = 2,
        warpStrength = riverWarp, warpFrequency = riverWarpFreq,
    }
    local lakeSpec = {
        kind = "fbm", frequency = lakeScale, octaves = 2,
    }
    local moistureSpec = {
        kind = "fbm", frequency = moistureScale, octaves = 3,
    }
    local biomePeakSpec = {
        kind = "ridged", frequency = ridgeScale, octaves = 4,
    }

    Dimensions:SetChunkTerrain(id, function(chunkX, chunkZ)
        local baseX = chunkX * CHUNK_SIZE
        local baseZ = chunkZ * CHUNK_SIZE

        -- Coarse lattices: 4-block cells for the broad fields, 2-block cells for ridges and
        -- fine detail so crests and hilltops keep their shape.
        local continent = Noise:Fill2DSmooth(baseX, baseZ, CHUNK_SIZE, CHUNK_SIZE, continentSpec,
            COARSE_STEP, COARSE_STEP)
        local ridge = Noise:Fill2DSmooth(baseX, baseZ, CHUNK_SIZE, CHUNK_SIZE, ridgeSpec,
            FINE_STEP, FINE_STEP)
        local erosion = Noise:Fill2DSmooth(baseX, baseZ, CHUNK_SIZE, CHUNK_SIZE, erosionSpec,
            COARSE_STEP, COARSE_STEP)
        local hills = Noise:Fill2DSmooth(baseX, baseZ, CHUNK_SIZE, CHUNK_SIZE, hillSpec,
            COARSE_STEP, COARSE_STEP)
        local detail = Noise:Fill2DSmooth(baseX, baseZ, CHUNK_SIZE, CHUNK_SIZE, detailSpec,
            FINE_STEP, FINE_STEP)
        local river = Noise:Fill2DSmooth(baseX, baseZ, CHUNK_SIZE, CHUNK_SIZE, riverSpec,
            COARSE_STEP, COARSE_STEP)
        local lake = Noise:Fill2DSmooth(baseX, baseZ, CHUNK_SIZE, CHUNK_SIZE, lakeSpec,
            COARSE_STEP, COARSE_STEP)

        local heights, surfaces = {}, {}
        for z = 0, CHUNK_SIZE - 1 do
            for x = 0, CHUNK_SIZE - 1 do
                local i = z * CHUNK_SIZE + x + 1
                local worldX = baseX + x
                local worldZ = baseZ + z

                local cont = continent[i]
                -- Steeper shelf below the coastline: oceans get deep, coasts stay gentle.
                local shaped = cont < 0 and cont * 1.6 or cont
                local baseHeight = sea + continentBias + shaped * continentAmp
                -- Mountains only grow well inland, so ranges never rise straight out of water.
                local inland = smoothstep(clamp01(cont * 2.5 - 0.05))
                -- Erosion chooses flat lowlands vs jagged ranges; without this contrast
                -- every square kilometre looked equally busy.
                local rugged = (1 - erosionStrength)
                    + erosionStrength * smoothstep(clamp01(0.55 + erosion[i] * 0.55))
                local crest = math.max(0, ridge[i]) ^ ridgePower
                local h = baseHeight
                    + inland * (mountainBase + crest * ridgeAmp) * rugged
                    + (1 - inland * 0.5) * hills[i] * hillAmp
                    + detail[i] * detailAmp

                -- Lakes: shallow basins in lowlands. The gate and the mask are both smooth,
                -- so a lake fades into its bank instead of ending in a vertical shaft.
                local lakeGate = 1 - smoothstep(clamp01((h - (sea + lakeMaxHeight)) / lakeMaxHeight))
                if lake[i] > lakeThreshold and lakeGate > 0 then
                    local lakeMask = smoothstep(clamp01((lake[i] - lakeThreshold) / lakeFalloff))
                    h = h + (sea - lakeDepth - h) * lakeMask * lakeGate
                end

                -- Rivers: meandering warped noise carved into a valley. Inside riverWidth the
                -- bed is fully cut; outside it the carve follows t*t, which keeps the water
                -- surface narrow and the banks smooth instead of flooding the lowlands.
                local distance = math.abs(river[i])
                local riverGate = 1 - smoothstep(clamp01((h - (sea + riverMaxHeight)) / riverMaxHeight))
                if distance < riverBlend and riverGate > 0 then
                    local t = (riverBlend - distance) / math.max(0.0001, riverBlend - riverWidth)
                    local blend = clamp01(t) * clamp01(t) * riverGate
                    local target = math.max(sea - riverDepth, h - riverMaxIncise)
                    h = h + (target - h) * blend
                end

                -- Spawn plateau: raise only, never cut into a mountain, and fade to zero at
                -- the edge so the origin is dry without becoming a crater or a cut cylinder.
                if spawnRadius > 0 then
                    local distSq = worldX * worldX + worldZ * worldZ
                    if distSq < spawnRadius * spawnRadius then
                        local t = smoothstep(1 - math.sqrt(distSq) / spawnRadius)
                        local lift = (sea + spawnLift - h) * t
                        if lift > 0 then
                            h = h + lift
                        end
                    end
                end

                heights[i] = math.floor(h)
                if h >= snowLine then
                    surfaces[i] = "minecraft:snow_block"
                elseif h >= rockLine then
                    surfaces[i] = "minecraft:stone"
                elseif h <= sea + beachBand and h >= sea - 1 then
                    surfaces[i] = "minecraft:sand"
                elseif h < sea then
                    surfaces[i] = "minecraft:gravel"
                else
                    surfaces[i] = "minecraft:grass_block"
                end
            end
        end
        return heights, surfaces
    end)

    Dimensions:SetBiomeBatch(id, function(chunkX, chunkZ)
        local baseX = chunkX * CHUNK_SIZE
        local baseZ = chunkZ * CHUNK_SIZE
        -- The biome source asks for exactly one sample per 4-block quart, so sample that
        -- lattice directly: BIOME_CELLS x BIOME_CELLS samples instead of a 16 x 16 grid.
        local river = Noise:Fill2D(baseX, baseZ, BIOME_CELLS, BIOME_CELLS, riverSpec,
            QUART_BLOCKS, QUART_BLOCKS)
        local peaks = Noise:Fill2D(baseX, baseZ, BIOME_CELLS, BIOME_CELLS, biomePeakSpec,
            QUART_BLOCKS, QUART_BLOCKS)
        local moisture = Noise:Fill2D(baseX, baseZ, BIOME_CELLS, BIOME_CELLS, moistureSpec,
            QUART_BLOCKS, QUART_BLOCKS)

        local biomes = {}
        for qz = 0, BIOME_CELLS - 1 do
            for qx = 0, BIOME_CELLS - 1 do
                local index = qz * BIOME_CELLS + qx + 1
                local worldX = baseX + qx * QUART_BLOCKS
                local worldZ = baseZ + qz * QUART_BLOCKS
                local biome = "minecraft:plains"
                if spawnRadius > 0 and worldX * worldX + worldZ * worldZ <= spawnRadius * spawnRadius then
                    -- Keep the spawn area green and tree-friendly instead of a river bank.
                    biome = "minecraft:plains"
                elseif math.abs(river[index]) < riverWidth then
                    biome = "minecraft:river"
                elseif peaks[index] > 0.78 and moisture[index] < 0 then
                    biome = "minecraft:snowy_plains"
                elseif peaks[index] > 0.66 then
                    biome = "minecraft:jagged_peaks"
                elseif moisture[index] > 0.2 then
                    biome = "minecraft:forest"
                end
                biomes[index] = biome
            end
        end
        return biomes
    end)

    Dimensions:SetSpawnPoint(id, 0, 0)
    addCaveCarver(id, c)
    addConfiguredSpawnRules(id, c)
    return id
end

-- ==== WORLD 2: ALPINE SPEC (declarative height + spline) ====
local function registerAlpineSpec()
    local c = worlds.alpine_spec or {}
    local id = c.dimension_id or "luatweaker:alpine_spec"
    local sea = num(c, "sea_level", 62)

    Dimensions:Create(id, {
        hasSkyLight = true,
        natural = true,
        bedWorks = true,
        skyColor = num(c, "sky_color", 0x9BC7F0),
        fogColor = num(c, "fog_color", 0xDCEBFA),
        ambientLight = 0.08,
        terrain = "custom",
        seaLevel = sea,
        minHeight = -64,
        maxHeight = 320,
        surfaceBlock = "minecraft:grass_block",
        subsurfaceBlock = "minecraft:dirt",
        fillerBlock = "minecraft:stone",
        waterBlock = "minecraft:water",
        hasBedrock = true,
        biomeSize = 12,
        spawnX = 0,
        spawnZ = 0,
        biomes = BIOMES,
    })

    Dimensions:SetTerrainSpec(id, {
        seaLevel = sea,
        height = {
            { kind = "fbm", amp = num(c, "continent_amp", 45), frequency = num(c, "continent_scale", 0.0012),
              octaves = 5 },
            { kind = "ridged", amp = num(c, "ridge_amp", 110), frequency = num(c, "ridge_scale", 0.0032),
              octaves = 5 },
            { kind = "warped_fbm", amp = num(c, "detail_amp", 14), frequency = num(c, "detail_scale", 0.012),
              octaves = 3, warpStrength = 30 },
        },
        spline = {
            { -0.85, num(c, "low_y", 18) },
            { -0.35, num(c, "coast_y", 48) },
            { 0.0, sea + 8 },
            { 0.35, num(c, "mid_y", 110) },
            { 0.7, num(c, "high_y", 175) },
            { 1.0, num(c, "peak_y", 250) },
        },
        surface = {
            { block = "minecraft:snow_block", maxDepth = 1, minY = num(c, "snow_line", 200) },
            { block = "minecraft:stone", maxDepth = 3, minSlope = num(c, "cliff_slope", 1.1) },
            { block = "minecraft:sand", maxDepth = 1, minY = sea - 3, maxY = sea + 2 },
            { block = "minecraft:grass_block", maxDepth = 0 },
        },
        -- Guaranteed dry spawn ground even if the noise puts a lake at the origin.
        platforms = {
            { x = 0, z = 0, radius = num(c, "spawn_radius", 20),
              bottomY = sea, topY = sea + num(c, "spawn_lift", 4) },
        },
    })

    local ridgeScale = num(c, "ridge_scale", 0.0032)
    Dimensions:SetBiomeBatch(id, function(chunkX, chunkZ, ctx)
        local baseX = chunkX * 16
        local baseZ = chunkZ * 16
        local peaks = Noise:Fill2D(baseX, baseZ, 16, 16, {
            kind = "ridged", frequency = ridgeScale, octaves = 4,
        })
        local moisture = Noise:Fill2D(baseX, baseZ, 16, 16, {
            kind = "fbm", frequency = 0.004, octaves = 3,
        })
        local biomes = {}
        for qz = 0, 3 do
            for qx = 0, 3 do
                local index = qz * 4 + qx + 1
                local sample = (qz * 4) * 16 + (qx * 4) + 1
                local biome = "minecraft:plains"
                if peaks[sample] > 0.75 then
                    biome = moisture[sample] < 0 and "minecraft:snowy_plains" or "minecraft:jagged_peaks"
                elseif moisture[sample] > 0.2 then
                    biome = "minecraft:forest"
                end
                biomes[index] = biome
            end
        end
        return biomes
    end)

    Dimensions:SetSpawnPoint(id, 0, 0)
    addCaveCarver(id, c)
    addConfiguredSpawnRules(id, c)
    return id
end

-- ==== WORLD 3: SKY ISLES (declarative 3D density) ====
local function registerSkyIsles()
    local c = worlds.sky_isles or {}
    local id = c.dimension_id or "luatweaker:sky_isles"
    local bandY = num(c, "band_y", 180)
    local spawnRadius = num(c, "spawn_radius", 14)
    local spawnTop = num(c, "spawn_top_y", 182)

    Dimensions:Create(id, {
        hasSkyLight = true,
        natural = true,
        bedWorks = false,
        skyColor = num(c, "sky_color", 0xBDE0FF),
        fogColor = num(c, "fog_color", 0xE8F4FF),
        ambientLight = 0.25,
        terrain = "custom",
        seaLevel = num(c, "sea_level", 63),
        minHeight = -64,
        maxHeight = 320,
        surfaceBlock = "minecraft:grass_block",
        subsurfaceBlock = "minecraft:dirt",
        fillerBlock = "minecraft:stone",
        waterBlock = "minecraft:water",
        hasBedrock = false,
        biomeSize = 16,
        spawnX = 0,
        spawnZ = 0,
        biomes = SKY_BIOMES,
    })

    Dimensions:SetTerrainSpec(id, {
        water = false,
        seaLevel = num(c, "sea_level", 63),
        density = {
            layers = {
                { kind = "fbm3", amp = num(c, "island_amp", 1.0), frequency = num(c, "island_scale", 0.015),
                  octaves = 5 },
                { kind = "simplex3", amp = num(c, "detail_amp", 0.25), frequency = num(c, "detail_scale", 0.045) },
            },
            threshold = num(c, "threshold", 0.65),
            thresholdSpline = {
                { 0, num(c, "limit_bias", 1.6) },
                { bandY - 60, num(c, "outer_bias", 0.9) },
                { bandY - 15, num(c, "band_bias", -0.25) },
                { bandY + 20, num(c, "band_bias", -0.25) },
                { bandY + 70, num(c, "outer_bias", 0.9) },
                { 319, num(c, "limit_bias", 1.6) },
            },
            cellSize = num(c, "cell_size", 4),
            cellHeight = num(c, "cell_height", 4),
        },
        surface = {
            { block = "minecraft:snow_block", maxDepth = 0, minY = bandY + 30 },
            { block = "minecraft:grass_block", maxDepth = 0 },
        },
        -- Guaranteed spawn island: a solid cylinder independent of the noise.
        platforms = {
            { x = 0, z = 0, radius = spawnRadius,
              bottomY = spawnTop - num(c, "spawn_thickness", 14), topY = spawnTop },
        },
    })

    Dimensions:SetSpawnPoint(id, 0, 0)
    return id
end

-- ==== REGISTRATION ====
local wildsId = registerGrandWilds()
local alpineId = registerAlpineSpec()
local skyId = registerSkyIsles()

print("[LuaTweakerTest] Vanilla showcase worlds registered: " .. wildsId
    .. ", " .. alpineId .. ", " .. skyId)

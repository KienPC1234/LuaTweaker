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

    local continentScale = num(c, "continent_scale", 0.0011)
    local continentAmp = num(c, "continent_amp", 85)
    local continentBias = num(c, "continent_bias", 34)
    local continentWarp = num(c, "continent_warp", 80)
    local continentWarpFreq = num(c, "continent_warp_frequency", 1.0)
    local ridgeScale = num(c, "ridge_scale", 0.0035)
    local ridgeAmp = num(c, "ridge_amp", 110)
    local ridgePower = num(c, "ridge_power", 1.0)
    local mountainBase = num(c, "mountain_base", 30)
    local erosionScale = num(c, "erosion_scale", 0.0022)
    local erosionStrength = num(c, "erosion_strength", 0.45)
    local hillScale = num(c, "hill_scale", 0.009)
    local hillAmp = num(c, "hill_amp", 12)
    local detailScale = num(c, "detail_scale", 0.03)
    local detailAmp = num(c, "detail_amp", 4)
    local riverScale = num(c, "river_scale", 0.0022)
    local riverWidth = num(c, "river_width", 0.02)
    local riverBlend = num(c, "river_blend", 0.18)
    local riverDepth = num(c, "river_depth", 4)
    local riverMaxHeight = num(c, "river_max_height", 24)
    local riverMaxIncise = num(c, "river_max_incise", 20)
    local riverWarp = num(c, "river_warp", 23)
    local riverWarpFreq = num(c, "river_warp_frequency", 1.0)
    local lakeScale = num(c, "lake_scale", 0.004)
    local lakeThreshold = num(c, "lake_threshold", 0.68)
    local lakeFalloff = num(c, "lake_falloff", 0.2)
    local lakeDepth = num(c, "lake_depth", 3)
    local lakeMaxHeight = num(c, "lake_max_height", 10)
    local snowLine = num(c, "snow_line", 180)
    local rockLine = num(c, "rock_line", 120)
    local beachBand = num(c, "beach_band", 3)
    local spawnRadius = num(c, "spawn_radius", 24)
    local spawnLift = num(c, "spawn_lift", 4)

    Dimensions:SetChunkTerrain(id, function(chunkX, chunkZ, ctx)
        local baseX = chunkX * 16
        local baseZ = chunkZ * 16
        local continent = Noise:Fill2D(baseX, baseZ, 16, 16, {
            kind = "warped_fbm", frequency = continentScale, octaves = 5, gain = 0.5,
            warpStrength = continentWarp, warpFrequency = continentWarpFreq,
        })
        local ridge = Noise:Fill2D(baseX, baseZ, 16, 16, {
            kind = "ridged", frequency = ridgeScale, octaves = 5, lacunarity = 2.0,
        })
        local erosion = Noise:Fill2D(baseX, baseZ, 16, 16, {
            kind = "fbm", frequency = erosionScale, octaves = 4,
        })
        local hills = Noise:Fill2D(baseX, baseZ, 16, 16, {
            kind = "fbm", frequency = hillScale, octaves = 3,
        })
        local detail = Noise:Fill2D(baseX, baseZ, 16, 16, {
            kind = "fbm", frequency = detailScale, octaves = 3,
        })
        local river = Noise:Fill2D(baseX, baseZ, 16, 16, {
            kind = "warped_fbm", frequency = riverScale, octaves = 2,
            warpStrength = riverWarp, warpFrequency = riverWarpFreq,
        })
        local lake = Noise:Fill2D(baseX, baseZ, 16, 16, {
            kind = "fbm", frequency = lakeScale, octaves = 2,
        })

        local heights, surfaces = {}, {}
        for z = 0, 15 do
            for x = 0, 15 do
                local i = z * 16 + x + 1
                local worldX = baseX + x
                local worldZ = baseZ + z

                -- Continent: broad land above sea, steeper continental shelf below it.
                -- Mountains grow with inland-ness, so peaks never stick out of the ocean alone.
                local continentValue = continent[i]
                local crest = math.max(0, ridge[i]) ^ ridgePower
                local baseHeight = sea + continentBias + continentValue * continentAmp
                local inland = math.max(0, continentValue)
                local rise = smoothstep(math.min(1, inland * 1.4))
                -- Erosion flattens some regions and leaves others jagged, like a real range.
                local eroded = (1 - erosionStrength) + erosionStrength * smoothstep(0.5 + erosion[i] * 0.5)
                local h = baseHeight + rise * (mountainBase + crest * ridgeAmp) * eroded
                    + (1 - rise) * hills[i] * hillAmp
                    + detail[i] * detailAmp

                -- Lakes: shallow basins in lowlands, with a smooth bank instead of a
                -- one-block vertical shaft. Deep lake holes were the 100+ block cliffs.
                if lake[i] > lakeThreshold and h <= sea + lakeMaxHeight then
                    local lakeMask = smoothstep(math.min(1, (lake[i] - lakeThreshold) / lakeFalloff))
                    local lakeBed = sea - lakeDepth
                    h = h + (lakeBed - h) * lakeMask
                end

                -- Rivers: meandering warped noise, carved only in lowlands. The bed is
                -- limited to riverMaxIncise below the local surface, so a river is a
                -- shallow valley rather than a 1-block-wide chasm.
                local distance = math.abs(river[i])
                if distance < riverBlend and h <= sea + riverMaxHeight then
                    local t = (riverBlend - distance) / math.max(0.0001, riverBlend - riverWidth)
                    t = math.max(0, math.min(1, t))
                    local blend = smoothstep(t)
                    local riverBed = sea - riverDepth
                    local target = math.max(riverBed, h - riverMaxIncise)
                    h = h + (target - h) * blend
                end

                -- Spawn plateau: smoothly lift the origin so it never looks like a cut cylinder.
                if spawnRadius > 0 then
                    local distSq = worldX * worldX + worldZ * worldZ
                    if distSq < spawnRadius * spawnRadius then
                        local t = 1 - math.sqrt(distSq) / spawnRadius
                        h = h + (sea + spawnLift - h) * smoothstep(t)
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

    Dimensions:SetBiomeBatch(id, function(chunkX, chunkZ, ctx)
        local baseX = chunkX * 16
        local baseZ = chunkZ * 16
        local river = Noise:Fill2D(baseX, baseZ, 16, 16, {
            kind = "warped_fbm", frequency = riverScale, octaves = 2,
            warpStrength = riverWarp, warpFrequency = riverWarpFreq,
        })
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
                local worldX = baseX + qx * 4
                local worldZ = baseZ + qz * 4
                local biome = "minecraft:plains"
                if spawnRadius > 0 and worldX * worldX + worldZ * worldZ <= spawnRadius * spawnRadius then
                    -- Keep the spawn area green and tree-friendly instead of a river bank.
                    biome = "minecraft:plains"
                elseif math.abs(river[sample]) < riverWidth then
                    biome = "minecraft:river"
                elseif peaks[sample] > 0.78 and moisture[sample] < 0 then
                    biome = "minecraft:snowy_plains"
                elseif peaks[sample] > 0.66 then
                    biome = "minecraft:jagged_peaks"
                elseif moisture[sample] > 0.2 then
                    biome = "minecraft:forest"
                end
                biomes[index] = biome
            end
        end
        return biomes
    end)

    Dimensions:SetSpawnPoint(id, 0, 0)
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

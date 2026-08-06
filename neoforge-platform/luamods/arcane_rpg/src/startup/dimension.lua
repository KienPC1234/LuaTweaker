-- ================================================================
-- ARCANE RPG: Crystal Realm Dimension
-- Custom dimension with Lua terrain generation (fBm/ridged/voronoi),
-- datapack biomes and a portal block.
--
-- LOW-LEVEL PHILOSOPHY: the engine only provides primitives
-- (SetTerrainGenerator, SetBlockPicker, SetStrataPicker, SetBiomeProvider).
-- EVERYTHING else - ores, caves, lakes, trees, buildings, floating
-- islands - is written here, in Lua, by the mod author.
-- ================================================================
local Content = require("LuaTweaker.Content")
local Dimensions = require("LuaTweaker.Dimensions")
local Noise = require("LuaTweaker.Noise")
local Datapack = require("LuaTweaker.Datapack")
local SpawnRules = require("LuaTweaker.SpawnRules")
local Biomes = require("LuaTweaker.Biomes")
local Events = require("LuaTweaker.Events")

-- ==== CONFIG (mod:GetConfig() -> crystal_realm section) ====
local cfg = mod and mod:GetConfig() or {}
local realm = cfg.crystal_realm or {}
local dimensionId = realm.dimension_id or "luatweaker:crystal_realm"
local portalBlockId = "luatweaker:crystal_portal"

-- ==== DIMENSION BLOCKS ====
Content.NewBlock("crystal_stone")
    :Hardness(3.0)
    :Resistance(15.0)
    :LightLevel(2)
    :SoundType("STONE")
    :RequiresTool(true)
    :MineableWith("pickaxe")
    :MiningLevel("iron")
    :CreativeTab("arcane_tab")
    :Register()

Content.NewBlock("crystal_grass")
    :Hardness(1.5)
    :LightLevel(4)
    :SoundType("GRASS")
    :Drop("luatweaker:crystal_dirt")
    :CreativeTab("arcane_tab")
    :Register()

Content.NewBlock("crystal_dirt")
    :Hardness(1.2)
    :SoundType("GRASS")
    :CreativeTab("arcane_tab")
    :Register()

Content.NewBlock("crystal_peak")
    :Hardness(6.0)
    :Resistance(25.0)
    :LightLevel(6)
    :SoundType("STONE")
    :RequiresTool(true)
    :MineableWith("pickaxe")
    :MiningLevel("diamond")
    :CreativeTab("arcane_tab")
    :Register()

Content.NewBlock("crystal_log")
    :Hardness(2.5)
    :Resistance(8.0)
    :SoundType("WOOD")
    :MineableWith("axe")
    :CreativeTab("arcane_tab")
    :Register()

Content.NewBlock("crystal_leaves")
    :Hardness(0.4)
    :SoundType("GLASS")
    :LightLevel(3)
    :CreativeTab("arcane_tab")
    :Register()

-- ==== PORTAL BLOCK: right-click teleports into the Crystal Realm ====
local function makePortalBlock(blockId, dimensionId, message)
    Content.NewBlock(blockId)
        :Hardness(-1)
        :Resistance(6000000.0)
        :LightLevel(15)
        :SoundType("GLASS")
        :CreativeTab("arcane_tab")
        :OnRightClick(function(player, blockState)
            local info = Dimensions:GetDimension(dimensionId)
            local target = info and info.portals and info.portals[blockId]
            if not target then
                player:sendActionBar("§cThe portal is inert: dimension '" .. dimensionId .. "' is not registered.")
                return true
            end
            local ok, err = pcall(function()
                Dimensions:TeleportTo(player, target)
            end)
            if ok then
                player:sendActionBar(message)
            else
                player:sendActionBar("§c" .. tostring(err))
            end
            return true
        end)
        :Register()
end

makePortalBlock("luatweaker:crystal_portal", dimensionId, "§bEntering the Crystal Realm...")

-- ==== DIMENSION BIOMES (datapack JSON via the virtual datapack) ====
Datapack:AddData("luatweaker/worldgen/biome/crystal_plains.json", [[{
  "has_precipitation": true,
  "temperature": 0.7,
  "downfall": 0.4,
  "effects": {
    "sky_color": 16751103,
    "fog_color": 11194367,
    "water_color": 4159204,
    "water_fog_color": 329011,
    "grass_color": 8969983,
    "foliage_color": 8969983
  },
  "spawners": {},
  "spawn_costs": {},
  "carvers": {},
  "features": []
}]])

Datapack:AddData("luatweaker/worldgen/biome/crystal_forest.json", [[{
  "has_precipitation": true,
  "temperature": 0.5,
  "downfall": 0.8,
  "effects": {
    "sky_color": 16751103,
    "fog_color": 11194367,
    "water_color": 4159204,
    "water_fog_color": 329011,
    "grass_color": 5686527,
    "foliage_color": 5686527
  },
  "spawners": {},
  "spawn_costs": {},
  "carvers": {},
  "features": []
}]])

Datapack:AddData("luatweaker/worldgen/biome/void_wastes.json", [[{
  "has_precipitation": false,
  "temperature": 0.1,
  "downfall": 0.0,
  "effects": {
    "sky_color": 4207412,
    "fog_color": 3158064,
    "water_color": 2498607,
    "water_fog_color": 2498607
  },
  "spawners": {},
  "spawn_costs": {},
  "carvers": {},
  "features": []
}]])

-- ==== DIMENSION REGISTRATION (data only - no behavior here) ====
Dimensions:Create(dimensionId, {
    -- Dimension type properties
    hasSkyLight = true,
    hasCeiling = false,
    ultraWarm = false,
    natural = true,
    coordinateScale = 1.0,
    bedWorks = true,
    respawnAnchorWorks = false,

    -- Visual (skyColor drives the custom sky disc, fog from the biomes)
    skyColor = realm.sky_color or 0xFF88FF,
    fogColor = realm.fog_color or 0xAADDFF,
    ambientLight = realm.ambient_light or 0.12,

    -- Terrain bounds (the SHAPES come from SetTerrainGenerator below)
    terrain = "custom",
    seaLevel = realm.sea_level or 63,
    minHeight = realm.min_height or -64,
    maxHeight = realm.max_height or 320,

    -- Surface blocks
    surfaceBlock = "luatweaker:crystal_grass",
    subsurfaceBlock = "luatweaker:crystal_dirt",
    fillerBlock = "luatweaker:crystal_stone",
    waterBlock = realm.water_block or "minecraft:water",
    hasBedrock = realm.has_bedrock or false,

    -- Biome regions: columns are grouped into cells of biome_size blocks
    biomeSize = realm.biome_size or 8,

    -- Custom spawn point (used by Dimensions:TeleportTo and /lt dimension tp)
    spawnX = realm.spawn_x,
    spawnZ = realm.spawn_z,

    -- Biomes (weights drive the deterministic weighted pick)
    biomes = {
        { id = "luatweaker:crystal_plains", weight = 5 },
        { id = "luatweaker:crystal_forest", weight = 3 },
        { id = "luatweaker:void_wastes", weight = 1 }
    },

    -- Vanilla natural spawner data (entity + weight + group sizes)
    spawnEntities = {
        { entity = "luatweaker:crystal_golem", weight = 3, minGroup = 1, maxGroup = 1 },
        { entity = "minecraft:zombie", weight = 8, minGroup = 1, maxGroup = 3 },
        { entity = "minecraft:phantom", weight = 2, minGroup = 1, maxGroup = 2 },
        { entity = "luatweaker:crystal_beast", weight = 6, minGroup = 1, maxGroup = 2 },
        { entity = "luatweaker:crystal_creeper", weight = 4, minGroup = 1, maxGroup = 2 }
    }
})

-- ==== TERRAIN GENERATOR (the tool; all tunables from config) ====
local continentScale = realm.continent_scale or 0.001
local continentAmplitude = realm.continent_amplitude or 50
local ridgeScale = realm.ridge_scale or 0.005
local ridgeAmplitude = realm.ridge_amplitude or 35
local ridgeOctaves = realm.ridge_octaves or 5
local detailScale = realm.detail_scale or 0.02
local detailAmplitude = realm.detail_amplitude or 3
local spireThreshold = realm.spire_threshold or 0.05
local spireHeight = realm.spire_height or 20
local lowLandHeight = realm.low_land_height or 70
local highLandHeight = realm.high_land_height or 120
local lakeScale = realm.lake_scale or 0.012
local lakeThreshold = realm.lake_threshold or 0.6
local seaLevel = realm.sea_level or 63

Dimensions:SetTerrainGenerator(dimensionId, function(x, z, baseHeight)
    -- Layer 1: large continent shapes (fBm)
    local continent = Noise:fBm(x * continentScale, z * continentScale, 6, 2.0, 0.5)

    -- Layer 2: crystal mountain ridges (ridged noise)
    local ridges = Noise:ridged(x * ridgeScale, z * ridgeScale, ridgeOctaves, 1.0, 2.0)

    -- Layer 3: fine surface detail
    local detail = Noise:fBm(x * detailScale, z * detailScale, 3, 2.0, 0.5)

    -- Layer 4: voronoi crystal spires
    local crystal = Noise:voronoi(x * 0.05, z * 0.05, 0.7, 0)

    -- Combine layers
    local height = baseHeight + continent * continentAmplitude
        + ridges * ridgeAmplitude
        + detail * detailAmplitude
    if crystal < spireThreshold then
        height = height + spireHeight
    end

    -- Water lakes: a lake noise pocket lowers the surface below sea level,
    -- and the engine's water fill turns the basin into a lake (in Lua).
    local lake = Noise:simplex(x * lakeScale, z * lakeScale, 1.0)
    if lake > lakeThreshold then
        local basinDepth = 2 + math.floor(math.abs(Noise:simplex(x * 0.07, z * 0.07, 1.0)) * 5)
        height = math.min(height, seaLevel - basinDepth)
    end

    -- Block selection by height
    local block = "luatweaker:crystal_stone"
    if height < lowLandHeight then
        block = "luatweaker:crystal_dirt"
    elseif height > highLandHeight then
        block = "luatweaker:crystal_peak"
    end

    return math.floor(height), block
end)

-- ==== BIOME PROVIDER (deterministic simplex bands) ====
Dimensions:SetBiomeProvider(dimensionId, function(x, z)
    local band = Noise:simplex(x * 0.002, z * 0.002, 1.0)
    if band < -0.3 then
        return "luatweaker:void_wastes"
    elseif band > 0.3 then
        return "luatweaker:crystal_forest"
    end
    return "luatweaker:crystal_plains"
end)

-- ==== BLOCK PICKER (the tool: per-column 3D overrides) ====
-- Everything the user sees is authored here: caves, ore veins, lava
-- pockets, water lakes (lower the column into the sea) and floating
-- debris. Returns a sparse table {[y] = blockId}; "minecraft:air" carves.
local caveScale = realm.cave_scale or 0.03
local caveThreshold = realm.cave_threshold or 0.35
local caveSize = realm.cave_size or 4
local veinThreshold = realm.vein_threshold or 0.25
local lavaThreshold = realm.lava_threshold or 0.5

Dimensions:SetBlockPicker(dimensionId, function(x, z, surfaceY, minY)
    local overrides = {}

    -- Crystal caves: air pockets at varying depths
    local cave = Noise:simplex(x * caveScale, z * caveScale, 1.0)
    if cave > caveThreshold then
        local depth = 10 + math.floor(math.abs(Noise:simplex(x * 0.1, z * 0.1, 1.0)) * 20)
        for dy = 0, caveSize - 1 do
            local y = surfaceY - depth + dy
            if y > minY + 1 then
                overrides[y] = "minecraft:air"
            end
        end
    end

    -- Crystal ore veins deep underground
    local vein = Noise:simplex(x * 0.15, z * 0.15, 1.0)
    if vein > veinThreshold then
        local veinY = surfaceY - 20 - math.floor(math.abs(Noise:simplex(x * 0.4, z * 0.4, 1.0)) * 30)
        overrides[veinY] = "luatweaker:crystal_ore"
        if veinY > minY + 2 then
            overrides[veinY - 1] = "luatweaker:crystal_ore"
        end
    end

    -- Lava pockets near the bottom of the world
    if Noise:simplex(x * 0.2, z * 0.2, 1.0) > lavaThreshold then
        overrides[minY + 2] = "minecraft:lava"
    end

    -- Crystal shrubs on the plains (single-block vegetation, ours in Lua)
    local shrub = Noise:simplex(x * 0.1, z * 0.1, 1.0)
    if shrub > (realm.shrub_threshold or 0.55) and surfaceY > 0 then
        overrides[surfaceY + 1] = "luatweaker:crystal_leaves"
    end

    -- Crystal trees in the forest: one trunk column + leaf cap (ours in Lua)
    local tree = Noise:simplex(x * 0.05, z * 0.05, 1.0)
    if tree > (realm.tree_threshold or 0.45) then
        local trunkHeight = 4 + math.floor(math.abs(Noise:simplex(x * 0.3, z * 0.3, 1.0)) * 3)
        for dy = 1, trunkHeight do
            overrides[surfaceY + dy] = "luatweaker:crystal_log"
        end
        overrides[surfaceY + trunkHeight + 1] = "luatweaker:crystal_leaves"
    end

    return overrides
end)

-- ==== SPAWN POINT ====
if realm.spawn_x ~= nil and realm.spawn_z ~= nil then
    Dimensions:SetSpawnPoint(dimensionId, realm.spawn_x, realm.spawn_z)
end

-- ==== PORTAL MAPPING ====
Dimensions:RegisterPortal(portalBlockId, dimensionId)

-- ==== BIOMES MODULE (data tool: vanilla biome spawner entries) ====
Biomes:AddSpawn("luatweaker:crystal_plains", "monster", "luatweaker:crystal_golem", 3, 1, 1)
Biomes:AddSpawn("luatweaker:void_wastes", "monster", "minecraft:phantom", 5, 1, 2)
Biomes:AddSpawn("luatweaker:crystal_forest", "monster", "luatweaker:crystal_beast", 8, 1, 2)
Biomes:AddSpawn("luatweaker:void_wastes", "monster", "luatweaker:crystal_creeper", 6, 1, 2)

-- ==== SPAWN RULES: full-control handler (user code decides every spawn) ====
SpawnRules:RegisterHandler("luatweaker:crystal_realm", function(dimensionId, players)
    local spawns = {}
    if #players > 0 then
        local p = players[1]
        -- Nighttime: phantoms dive from above the player (chance from config)
        if math.random() < (realm.handler_spawn_chance or 0.1) then
            spawns[1] = {
                entity = "minecraft:phantom",
                x = p.X + math.random(-8, 8),
                y = p.Y + 20,
                z = p.Z + math.random(-8, 8)
            }
        end
    end
    return spawns
end)

-- ==== WORLD EVENTS ====
Events:Listen("DimensionEntered", function(event)
    print("[ArcaneRPG] " .. event.player.Name .. " entered '" .. event.dimensionId
        .. "' from '" .. event.fromDimensionId .. "'")
end)

-- ==== NBT STRUCTURE PLACEMENT (low-level tool) ====
-- World:PlaceStructure(templateId, x, y, z, rotation) loads the NBT template
-- from data/arcane_rpg/structures/*.nbt (generated by tools/generate_structures.py)
-- and places it at the given position. The mod author decides when and where.
Events:Listen("DimensionEntered", function(event)
    if event.dimensionId ~= "luatweaker:crystal_skylands" then
        return true
    end
    -- A crystal monolith rises at the spawn island when a player arrives.
    local placed = World:PlaceStructure("arcane_rpg:sky_monolith", 12, 181, 8, 90)
    if placed then
        print("[ArcaneRPG] The sky monolith hums with crystal energy.")
    else
        print("[ArcaneRPG] Sky monolith template not found (run tools/generate_structures.py)")
    end
    return true
end)

-- Ban phantoms from spawning in the Crystal Realm (cancellable event)
Events:Listen("MobSpawnAttempt", function(event)
    if event.dimensionId == "luatweaker:crystal_realm" and event.entityId == "minecraft:phantom" then
        return false
    end
    return true
end)

-- ================================================================
-- SECOND DIMENSION: Crystal Skylands (floating islands)
-- ================================================================
local skylandsId = realm.skylands_id or "luatweaker:crystal_skylands"
local skylands = cfg.crystal_skylands or {}

makePortalBlock("luatweaker:crystal_sky_portal", skylandsId, "§dAscending to the Crystal Skylands...")

Datapack:AddData("luatweaker/worldgen/biome/skyland_plains.json", [[{
  "has_precipitation": true,
  "temperature": 0.6,
  "downfall": 0.3,
  "effects": {
    "sky_color": 12451583,
    "fog_color": 9418751,
    "water_color": 4159204,
    "water_fog_color": 329011,
    "grass_color": 10092543,
    "foliage_color": 10092543
  },
  "spawners": {},
  "spawn_costs": {},
  "carvers": {},
  "features": []
}]])

Dimensions:Create(skylandsId, {
    hasSkyLight = true,
    hasCeiling = false,
    ultraWarm = false,
    natural = true,
    bedWorks = false,

    -- Visual: pale dream-like sky
    skyColor = skylands.sky_color or 0xBDE0FF,
    fogColor = skylands.fog_color or 0xE0F0FF,
    ambientLight = 0.3,

    -- Terrain: floating islands between y=base and y=base+heightRange.
    -- The island SHAPES come from SetTerrainGenerator + SetBlockPicker.
    terrain = "custom",
    seaLevel = skylands.sea_level or 0,
    minHeight = -64,
    maxHeight = 320,

    surfaceBlock = "luatweaker:crystal_grass",
    subsurfaceBlock = "luatweaker:crystal_dirt",
    fillerBlock = "luatweaker:crystal_stone",

    biomeSize = 8,
    spawnX = 0,
    spawnZ = 0,

    biomes = {
        { id = "luatweaker:skyland_plains", weight = 5 },
        { id = "luatweaker:crystal_forest", weight = 2 }
    },

    -- Vanilla natural spawner data
    spawnEntities = {
        { entity = "minecraft:phantom", weight = 3, minGroup = 1, maxGroup = 2 }
    }
})

-- Island placement + shape, entirely in Lua: noise decides whether a
-- column is an island slab (solid from bottom to top) or open void.
Dimensions:SetTerrainGenerator(skylandsId, function(x, z, baseHeight)
    local shape = Noise:simplex(x * (skylands.scale or 0.012), z * (skylands.scale or 0.012), 1.0)
    local threshold = skylands.threshold or 0.55
    if shape > threshold then
        local t = (shape - threshold) / (1.0 - threshold)
        local baseY = skylands.base_y or 180
        local topY = baseY + math.floor(t * (skylands.height_range or 90))
        return topY, "luatweaker:crystal_grass"
    end
    -- Void column: the picker below fills it with air.
    return -64, "luatweaker:crystal_stone"
end)

Dimensions:SetBlockPicker(skylandsId, function(x, z, surfaceY, minY)
    local overrides = {}

    -- Void column (terrain generator returned minY): clear everything so
    -- nothing but floating debris exists between the islands.
    if surfaceY <= minY + 1 then
        for y = minY, minY + 128 do
            overrides[y] = "minecraft:air"
        end
        local debris = Noise:simplex(x * 0.05, z * 0.05, 1.0)
        if debris > 0.75 then
            overrides[150] = "luatweaker:crystal_block"
        end
        return overrides
    end

    -- Island slab: solid only `thickness` blocks under the top, air below.
    local thickness = skylands.thickness or 10
    for y = minY, surfaceY - thickness do
        overrides[y] = "minecraft:air"
    end

    -- Crystal veins inside islands
    local vein = Noise:simplex(x * 0.15, z * 0.15, 1.0)
    if vein > 0.4 then
        overrides[surfaceY - 4] = "luatweaker:crystal_ore"
        overrides[surfaceY - 5] = "luatweaker:crystal_ore"
    end
    return overrides
end)

Dimensions:SetSpawnPoint(skylandsId, 0, 0)
Dimensions:RegisterPortal("luatweaker:crystal_sky_portal", skylandsId)

-- Full-control handler for the skylands: parrots flock near a player
SpawnRules:RegisterHandler("luatweaker:crystal_skylands", function(dimensionId, players)
    local spawns = {}
    if #players > 0 then
        local p = players[1]
        for i = 1, 2 do
            spawns[i] = {
                entity = "minecraft:parrot",
                x = p.X + math.random(-8, 8),
                y = p.Y + 6,
                z = p.Z + math.random(-8, 8)
            }
        end
    end
    return spawns
end)

print("[ArcaneRPG] Crystal Realm dimension registered: " .. dimensionId
    .. " (terrain generator, block picker, biome provider, spawn handler)")
print("[ArcaneRPG] Crystal Skylands dimension registered: " .. skylandsId
    .. " (floating islands in Lua, parrot spawn handler)")

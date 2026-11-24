-- ===================================================================
-- LuaTweaker Test Suite: Worldgen Service
-- Exercises ore placement, biome-filtered ore, vegetation and the
-- pending-modification summary (pure Java, no server needed).
-- ===================================================================
local Worldgen = require("LuaTweaker.Worldgen")

local function check(label, cond)
    if cond then
        print("[Test][Worldgen] " .. label .. " OK")
    else
        print("[ERROR][Test] " .. label .. " FAILED")
    end
end

-- Ore placement
Worldgen:AddOre("luatweaker_test:test_ore", "minecraft:overworld", -64, 32, 8, 10)
Worldgen:AddOre("luatweaker_test:test_ore", "minecraft:the_nether", 0, 128, 6, 5)

-- Biome-filtered ore
Worldgen:AddOreBiomeFiltered("luatweaker_test:test_ore", "minecraft:overworld",
    -64, 32, 8, 10, { "minecraft:plains", "minecraft:forest" })

-- Vegetation
Worldgen:AddVegetation("minecraft:poppy", 0.1, { "minecraft:plains" })

-- Vegetation patch variant (random_patch shape, not flower)
Worldgen:AddVegetationPatch("minecraft:short_grass", 0.2, { "minecraft:plains" })

-- Summary
local mods = Worldgen:GetModifications()
check("GetModifications returns table", type(mods) == "table")
check("GetModifications counts vegetation", mods["vegetation"] == 2)

-- Remove vanilla ore
local removed = Worldgen:RemoveOre("minecraft:coal_ore", "minecraft:overworld")
check("RemoveOre returns boolean", type(removed) == "boolean")

-- Data-driven registries: custom removal table + custom dimension tag
local regOk = Worldgen:RegisterRemovalFeatures("luatweaker_test:test_ore", { "luatweaker_test:test_ore_upper" })
check("RegisterRemovalFeatures returns true", regOk == true)
local dimOk = Worldgen:RegisterDimensionBiomeTag("luatweaker_test:deep_dark", "#minecraft:is_overworld")
check("RegisterDimensionBiomeTag returns true", dimOk == true)
local dims = Worldgen:GetKnownDimensions()
check("GetKnownDimensions returns table", type(dims) == "table")

-- ModifyBiome: explicit add + remove (emits neoforge biome-modifier JSON)
local beforeModify = Worldgen:GetModifications()
Worldgen:ModifyBiome("minecraft:plains", {
    biomes = { "minecraft:plains", "minecraft:forest" },
    add = { { feature = "luatweaker_test:test_vein", step = "underground_ores" } },
    remove = { "minecraft:ore_dirt" },
})
local afterModify = Worldgen:GetModifications()
check("ModifyBiome increments biomeModifiers", afterModify["biomeModifiers"] == beforeModify["biomeModifiers"] + 1)

-- AddFeature: custom ore-like feature (configured + placed + modifier JSON)
local beforeFeature = Worldgen:GetModifications()
Worldgen:AddFeature("luatweaker_test:test_vein", {
    block = "luatweaker_test:test_ore",
    biomes = { "minecraft:plains" },
    count = 10,
    size = 8,
    minHeight = -64,
    maxHeight = 32,
})
local afterFeature = Worldgen:GetModifications()
check("AddFeature increments customFeatures", afterFeature["customFeatures"] == beforeFeature["customFeatures"] + 1)

-- Error paths must fail loudly (no silent no-ops)
local okBlank = pcall(function()
    Worldgen:ModifyBiome("", { biomes = { "minecraft:plains" }, remove = { "minecraft:ore_dirt" } })
end)
check("ModifyBiome rejects blank biomeId", not okBlank)

local okStep = pcall(function()
    Worldgen:AddFeature("luatweaker_test:bad_vein", {
        block = "luatweaker_test:test_ore",
        biomes = { "minecraft:plains" },
        step = "not_a_step",
    })
end)
check("AddFeature rejects unknown step", not okStep)

local okRange = pcall(function()
    Worldgen:AddFeature("luatweaker_test:bad_vein", {
        block = "luatweaker_test:test_ore",
        biomes = { "minecraft:plains" },
        minHeight = 32,
        maxHeight = -64,
    })
end)
check("AddFeature rejects inverted height range", not okRange)

local okReg = pcall(function()
    Worldgen:RegisterRemovalFeatures("luatweaker_test:test_ore", {})
end)
check("RegisterRemovalFeatures rejects empty features", not okReg)

-- GetBiome reads the live server biome registry (nil at load time: no server yet).
local okBiome, biome = pcall(function()
    return Worldgen:GetBiome("minecraft:plains")
end)
if okBiome and type(biome) == "table" then
    check("GetBiome returns the requested biome", biome.id == "minecraft:plains")
    check("GetBiome exposes temperature", type(biome.temperature) == "number")
    check("GetBiome exposes spawners", type(biome.spawners) == "table")
    check("GetBiome rejects unknown ids", not pcall(function()
        Worldgen:GetBiome("luatweaker_test:definitely_not_a_biome")
    end))
else
    local Events = require("LuaTweaker.Events")
    Events:Listen("ServerStarted", function()
        local okLive, liveBiome = pcall(function() return Worldgen:GetBiome("minecraft:plains") end)
        check("GetBiome returns a table", okLive and type(liveBiome) == "table")
        check("GetBiome exposes temperature", okLive and type(liveBiome.temperature) == "number")
        check("GetBiome exposes spawners", okLive and type(liveBiome.spawners) == "table")
        check("GetBiome rejects unknown ids", not pcall(function()
            Worldgen:GetBiome("luatweaker_test:definitely_not_a_biome")
        end))
    end)
end

-- Clear
Worldgen:ClearAll()
local afterClear = Worldgen:GetModifications()
check("ClearAll returns table", type(afterClear) == "table")

print("[Test][Worldgen] Worldgen service verified")

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

-- Summary
local mods = Worldgen:GetModifications()
check("GetModifications returns table", type(mods) == "table")

-- Remove vanilla ore
local removed = Worldgen:RemoveOre("minecraft:coal_ore", "minecraft:overworld")
check("RemoveOre returns boolean", type(removed) == "boolean")

-- Clear
Worldgen:ClearAll()
local afterClear = Worldgen:GetModifications()
check("ClearAll returns table", type(afterClear) == "table")

print("[Test][Worldgen] Worldgen service verified")

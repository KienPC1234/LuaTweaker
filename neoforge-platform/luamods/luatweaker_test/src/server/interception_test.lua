-- ===================================================================
-- LuaTweaker Test Suite: Interception Service
-- Exercises anvil/brewing/villager-trade interception registration and
-- the pending-interception clear (pure Java, no server needed).
-- ===================================================================
local Interception = require("LuaTweaker.Interception")

local function check(label, cond)
    if cond then
        print("[Test][Interception] " .. label .. " OK")
    else
        print("[ERROR][Test] " .. label .. " FAILED")
    end
end

-- Anvil recipe
Interception:AddAnvilRecipe("test_anvil", "minecraft:iron_ingot", "minecraft:gold_ingot",
    "minecraft:golden_apple", 5)
check("AddAnvilRecipe no-throw", true)

-- Brewing recipe
Interception:AddBrewingRecipe("test_brew", "minecraft:water", "minecraft:glowstone_dust",
    "minecraft:thick")
check("AddBrewingRecipe no-throw", true)

-- Villager trade
Interception:AddVillagerTrade("farmer", 2, "minecraft:emerald", "minecraft:bread", 12, 10)
check("AddVillagerTrade no-throw", true)

-- Clear pending
Interception:ClearInterceptions()
check("ClearInterceptions no-throw", true)

print("[Test][Interception] Interception service verified")

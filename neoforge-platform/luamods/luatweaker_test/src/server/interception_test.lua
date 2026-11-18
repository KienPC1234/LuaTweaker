-- ===================================================================
-- LuaTweaker Test Suite: Interception Service
-- Verifies that each legacy Interception operation is really forwarded
-- to the active recipe registry (observable via the registration count),
-- and that invalid input fails loudly instead of being silently logged.
-- ===================================================================
local Interception = require("LuaTweaker.Interception")

local function check(label, cond)
    if cond then
        print("[Test][Interception] " .. label .. " OK")
    else
        error("[Test][Interception] " .. label .. " FAILED")
    end
end

local initialCount = Interception:GetRegistrationCount()

-- Anvil recipe
Interception:AddAnvilRecipe("test_anvil", "minecraft:iron_ingot", "minecraft:gold_ingot",
    "minecraft:golden_apple", 5)
check("AddAnvilRecipe forwarded to registry",
    Interception:GetRegistrationCount() == initialCount + 1)

-- Brewing recipe
Interception:AddBrewingRecipe("test_brew", "minecraft:water", "minecraft:glowstone_dust",
    "minecraft:thick")
check("AddBrewingRecipe forwarded to registry",
    Interception:GetRegistrationCount() == initialCount + 2)

-- Villager trade
Interception:AddVillagerTrade("farmer", 2, "minecraft:emerald", "minecraft:bread", 12, 10)
check("AddVillagerTrade forwarded to registry",
    Interception:GetRegistrationCount() == initialCount + 3)

-- Error paths: invalid input must throw, not log-and-discard.
local blankAccepted = pcall(function()
    Interception:AddAnvilRecipe("", "minecraft:iron_ingot", "minecraft:gold_ingot",
        "minecraft:golden_apple", 5)
end)
check("blank recipe id rejected", not blankAccepted)

local missingArgsAccepted = pcall(function()
    Interception:AddBrewingRecipe("test_brew_invalid")
end)
check("missing brewing arguments rejected", not missingArgsAccepted)

check("rejected calls leave registry unchanged",
    Interception:GetRegistrationCount() == initialCount + 3)

print("[Test][Interception] Interception service verified: " .. Interception:GetRegistrationCount() .. " registrations")

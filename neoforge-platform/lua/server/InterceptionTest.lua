-- ===================================================================
-- Interception Test Script (Anvil, Brewing, Villager Trade)
-- ===================================================================
local Interception = require("LuaTweaker.Interception")

print("Initializing InterceptionTest.lua...")

-- 1. ANVIL RECIPE INTERCEPTION
Interception:AddAnvilRecipe(
    "ruby_sword_anvil",
    "minecraft:netherite_sword",
    "luatweaker:custom_ruby",
    "luatweaker:ruby_sword",
    5
)

-- 2. BREWING RECIPE INTERCEPTION
Interception:AddBrewingRecipe(
    "ruby_strength_potion",
    "minecraft:water",
    "luatweaker:custom_ruby",
    "minecraft:strength"
)

-- 3. VILLAGER TRADE INTERCEPTION
Interception:AddVillagerTrade(
    "minecraft:armorer",
    1,
    "minecraft:emerald",
    "luatweaker:custom_ruby",
    16,
    5
)

print("InterceptionTest.lua initialized successfully!")

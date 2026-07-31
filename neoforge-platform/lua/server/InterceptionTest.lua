-- ===================================================================
-- Roblox Interception Test Script (Anvil, Brewing, Villager Trade)
-- Intercepts dynamic game mechanics without modifying base game code
-- ===================================================================

print("Initializing InterceptionTest.lua...")

-- SERVICES
local Interception = Mod:GetService("InterceptionService") or Interception

-- 1. ANVIL RECIPE INTERCEPTION
-- Combines Netherite Sword + Diamond to create Custom Ruby Sword with EXP cost 5
Interception:AddAnvilRecipe(
    "ruby_sword_anvil",
    "minecraft:netherite_sword",
    "luatweaker:custom_ruby",
    "luatweaker:ruby_sword",
    5
)
print("[Interception] Registered Anvil combination recipe: netherite_sword + custom_ruby -> ruby_sword")

-- 2. BREWING RECIPE INTERCEPTION
-- Brews Water Bottle + Custom Ruby into Strength Potion
Interception:AddBrewingRecipe(
    "ruby_strength_potion",
    "minecraft:water",
    "luatweaker:custom_ruby",
    "minecraft:strength"
)
print("[Interception] Registered Brewing recipe: water + custom_ruby -> strength potion")

-- 3. VILLAGER TRADE INTERCEPTION
-- Adds custom trade to Armorer villager at level 1 (12 Emeralds -> Custom Ruby)
Interception:AddVillagerTrade(
    "minecraft:armorer",
    1,
    "minecraft:emerald",
    "luatweaker:custom_ruby",
    16,
    5
)
print("[Interception] Registered Villager Trade: Armorer lvl 1 (12 Emeralds -> custom_ruby)")

print("InterceptionTest.lua initialized successfully!")

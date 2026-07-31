-- ===================================================================
-- Server Recipe Tests: Recipe & Content Builders
-- ===================================================================
local Recipe  = require("LuaTweaker.Recipe")
local Content = require("LuaTweaker.Content")

print("Initializing comprehensive server recipe test suite...")

-- 1. Recipe Removals
Recipe.RemoveByOutput("minecraft:diamond_sword")
Recipe.RemoveById("minecraft:cake")

-- 2. Recipe & Content Builder definitions
local CustomSword = Content.Item("minecraft:diamond_sword", 1)
local SuperApple  = Content.Item("minecraft:golden_apple", 2)

-- 3. Shaped & Shapeless Crafting
Recipe.Shaped("luatweaker:custom_iron_sword")
    :Pattern({
        " I ",
        " I ",
        " S "
    })
    :Key("I", Content.Item("minecraft:iron_ingot"))
    :Key("S", Content.Item("minecraft:stick"))
    :Output(Content.Item("minecraft:iron_sword"), 1)
    :Register()

Recipe.Shapeless("luatweaker:super_apple_recipe")
    :Inputs({
        Content.Item("minecraft:apple"),
        Content.Item("minecraft:gold_block")
    })
    :Output(SuperApple)
    :Register()

-- 4. Smelting Workstations
Recipe.Smelting("luatweaker:smelt_iron_raw")
    :Input(Content.Item("minecraft:raw_iron"))
    :Output(Content.Item("minecraft:iron_ingot"), 2)
    :Xp(1.5)
    :CookingTime(200)
    :Register()

-- 5. Global Replacements
Recipe.ReplaceInput("minecraft:coal", "minecraft:charcoal")
Recipe.ReplaceOutput("minecraft:dirt", "minecraft:cobblestone")

print("Comprehensive server recipe test suite executed successfully!")

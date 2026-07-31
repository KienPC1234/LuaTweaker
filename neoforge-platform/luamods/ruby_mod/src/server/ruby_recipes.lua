-- ===================================================================
-- LuaTweaker Server Recipes & DataPack Integration
-- ===================================================================
local Recipe  = require("LuaTweaker.Recipe")
local Content = require("LuaTweaker.Content")

local ruby  = Content.Item("luatweaker:custom_ruby")
local stick = Content.Item("minecraft:stick")

-- 1. Ruby Block Crafting & Unpacking
Recipe.Shaped("ruby_block_craft")
    :Pattern({
        "RRR",
        "RRR",
        "RRR"
    })
    :Key("R", ruby)
    :Output(Content.Item("luatweaker:ruby_block"), 1)
    :Register()

Recipe.Shapeless("ruby_block_unpack")
    :Inputs({
        Content.Item("luatweaker:ruby_block")
    })
    :Output(ruby, 9)
    :Register()

-- 2. Ruby Tools & Weapons Crafting
Recipe.Shaped("ruby_sword_craft")
    :Pattern({
        "R",
        "R",
        "S"
    })
    :Key("R", ruby)
    :Key("S", stick)
    :Output(Content.Item("luatweaker:ruby_sword"), 1)
    :Register()

Recipe.Shaped("ruby_pickaxe_craft")
    :Pattern({
        "RRR",
        " S ",
        " S "
    })
    :Key("R", ruby)
    :Key("S", stick)
    :Output(Content.Item("luatweaker:ruby_pickaxe"), 1)
    :Register()

Recipe.Shaped("ruby_axe_craft")
    :Pattern({
        "RR",
        "RS",
        " S"
    })
    :Key("R", ruby)
    :Key("S", stick)
    :Output(Content.Item("luatweaker:ruby_axe"), 1)
    :Register()

Recipe.Shaped("ruby_shovel_craft")
    :Pattern({
        "R",
        "S",
        "S"
    })
    :Key("R", ruby)
    :Key("S", stick)
    :Output(Content.Item("luatweaker:ruby_shovel"), 1)
    :Register()

Recipe.Shaped("ruby_hoe_craft")
    :Pattern({
        "RR",
        " S",
        " S"
    })
    :Key("R", ruby)
    :Key("S", stick)
    :Output(Content.Item("luatweaker:ruby_hoe"), 1)
    :Register()

-- 3. Ruby Armor Crafting
Recipe.Shaped("ruby_helmet_craft")
    :Pattern({
        "RRR",
        "R R"
    })
    :Key("R", ruby)
    :Output(Content.Item("luatweaker:ruby_helmet"), 1)
    :Register()

Recipe.Shaped("ruby_chestplate_craft")
    :Pattern({
        "R R",
        "RRR",
        "RRR"
    })
    :Key("R", ruby)
    :Output(Content.Item("luatweaker:ruby_chestplate"), 1)
    :Register()

Recipe.Shaped("ruby_leggings_craft")
    :Pattern({
        "RRR",
        "R R",
        "R R"
    })
    :Key("R", ruby)
    :Output(Content.Item("luatweaker:ruby_leggings"), 1)
    :Register()

Recipe.Shaped("ruby_boots_craft")
    :Pattern({
        "R R",
        "R R"
    })
    :Key("R", ruby)
    :Output(Content.Item("luatweaker:ruby_boots"), 1)
    :Register()

-- 4. Ruby Ore Smelting & Blasting
Recipe.Smelting("ruby_ore_smelt")
    :Input(Content.Item("luatweaker:ruby_ore"))
    :Output(ruby, 1)
    :Xp(1.5)
    :CookingTime(200)
    :Register()

Recipe.Blasting("ruby_ore_blast")
    :Input(Content.Item("luatweaker:ruby_ore"))
    :Output(ruby, 1)
    :Xp(2.0)
    :CookingTime(100)
    :Register()

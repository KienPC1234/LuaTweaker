-- ================================================================
-- ARCANE RPG: Recipes
-- Crafting recipes for all Arcane items
-- ================================================================
local Recipe = require("LuaTweaker.Recipe")

-- ==== SHAPED RECIPES ====

Recipe.Shaped("crystal_staff")
    :Pattern({
        "  S",
        " C ",
        "C  "
    })
    :Key("S", "luatweaker:crystal_shard")
    :Key("C", "minecraft:stick")
    :Output("luatweaker:crystal_staff", 1)
    :Register()

Recipe.Shaped("crystal_sword")
    :Pattern({
        " C ",
        " C ",
        " S "
    })
    :Key("C", "luatweaker:crystal_shard")
    :Key("S", "minecraft:stick")
    :Output("luatweaker:crystal_sword", 1)
    :Register()

Recipe.Shaped("crystal_helmet")
    :Pattern({
        "CCC",
        "C C"
    })
    :Key("C", "luatweaker:crystal_shard")
    :Output("luatweaker:crystal_helmet", 1)
    :Register()

Recipe.Shaped("crystal_chestplate")
    :Pattern({
        "C C",
        "CCC",
        "CCC"
    })
    :Key("C", "luatweaker:crystal_shard")
    :Output("luatweaker:crystal_chestplate", 1)
    :Register()

Recipe.Shaped("crystal_leggings")
    :Pattern({
        "CCC",
        "C C",
        "C C"
    })
    :Key("C", "luatweaker:crystal_shard")
    :Output("luatweaker:crystal_leggings", 1)
    :Register()

Recipe.Shaped("crystal_boots")
    :Pattern({
        "C C",
        "C C"
    })
    :Key("C", "luatweaker:crystal_shard")
    :Output("luatweaker:crystal_boots", 1)
    :Register()

Recipe.Shaped("crystal_altar")
    :Pattern({
        "CCC",
        " D ",
        "DDD"
    })
    :Key("C", "luatweaker:crystal_shard")
    :Key("D", "minecraft:diamond")
    :Output("luatweaker:crystal_altar", 1)
    :Register()

Recipe.Shaped("crystal_block")
    :Pattern({
        "CCC",
        "CCC",
        "CCC"
    })
    :Key("C", "luatweaker:crystal_shard")
    :Output("luatweaker:crystal_block", 1)
    :Register()

-- ==== SMELTING ====

Recipe.Smelting("crystal_shard_from_ore")
    :Input("#c:ores/crystal")
    :Output("luatweaker:crystal_shard", 2)
    :Xp(1.5)
    :CookingTime(200)
    :Register()

-- ==== SHAPELESS ====

Recipe.Shapeless("mana_potion")
    :Inputs({
        "minecraft:potion",
        "luatweaker:crystal_shard",
        "minecraft:lapis_lazuli"
    })
    :Output("luatweaker:mana_potion", 1)
    :Register()

print("[ArcaneRPG] Recipes registered: 10 shaped, 1 smelting, 1 shapeless")

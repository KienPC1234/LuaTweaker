local recipes = Mod:GetService("Recipes")

log.info("Initializing comprehensive server recipe test suite...")

-- 1. Recipe Removals
recipes:removeByOutput("minecraft:diamond_sword")
recipes:removeByInput("minecraft:netherite_scrap")
recipes:removeById("minecraft:cake")

-- 2. KubeJS-style Rich Item Definitions
local customSword = item("minecraft:diamond_sword", 1)
    :withName("&6Excalibur")
    :withLore({ "&7Legendary Blade of Flame", "&8Forged with LuaTweaker" })
    :withDamage(10)

local superApple = item({
    id = "minecraft:golden_apple",
    count = 2,
    name = "&dEnchanted Super Apple"
})

-- 3. KubeJS-style Oredict & Tag Ingredient Definitions (tag, oredict, item with #tag)
local woodTag = tag("#minecraft:logs")
local ironOrCopper = ingredient("minecraft:iron_ingot"):orIngredient("minecraft:copper_ingot")

-- 4. Shapeless Crafting with KubeJS-style items & tags
recipes:addShapeless("luatweaker:instant_bread", item("minecraft:bread", 4), {
    ingredient("minecraft:wheat"),
    ingredient("minecraft:sugar")
})

recipes:addShapeless("luatweaker:super_apple_recipe", superApple, {
    ingredient("minecraft:apple"),
    ingredient("minecraft:gold_block")
})

-- 5. Shaped Crafting
recipes:addShaped("luatweaker:custom_iron_sword", item("minecraft:iron_sword", 1), {
    " I ",
    " I ",
    " S "
}, {
    I = ingredient("minecraft:iron_ingot"),
    S = ingredient("minecraft:stick")
})

-- 6. Smelting & Cooking Workstations
recipes:addSmelting("luatweaker:smelt_iron_raw", item("minecraft:iron_ingot", 2), ingredient("minecraft:raw_iron"), 1.5, 200)
recipes:addBlasting("luatweaker:blast_gold_raw", item("minecraft:gold_ingot", 2), ingredient("minecraft:raw_gold"), 2.0, 100)
recipes:addSmoking("luatweaker:quick_beef", item("minecraft:cooked_beef", 1), ingredient("minecraft:beef"), 0.35, 100)
recipes:addCampfire("luatweaker:campfire_fish", item("minecraft:cooked_cod", 1), ingredient("minecraft:cod"), 0.35, 600)

-- 7. Stonecutter Recipe
recipes:addStonecutting("luatweaker:stonecut_copper", item("minecraft:copper_block", 1), ingredient("minecraft:cut_copper"))

-- 8. Smithing Table Upgrade
recipes:addSmithing(
    "luatweaker:custom_netherite_upgrade",
    item("minecraft:netherite_pickaxe", 1),
    ingredient("minecraft:netherite_upgrade_smithing_template"),
    ingredient("minecraft:diamond_pickaxe"),
    ingredient("minecraft:netherite_ingot")
)

-- 9. Anvil Recipe
recipes:addAnvil(
    "luatweaker:empower_diamond_sword",
    item("minecraft:diamond_sword"),
    ingredient("minecraft:diamond_sword"),
    ingredient("minecraft:diamond"),
    5
)

-- 10. Brewing Stand Potion Recipe
recipes:addBrewing(
    "luatweaker:custom_healing_potion",
    "minecraft:strong_healing",
    "minecraft:healing",
    ingredient("minecraft:glistering_melon_slice")
)

-- 11. Villager Trade Recipe
recipes:addTrade("cleric", 3, item("minecraft:emerald", 5), nil, item("minecraft:redstone", 16), 12, 10)

-- 12. Global Replacements
recipes:replaceInput("minecraft:coal", "minecraft:charcoal")
recipes:replaceOutput("minecraft:dirt", "minecraft:cobblestone")

log.info("Comprehensive server recipe test suite executed successfully!")

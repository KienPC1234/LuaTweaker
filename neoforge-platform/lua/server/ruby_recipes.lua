-- ===================================================================
-- 🛠️ LuaTweaker Server Recipes & Multi-Namespace DataPack Integration
-- ===================================================================

local recipes = Mod:GetService("Recipes")

local ruby = "luatweaker:custom_ruby"
local stick = "minecraft:stick"

-- 1. Ruby Block Crafting & Unpacking
recipes:addShaped("ruby_block_craft", item("luatweaker:ruby_block", 1), {
    "RRR",
    "RRR",
    "RRR"
}, {
    R = ingredient(ruby)
})

recipes:addShapeless("ruby_block_unpack", item(ruby, 9), {
    ingredient("luatweaker:ruby_block")
})

-- 2. Ruby Tools & Weapons Crafting
recipes:addShaped("ruby_sword_craft", item("luatweaker:ruby_sword", 1), {
    "R",
    "R",
    "S"
}, {
    R = ingredient(ruby),
    S = ingredient(stick)
})

recipes:addShaped("ruby_pickaxe_craft", item("luatweaker:ruby_pickaxe", 1), {
    "RRR",
    " S ",
    " S "
}, {
    R = ingredient(ruby),
    S = ingredient(stick)
})

recipes:addShaped("ruby_axe_craft", item("luatweaker:ruby_axe", 1), {
    "RR",
    "RS",
    " S"
}, {
    R = ingredient(ruby),
    S = ingredient(stick)
})

recipes:addShaped("ruby_shovel_craft", item("luatweaker:ruby_shovel", 1), {
    "R",
    "S",
    "S"
}, {
    R = ingredient(ruby),
    S = ingredient(stick)
})

recipes:addShaped("ruby_hoe_craft", item("luatweaker:ruby_hoe", 1), {
    "RR",
    " S",
    " S"
}, {
    R = ingredient(ruby),
    S = ingredient(stick)
})

-- 3. Ruby Armor Set Crafting
recipes:addShaped("ruby_helmet_craft", item("luatweaker:ruby_helmet", 1), {
    "RRR",
    "R R"
}, {
    R = ingredient(ruby)
})

recipes:addShaped("ruby_chestplate_craft", item("luatweaker:ruby_chestplate", 1), {
    "R R",
    "RRR",
    "RRR"
}, {
    R = ingredient(ruby)
})

recipes:addShaped("ruby_leggings_craft", item("luatweaker:ruby_leggings", 1), {
    "RRR",
    "R R",
    "R R"
}, {
    R = ingredient(ruby)
})

recipes:addShaped("ruby_boots_craft", item("luatweaker:ruby_boots", 1), {
    "R R",
    "R R"
}, {
    R = ingredient(ruby)
})

-- 4. Magic Staff & Wood Crate Crafting
recipes:addShaped("magic_staff_craft", item("luatweaker:magic_staff", 1), {
    " R ",
    " S ",
    " S "
}, {
    R = ingredient(ruby),
    S = ingredient("minecraft:blaze_rod")
})

recipes:addShaped("wood_crate_craft", item("luatweaker:wood_crate", 1), {
    "PPP",
    "PIP",
    "PPP"
}, {
    P = ingredient("#minecraft:planks"),
    I = ingredient("minecraft:iron_ingot")
})

-- 5. Smelting & Blasting Ruby Ore into Enchanted Ruby Gem (nung hoặc lò cao)
recipes:addSmelting("ruby_ore_smelt", item(ruby, 1), ingredient("luatweaker:ruby_ore"), 1.0, 200)
recipes:addBlasting("ruby_ore_blast", item(ruby, 1), ingredient("luatweaker:ruby_ore"), 1.0, 100)

-- 6. KubeJS-style Virtual DataPack: any namespace, no files written to disk
if datapack then

    -- 6a. 50% Ruby / 50% Redstone drop loot table for luatweaker:ruby_ore
    datapack:addLootTable("luatweaker:blocks/ruby_ore", [[{
      "type": "minecraft:block",
      "pools": [
        {
          "rolls": 1.0,
          "entries": [
            {
              "type": "minecraft:item",
              "name": "luatweaker:custom_ruby",
              "weight": 1
            },
            {
              "type": "minecraft:item",
              "name": "minecraft:redstone",
              "weight": 1
            }
          ]
        }
      ]
    }]])
    print("✅ [Virtual DataPack] Registered 50% Ruby / 50% Redstone loot for luatweaker:ruby_ore")

    -- 6b. Patch vanilla diamond_ore to also drop an extra emerald (multi-namespace test)
    -- Demonstrates KubeJS-style: patch any mod's data without writing a single file to disk
    datapack:addLootTable("minecraft:blocks/diamond_ore", [[{
      "type": "minecraft:block",
      "pools": [
        {
          "rolls": 1.0,
          "entries": [
            {
              "type": "minecraft:item",
              "name": "minecraft:diamond"
            }
          ]
        },
        {
          "rolls": 1.0,
          "entries": [
            {
              "type": "minecraft:item",
              "name": "minecraft:emerald"
            }
          ]
        }
      ]
    }]])
    print("✅ [Virtual DataPack] Patched minecraft:blocks/diamond_ore — now also drops emerald!")

end

print("✅ [Ruby Recipes] All recipes and DataPack rules loaded successfully!")



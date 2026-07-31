# Recipe Management Reference (`LuaTweaker.Recipe`)

LuaTweaker provides a chainable Builder DSL for dynamic recipe management, support for vanilla and modded crafting, smithing, anvil, brewing, smelting, and global replacements.

---

## 1. Module Imports

All static recipe definitions require explicit module imports:

```lua
local Recipe  = require("LuaTweaker.Recipe")
local Content = require("LuaTweaker.Content")
```

---

## 2. Shaped Crafting Grid (`Recipe.Shaped`)

Chainable Builder pattern with visual pattern arrays and input key mappings ending with `:Register()`:

```lua
local Excalibur = Content.Item("luatweaker:excalibur")

Recipe.Shaped("luatweaker:excalibur_craft")
    :Pattern({
        "  D  ",
        "  D  ",
        "  S  "
    })
    :Key("D", Content.Item("minecraft:diamond_block"))
    :Key("S", Content.Item("minecraft:blaze_rod"))
    :Output(Excalibur, 1)
    :Register()
```

---

## 3. Shapeless Crafting (`Recipe.Shapeless`)

```lua
Recipe.Shapeless("luatweaker:instant_bread")
    :Inputs({
        Content.Item("minecraft:wheat"),
        Content.Item("minecraft:sugar")
    })
    :Output(Content.Item("minecraft:bread"), 4)
    :Register()
```

---

## 4. Smelting, Blasting & Cooking (`Recipe.Smelting`)

```lua
-- Smelting (Furnace)
Recipe.Smelting("luatweaker:ruby_smelting")
    :Input(Content.Item("luatweaker:ruby_ore"))
    :Output(Content.Item("luatweaker:custom_ruby"), 1)
    :Xp(1.5)
    :CookingTime(200)
    :Register()

-- Blasting (Blast Furnace)
Recipe.Blasting("luatweaker:ruby_blasting")
    :Input(Content.Item("luatweaker:ruby_ore"))
    :Output(Content.Item("luatweaker:custom_ruby"), 1)
    :Xp(2.0)
    :CookingTime(100)
    :Register()
```

---

## 5. Smithing Table Upgrades (`Recipe.Smithing`)

```lua
Recipe.Smithing("luatweaker:ruby_pickaxe_upgrade")
    :Template(Content.Item("minecraft:netherite_upgrade_smithing_template"))
    :Base(Content.Item("minecraft:diamond_pickaxe"))
    :Addition(Content.Item("luatweaker:custom_ruby"))
    :Output(Content.Item("luatweaker:ruby_pickaxe"), 1)
    :Register()
```

---

## 6. Anvil & Brewing Recipes (`Recipe.Anvil` / `Recipe.Brewing`)

```lua
-- Anvil Recipe
Recipe.Anvil("luatweaker:ruby_enchant")
    :LeftInput(Content.Item("minecraft:diamond_sword"))
    :RightInput(Content.Item("luatweaker:custom_ruby"))
    :Output(Content.Item("minecraft:diamond_sword"))
    :ExpCost(5)
    :Register()

-- Brewing Recipe
Recipe.Brewing("luatweaker:ruby_potion")
    :InputPotion("minecraft:water")
    :Ingredient(Content.Item("luatweaker:custom_ruby"))
    :OutputPotion("minecraft:healing")
    :Register()
```

---

## 7. Global Replacements & Removals

```lua
-- Global Ingredient / Output Replacements
Recipe.ReplaceInput("minecraft:coal", "minecraft:charcoal")
Recipe.ReplaceOutput("minecraft:dirt", "minecraft:cobblestone")

-- Recipe Removals
Recipe.RemoveByOutput("minecraft:diamond_sword")
Recipe.RemoveByInput("minecraft:netherite_scrap")
Recipe.RemoveById("minecraft:cake")
Recipe.RemoveByMod("some_mod_id")
```

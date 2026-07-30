# 🔨 Comprehensive Recipe & Item Management Reference (`recipes`)

> **Stage:** Server Scripts (`lua/server/*.lua`)  
> **Global Service:** `recipes` | `Mod:GetService("Recipes")` | `game:GetService("Recipes")`

LuaTweaker provides a high-performance, dynamic recipe management engine for Minecraft 1.21.1 NeoForge. It supports all vanilla and modded crafting/cooking workstations, anvil combinations, brewing stand recipes, villager trades, and global ingredient replacements.

---

## 🎨 1. KubeJS-Style Rich Item Definitions (`item`)

The `item(...)` function creates item stack definitions with full support for counts, damage, custom names with color formatting, lore lines, enchantments, and custom NBT data.

### Option A: Method Chaining (Fluent Syntax)
```lua
local excalibur = item("minecraft:diamond_sword", 1)
    :withName("&6Excalibur")                                    -- Gold custom name (& or § codes supported)
    :withLore({ "&7Legendary Blade of Flame", "&8LuaTweaker" }) -- Colored lore lines
    :withDamage(10)                                              -- Item damage / durability lost
    :withEnchantment("minecraft:sharpness", 5)                  -- Sharpness V
    :withEnchantment("minecraft:fire_aspect", 2)                 -- Fire Aspect II
    :withNbt("{CustomModelData:100, Unbreakable:1b}")           -- NBT JSON string
```

### Option B: Table Syntax
```lua
local superApple = item({
    id = "minecraft:golden_apple",
    count = 2,
    name = "&dEnchanted Super Apple",
    lore = { "&7Restores full health" },
    nbt = "{CustomModelData:50}"
})
```

### Option C: Short Item & Tag Syntax
```lua
local wheatItem = item("minecraft:wheat")        -- 1x wheat
local countItem = item("minecraft:bread", 4)      -- 4x bread
local tagAsItem = item("#minecraft:logs")         -- Tag accepted directly as item/ingredient
```

---

## 🏷️ 2. Ingredient Definitions (`ingredient`, `tag`, `oredict`)

Input slots expect an ingredient, which can be an item ID, a tag `#namespace:tag`, an oredict tag, or alternative ingredients.

```lua
-- Single Item Ingredient
local iron = ingredient("minecraft:iron_ingot")

-- Tag & OreDict Ingredients (all equivalent)
local wood1 = tag("#minecraft:logs")
local wood2 = oredict("c:ingots/iron")           -- Auto-prefixed as #c:ingots/iron
local wood3 = ingredient("#minecraft:logs")

-- Composite / Alternative Ingredients (:orIngredient, :alt, :otherwise)
-- Note: Use :orIngredient() or :alt() to avoid collision with Lua's reserved 'or' keyword
local ironOrCopper = ingredient("minecraft:iron_ingot"):orIngredient("minecraft:copper_ingot")
local anyPlank = tag("#minecraft:planks"):alt("minecraft:bamboo_planks")
```

---

## 🎨 3. Color Codes Reference

You can use standard Minecraft color codes with either `&` or `§`:

| Code | Color | Code | Color |
|---|---|---|---|
| `&0` | Black | `&8` | Dark Gray |
| `&1` | Dark Blue | `&9` | Blue |
| `&2` | Dark Green | `&a` | Green |
| `&3` | Dark Aqua | `&b` | Aqua |
| `&4` | Dark Red | `&c` | Red |
| `&5` | Dark Purple | `&d` | Light Purple |
| `&6` | Gold | `&e` | Yellow |
| `&7` | Gray | `&f` | White |
| `&l` | **Bold** | `&o` | *Italic* |

---

## 📐 4. Shaped Crafting Grid (`recipes:addShaped`)

### Signature
```lua
recipes:addShaped(recipeId: string, output: ItemCount, pattern: table<integer, string>, keys: table<string, IngredientWrapper>)
```

### Example
```lua
recipes:addShaped("luatweaker:custom_iron_sword", item("minecraft:iron_sword", 1), {
    " I ",
    " I ",
    " S "
}, {
    I = ingredient("minecraft:iron_ingot"),
    S = ingredient("minecraft:stick")
})
```

---

## 🌀 5. Shapeless Crafting (`recipes:addShapeless`)

### Signature
```lua
recipes:addShapeless(recipeId: string, output: ItemCount, ingredients: table<integer, IngredientWrapper>)
```

### Example
```lua
recipes:addShapeless("luatweaker:instant_bread", item("minecraft:bread", 4), {
    ingredient("minecraft:wheat"),
    ingredient("minecraft:sugar")
})
```

---

## 🔥 6. Cooking & Workstation Recipes

```lua
-- Smelting (Furnace): addSmelting(id, output, input, xp: number, cookTimeTicks: integer)
recipes:addSmelting("luatweaker:smelt_iron", item("minecraft:iron_ingot", 2), ingredient("minecraft:raw_iron"), 1.5, 200)

-- Blasting (Blast Furnace): addBlasting(id, output, input, xp: number, cookTimeTicks: integer)
recipes:addBlasting("luatweaker:blast_gold", item("minecraft:gold_ingot", 2), ingredient("minecraft:raw_gold"), 2.0, 100)

-- Smoking (Smoker): addSmoking(id, output, input, xp: number, cookTimeTicks: integer)
recipes:addSmoking("luatweaker:quick_beef", item("minecraft:cooked_beef", 1), ingredient("minecraft:beef"), 0.35, 100)

-- Campfire Cooking: addCampfire(id, output, input, xp: number, cookTimeTicks: integer)
recipes:addCampfire("luatweaker:campfire_cod", item("minecraft:cooked_cod", 1), ingredient("minecraft:cod"), 0.35, 600)
```

---

## 🪨 7. Stonecutter (`recipes:addStonecutting`)

```lua
recipes:addStonecutting("luatweaker:stonecut_copper", item("minecraft:copper_block", 1), ingredient("minecraft:cut_copper"))
```

---

## 🔨 8. Smithing Table Upgrades (`recipes:addSmithing`)

```lua
recipes:addSmithing(
    "luatweaker:custom_netherite_upgrade",
    item("minecraft:netherite_pickaxe", 1),
    ingredient("minecraft:netherite_upgrade_smithing_template"), -- Template
    ingredient("minecraft:diamond_pickaxe"),                      -- Base
    ingredient("minecraft:netherite_ingot")                       -- Addition
)
```

---

## ⚒️ 9. Anvil Combinations (`recipes:addAnvil`)

Anvil recipes are evaluated dynamically via `AnvilUpdateEvent` and registered in JEI under the **Anvil** category.

### Signature
```lua
recipes:addAnvil(recipeId: string, output: ItemCount, leftInput: IngredientWrapper, rightInput: IngredientWrapper, expCost: integer)
```

### Example
```lua
local excalibur = item("minecraft:diamond_sword", 1)
    :withName("&6Excalibur")
    :withLore({ "&7Legendary Blade of Flame" })
    :withEnchantment("minecraft:sharpness", 5)

-- Wooden Sword + Emerald -> Excalibur Diamond Sword (1 XP Level)
recipes:addAnvil(
    "luatweaker:empower_sword_anvil",
    excalibur,
    ingredient("minecraft:wooden_sword"),
    ingredient("minecraft:emerald"),
    1
)
```

---

## 🧪 10. Brewing Stand Potions (`recipes:addBrewing`)

Brewing recipes are registered into NeoForge's `PotionBrewing.Builder` during startup and displayed in JEI's **Brewing** category.

### Signature
```lua
recipes:addBrewing(recipeId: string, outputPotionId: string, inputPotionId: string, ingredient: IngredientWrapper)
```

### Example
```lua
-- Water Bottle + Diamond -> Potion of Night Vision
recipes:addBrewing(
    "luatweaker:diamond_night_vision_brew",
    "minecraft:night_vision",
    "minecraft:water",
    ingredient("minecraft:diamond")
)
```

---

## 🧑‍🌾 11. Villager Trades (`recipes:addTrade`)

### Signature
```lua
recipes:addTrade(profession: string, level: integer, buy1: ItemCount, buy2: ItemCount?, sell: ItemCount, maxUses: integer, xp: integer)
```

### Example
```lua
-- Cleric (level 3) sells 16 redstone for 5 emeralds
recipes:addTrade("cleric", 3, item("minecraft:emerald", 5), nil, item("minecraft:redstone", 16), 12, 10)
```

---

## 🔄 12. Global Ingredient & Output Replacements

```lua
-- Replace coal with charcoal across all loaded recipes
recipes:replaceInput("minecraft:coal", "minecraft:charcoal")

-- Replace dirt output with cobblestone across all loaded recipes
recipes:replaceOutput("minecraft:dirt", "minecraft:cobblestone")
```

---

## ❌ 13. Recipe Removals

```lua
recipes:removeByOutput("minecraft:diamond_sword")  -- Remove all recipes outputting a diamond sword
recipes:removeByInput("minecraft:netherite_scrap") -- Remove all recipes using netherite scrap
recipes:removeById("minecraft:cake")               -- Remove recipe by full ID
recipes:removeByMod("some_mod_id")                -- Remove all recipes from a mod
recipes:removeByTag("#minecraft:logs")             -- Remove recipes using items in tag
recipes:removeAll()                                -- Clear all recipes
```

---

## 🛠️ 14. Useful In-Game Commands

| Command | Description |
|---|---|
| `/lt hand` | Inspect held item or targeted block. Click any line in chat to copy Lua `item(...)`, `ingredient(...)`, `tag(...)`, or `:withNbt(...)` code! |
| `/lt reload` | Hot-reload all Lua scripts, regenerate LSP autocomplete stubs, and re-apply recipes instantly without restarting Minecraft! |
| `/lt doctor` | Run health diagnostics on loaded Lua scripts. |
| `/lt dump` | Dump item, block, and potion registries to logs. |

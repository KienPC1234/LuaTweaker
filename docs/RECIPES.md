# 🔨 Exhaustive Recipe Management Reference (`recipes`)

> **Stage:** `server_scripts/` (`lua/server_scripts/*.lua`)  
> **Global Variable:** `recipes` | **Service Lookup:** `local recipes = game:GetService("Recipes")`

LuaTweaker provides a high-performance, dynamic recipe management engine supporting all vanilla and modded crafting/cooking workstations, villager trades, and global ingredient replacements.

> 💡 **Service Registry Paradigm**: You can access this service via the global variable `recipes` or via `game:GetService("Recipes")` / `Mod:GetService("Recipes")`.

---

## 🛑 Common Error Pitfalls & Rules

> [!WARNING]
> 1. **Resource Location Namespaces**: Always use valid `modid:item_name` identifiers (e.g. `minecraft:iron_ingot`, `luatweaker:custom_ruby`).
> 2. **Item vs Ingredient Wrappers**: Output slots expect `item("id", count)`. Input slots expect `ingredient("id")` or `ingredient("#c:tag_name")`.
> 3. **Shaped Pattern Keys**: Pattern rows must be strings of equal character length. Keys must match the characters used in pattern rows.

---

## 📐 1. Shaped 3x3 Crafting Grid Recipes (`recipes:addShaped`)

### Signature:
```lua
recipes:addShaped(recipeId: string, output: ItemStack, pattern: table<integer, string>, keys: table<string, Ingredient>)
```

### ✅ Correct Usage:
```lua
recipes:addShaped("ruby_sword", item("luatweaker:custom_ruby_sword", 1), {
    " R ",
    " R ",
    " S "
}, {
    R = ingredient("luatweaker:custom_ruby"),
    S = ingredient("minecraft:stick")
})
```

### ❌ Incorrect Usage:
```lua
-- WRONG: Pattern rows have uneven lengths or missing key definitions
recipes:addShaped("broken_sword", item("minecraft:iron_sword"), {
    "RR", -- ERROR: 2 chars instead of 3
    " R ",
    " S "
}, {
    R = "luatweaker:custom_ruby" -- ERROR: String instead of ingredient()
})
```

---

## 🌀 2. Shapeless Crafting Recipes (`recipes:addShapeless`)

### Signature:
```lua
recipes:addShapeless(recipeId: string, output: ItemStack, ingredients: table<integer, Ingredient>)
```

### ✅ Correct Usage:
```lua
recipes:addShapeless("ruby_ingot_from_nuggets", item("luatweaker:custom_ruby", 2), {
    ingredient("luatweaker:custom_ruby_nugget"),
    ingredient("#c:ores")
})
```

---

## 🔨 3. Smithing Table Upgrades (`recipes:addSmithing`)

### Signature:
```lua
recipes:addSmithing(recipeId: string, result: ItemStack, template: Ingredient, base: Ingredient, addition: Ingredient)
```

### ✅ Correct Usage:
```lua
recipes:addSmithing("ruby_pickaxe_upgrade", 
    item("luatweaker:ruby_pickaxe", 1),
    ingredient("minecraft:netherite_upgrade_smithing_template"),
    ingredient("minecraft:diamond_pickaxe"),
    ingredient("luatweaker:custom_ruby")
)
```

---

## 🪨 4. Stonecutter Recipes (`recipes:addStonecutting`)

### Signature:
```lua
recipes:addStonecutting(recipeId: string, output: ItemStack, input: Ingredient)
```

### ✅ Correct Usage:
```lua
recipes:addStonecutting("ruby_block_unpack", item("luatweaker:custom_ruby", 9), ingredient("luatweaker:custom_ruby_block"))
```

---

## ⚒️ 5. Anvil Combination Recipes (`recipes:addAnvil`)

### Signature:
```lua
recipes:addAnvil(recipeId: string, output: ItemStack, leftInput: Ingredient, rightInput: Ingredient, expCost: integer)
```

### ✅ Correct Usage:
```lua
recipes:addAnvil("ruby_empower_sword", item("minecraft:diamond_sword"), ingredient("minecraft:diamond_sword"), ingredient("luatweaker:custom_ruby"), 5)
```

---

## 🧪 6. Brewing Stand Potion Recipes (`recipes:addBrewing`)

### Signature:
```lua
recipes:addBrewing(recipeId: string, outputPotion: string, inputPotion: string, ingredient: Ingredient)
```

### ✅ Correct Usage:
```lua
recipes:addBrewing("ruby_health_potion", "minecraft:strong_healing", "minecraft:healing", ingredient("luatweaker:custom_ruby"))
```

---

## 🧑‍🌾 7. Villager Trading Recipes (`recipes:addTrade`)

### Signature:
```lua
recipes:addTrade(profession: string, level: integer, buy1: ItemStack, buy2: ItemStack?, sell: ItemStack, maxUses: integer, xp: integer)
```

### ✅ Correct Usage:
```lua
recipes:addTrade("cleric", 3, item("minecraft:emerald", 5), nil, item("luatweaker:custom_ruby", 1), 12, 10)
```

---

## 🔥 8. Smelting & Cooking Workstations

```lua
-- Smelting (Furnace): addSmelting(id, output, input, xp: number, cookTimeInTicks: integer)
recipes:addSmelting("ruby_smelt", item("luatweaker:custom_ruby"), ingredient("luatweaker:ruby_ore"), 1.5, 200)

-- Blasting (Blast Furnace)
recipes:addBlasting("ruby_blast", item("luatweaker:custom_ruby"), ingredient("luatweaker:ruby_ore"), 2.0, 100)

-- Smoking (Smoker)
recipes:addSmoking("cooked_meat", item("minecraft:cooked_beef"), ingredient("minecraft:beef"), 0.35, 100)

-- Campfire Cooking
recipes:addCampfire("campfire_meat", item("minecraft:cooked_beef"), ingredient("minecraft:beef"), 0.35, 600)
```

---

## 🔄 9. Global Recipe Input & Output Replacements

Replace ingredients across all loaded recipes in the game dynamically:

```lua
-- Replace all recipe inputs of oak planks with custom ruby
recipes:replaceInput("minecraft:oak_planks", "luatweaker:custom_ruby")

-- Replace all recipe outputs of dirt with diamond
recipes:replaceOutput("minecraft:dirt", "minecraft:diamond")
```

---

## ❌ 10. Recipe Removals

```lua
-- Remove all recipes producing a diamond sword
recipes:removeByOutput("minecraft:diamond_sword")

-- Remove recipe by specific ID
recipes:removeById("minecraft:golden_apple")
```

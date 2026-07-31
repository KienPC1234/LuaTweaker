# 📜 Exhaustive LuaTweaker Scripting Guide

> **Target Version:** Minecraft 1.21.6 (NeoForge) | **Java Version:** 21 | **Lua Version:** 5.1 / 5.2 (LuaJ 3.0.1)

This reference manual documents all global functions, data wrappers, recipe types, WorldGen capabilities, and event hooks provided by LuaTweaker.

---

## 🎨 Custom Items, Blocks, Textures & Models

### Auto-Generate Texture via Multiply Blending
Call `item:color()` to auto-generate a colored texture from a grayscale template.

```lua
startup:createItem("ruby_sword", function(item)
    item:displayName("Ruby Sword")
    item:color(0xFF1E3C)                -- Ruby red (RGB hex)
    item:textureTemplate("iron_sword")   -- Optional: grayscale template (auto-detect if omitted)
end)
```

### Provide Your Own Texture & Model (Assets Folder)
Place PNG textures and JSON models in the mod's `assets/` folder. They will be loaded automatically from RAM (no disk writes by the mod).

```
mod_example/
├── assets/
│   ├── textures/item/ruby_gem.png       ← Your custom texture
│   ├── textures/block/ruby_block.png
│   ├── models/item/ruby_sword.json     ← Your custom model (auto-loaded)
│   └── blockstates/ruby_block.json     ← Your custom blockstate (auto-loaded)
├── startup/
│   └── ruby_registry.lua
└── manifest.json
```

```lua
-- No color() call → uses your custom texture from assets/
startup:createItem("ruby_gem", function(item)
    item:displayName("Ruby Gem")
    item:maxStackSize(64)
end)
```

### Custom Model JSON (Lua inline)
```lua
startup:createItem("custom_item", function(item)
    item:displayName("Custom Item")
    item:modelParent("minecraft:item/handheld")  -- Quick parent override
    -- OR full custom JSON:
    -- item:modelJson('{"parent":"minecraft:item/generated","textures":{"layer0":"mod_example:item/my_tex"}}')
end)

startup:createBlock("custom_block", function(block)
    block:displayName("Custom Block")
    block:blockstateJson('{"variants":{"":{"model":"mod_example:block/custom"}}}')
    block:blockModelJson('{"parent":"minecraft:block/cube_all","textures":{"all":"mod_example:block/custom"}}')
end)
```

### API Reference
| Method | Description |
|--------|-------------|
| `item:color(hex)` | Enable auto-gen texture with Multiply blending (grayscale template × color) |
| `item:textureTemplate("iron_sword")` | Explicit grayscale template (auto-detected if omitted) |
| `item:modelParent("minecraft:item/handheld")` | Override model parent (default: generated/handheld) |
| `item:modelJson('{...}')` | Full custom model JSON |
| `block:blockstateJson('{...}')` | Custom blockstate JSON |
| `block:blockModelJson('{...}')` | Custom block model JSON |
| `block:itemModelJson('{...}')` | Custom block item model JSON |

### Grayscale Template Priority (item)
`iron_<type>` → `diamond_<type>` → `stone_<type>` → `<type>` → `emerald` → `diamond` → `amethyst_shard`

### Grayscale Template Priority (block)
`<name>` → `diamond_block` → `iron_block` → `stone`

### Grayscale Template Priority (ore)
`emerald_ore` → `iron_ore` → `gold_ore` → `diamond_ore` → `copper_ore` → procedural spots

---

## 🛠️ Global Helpers & Data Constructors

| Function | Signature | Description |
| :--- | :--- | :--- |
| `item(id, count)` | `item(string, integer?): ItemStack` | Creates an item stack wrapper with optional count (default: 1). |
| `ingredient(id)` | `ingredient(string): Ingredient` | Creates an item/tag ingredient wrapper (e.g. `ingredient("#c:ores")`). |
| `fluid(id, amount)` | `fluid(string, integer?): FluidStack` | Creates a fluid stack wrapper (default: 1000 mB). |
| `block(id)` | `block(string): BlockState` | Creates a block state wrapper. |

---

## 🌍 WorldGen & Structure Generation API (`worldgen`)

```lua
-- 1. Dynamic Ore Generation (id, blockToSpawn, targetBlock, veinSize, countPerChunk, minHeight, maxHeight)
worldgen:addOre("ruby_ore_gen", "luatweaker:custom_ruby_ore", "minecraft:stone", 8, 12, -64, 32)

-- 2. Advanced Structure Placement (id, structureNbtPath, biomeFilter)
worldgen:addStructure("ruby_temple", "luatweaker:structures/ruby_temple.nbt", "#minecraft:is_overworld")

-- 3. Biome Feature Removal (biomeId, featureId)
worldgen:removeFeature("minecraft:plains", "minecraft:patch_sugar_cane")
```

---

## ⚔️ Recipe Manager API (`recipes`)

### 1. Shaped Crafting Grid Recipe (`recipes:addShaped`)
```lua
recipes:addShaped("ruby_sword", item("luatweaker:custom_ruby_sword"), {
    " R ",
    " R ",
    " S "
}, {
    R = ingredient("luatweaker:custom_ruby"),
    S = ingredient("minecraft:stick")
})
```

### 2. Smithing Table Recipe (`recipes:addSmithing`)
```lua
recipes:addSmithing("ruby_pickaxe_upgrade", item("luatweaker:ruby_pickaxe"),
    ingredient("minecraft:netherite_upgrade_smithing_template"),
    ingredient("minecraft:diamond_pickaxe"),
    ingredient("luatweaker:custom_ruby")
)
```

### 3. Stonecutter Recipe (`recipes:addStonecutting`)
```lua
recipes:addStonecutting("ruby_block_to_gems", item("luatweaker:custom_ruby", 9), ingredient("luatweaker:custom_ruby_block"))
```

### 4. Anvil Recipe (`recipes:addAnvil`)
```lua
recipes:addAnvil("ruby_enchant", item("minecraft:diamond_sword"), ingredient("minecraft:diamond_sword"), ingredient("luatweaker:custom_ruby"), 5)
```

### 5. Brewing Stand Potion Recipe (`recipes:addBrewing`)
```lua
recipes:addBrewing("ruby_potion", "minecraft:healing", "minecraft:water", ingredient("luatweaker:custom_ruby"))
```

### 6. Villager Trade (`recipes:addTrade`)
```lua
-- profession, level, buy1, buy2, sell, maxUses, xp
recipes:addTrade("cleric", 3, item("minecraft:emerald", 5), nil, item("luatweaker:custom_ruby", 1), 12, 10)
```

### 7. Smelting & Cooking Recipes (`recipes:addSmelting`)
```lua
recipes:addSmelting("ruby_smelting", item("luatweaker:custom_ruby"), ingredient("luatweaker:ruby_ore"), 1.5, 200)
recipes:addBlasting("ruby_blasting", item("luatweaker:custom_ruby"), ingredient("luatweaker:ruby_ore"), 2.0, 100)
```

### 8. Global Recipe Replacements
```lua
recipes:replaceInput("minecraft:oak_planks", "luatweaker:custom_ruby")
recipes:replaceOutput("minecraft:dirt", "minecraft:diamond")
```

---

## ⚡ Signal-Based Event System (`Signal`, `EntityService`)

```lua
-- Reactive signal-style event (Roblox `Signal:Connect`)
local EntityService = Mod:GetService("EntityService")
EntityService.EntitySpawned:Connect(function(entity)
    if entity.Type == "minecraft:player" then
        entity:SendMessage("§aWelcome, " .. entity.Name .. "!")
    end
end)

-- Custom signal
local onCustom = Signal.new()
onCustom:Connect(function(author, score)
    print("Custom event from " .. author .. " (score " .. score .. ")")
end)
onCustom:Fire("Kien", 100)
```

---

## 👁️ JEI / REI / EMI Mod Integration API (`jei`)

```lua
jei:hide("minecraft:dirt")
jei:hideMod("secretmod")
jei:addDescription("luatweaker:custom_ruby", "Found deep underground in ruby ore veins.")
```

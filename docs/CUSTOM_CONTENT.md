# 🎨 Exhaustive Custom Content & Resource Management Guide (`startup`, `assets`, `data`, `datapack`, `storage`)

> **Stage:** `startup_scripts/` (`lua/startup_scripts/*.lua`)  
> **Global Variables:** `startup`, `datapack`, `storage` | **Service Lookup:** `local startup = game:GetService("Startup")`, `local storage = game:GetService("Storage")`

LuaTweaker allows registering brand-new **Custom Items**, **Custom Blocks**, **Custom Fluids**, **Material Tier Sets**, auto-mounting resourcepacks/datapacks, saving persistent modpack variables, and attaching Lua right-click action handlers.

> 💡 **Service Registry Paradigm**: Access startup registry via `startup` or `game:GetService("Startup")` / `Mod:GetService("Startup")`, and persistent storage via `storage` or `game:GetService("Storage")` / `game:GetService("DataStoreService")`.

---

## ⚡ 1. Custom Item Builder (`startup:createItem`) with Action Handlers

```lua
startup:createItem("custom_ruby", function(item)
    item:maxStackSize(16)
        :rarity("EPIC")
        :burnTime(400)
        :displayName("Enchanted Ruby Gem")
        :onRightClick(function(player, itemStack)
            player:sendMessage("✨ Enchanted Ruby Gem activated!")
        end)
end)
```

---

## 🧱 2. Custom Block Builder (`startup:createBlock`) with Action Handlers

```lua
startup:createBlock("custom_ruby_block", function(block)
    block:hardness(3.0)
         :resistance(12.0)
         :lightLevel(10)
         :soundType("STONE")
         :onRightClick(function(player, blockState)
             player:sendMessage("🔮 Custom Ruby Altar interacted!")
         end)
end)
```

---

## 💧 3. Custom Fluid Builder (`startup:createFluid`)

```lua
startup:createFluid("liquid_ruby", function(fluid)
    fluid:color(0xFF0033)
         :stillTexture("luatweaker:block/liquid_ruby_still")
         :flowingTexture("luatweaker:block/liquid_ruby_flow")
         :temperature(1200)
         :viscosity(2000)
end)
```

---

## 💾 4. Persistent Modpack Storage API (`storage`)

Save quest states, player data, and world variables to disk (`lua/storage.json`) across server restarts:

```lua
-- Save boolean, string, number, or table values
storage:set("first_join_reward_given", true)
storage:set("player_level_multiplier", 1.5)

-- Retrieve saved data with default fallbacks
local rewardGiven = storage:get("first_join_reward_given", false)
if not rewardGiven then
    print("Granting first join reward!")
end
```

---

## 📦 5. In-Memory Virtual Datapack Helpers (`datapack`)

Inject dynamic JSON recipes, tags, loot tables, advancements, and mcfunctions into memory without physical files:

```lua
datapack:addJsonRecipe("custom_crafting", '{"type":"minecraft:crafting_shapeless","result":{"id":"minecraft:diamond"}}')
datapack:addLootTable("blocks/custom_ruby_ore", '{"type":"minecraft:block","pools":[]}')
datapack:addAdvancement("story/ruby_master", '{"display":{"title":"Ruby Master"}}')
datapack:addFunction("utility/heal_all", "effect give @a minecraft:regeneration 10 2")
```

---

## 🖼️ 6. Physical ResourcePack Mounting (`lua/assets/`)

Files placed inside `lua/assets/` are **automatically mounted into Minecraft's client Resource Pack repository**:

- **Item Texture:** `lua/assets/luatweaker/textures/item/custom_ruby.png`
- **Block Texture:** `lua/assets/luatweaker/textures/block/custom_ruby_block.png`
- **Language File:** `lua/assets/luatweaker/lang/en_us.json`

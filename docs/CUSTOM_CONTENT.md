# Custom Content & Resource Management

LuaTweaker provides a chainable Builder DSL for static content registration (items, blocks, fluids), virtual datapack injection, and persistent storage.

---

## 1. Content Registration (`Content`)

All custom items, blocks, and fluids are registered using explicit `require("LuaTweaker.Content")` imports and chainable Builder patterns ending with `:Register()`.

```lua
local Content = require("LuaTweaker.Content")

-- Custom Item Builder
local RubyGem = Content.NewItem("luatweaker:custom_ruby")
    :DisplayName("§6Enchanted Ruby Gem")
    :MaxStackSize(16)
    :Rarity("EPIC")
    :BurnTime(400)
    :OnUse(function(player, itemStack)
        player:SendMessage("✨ Enchanted Ruby Gem activated!")
    end)
    :Register()

-- Custom Block Builder
local RubyBlock = Content.NewBlock("luatweaker:custom_ruby_block")
    :DisplayName("§cRuby Block")
    :Hardness(3.0)
    :Resistance(12.0)
    :LightLevel(10)
    :SoundType("STONE")
    :OnInteract(function(player, blockState)
        player:SendMessage("🔮 Custom Ruby Altar interacted!")
    end)
    :Register()

-- Custom Fluid Builder
local LiquidRuby = Content.NewFluid("luatweaker:liquid_ruby")
    :Color(0xFF0033)
    :StillTexture("luatweaker:block/liquid_ruby_still")
    :FlowingTexture("luatweaker:block/liquid_ruby_flow")
    :Temperature(1200)
    :Viscosity(2000)
    :Register()
```

---

## 2. Persistent Storage API (`Storage`)

Save quest states, player data, and world variables across server restarts:

```lua
local Storage = require("LuaTweaker.Storage")

-- Set persistent data
Storage.Set("first_join_reward_given", true)
Storage.Set("player_level_multiplier", 1.5)

-- Retrieve saved data with fallback
local rewardGiven = Storage.Get("first_join_reward_given", false)
if not rewardGiven then
    print("Granting first join reward!")
end
```

---

## 3. In-Memory Virtual Datapack Engine (`Datapack`)

Inject dynamic JSON recipes, tags, loot tables, advancements, and mcfunctions into memory without creating physical files on disk:

```lua
local Datapack = require("LuaTweaker.Datapack")

Datapack.AddJsonRecipe("custom_crafting", '{"type":"minecraft:crafting_shapeless","result":{"id":"minecraft:diamond"}}')
Datapack.AddLootTable("blocks/custom_ruby_ore", '{"type":"minecraft:block","pools":[]}')
Datapack.AddAdvancement("story/ruby_master", '{"display":{"title":"Ruby Master"}}')
Datapack.AddFunction("utility/heal_all", "effect give @a minecraft:regeneration 10 2")
```

---

## 4. Physical ResourcePack Mounting (`lua/assets/`)

Files placed inside `lua/assets/` are automatically mounted into Minecraft's client Resource Pack repository:

- **Item Texture:** `lua/assets/luatweaker/textures/item/custom_ruby.png`
- **Block Texture:** `lua/assets/luatweaker/textures/block/custom_ruby_block.png`
- **Language File:** `lua/assets/luatweaker/lang/en_us.json`

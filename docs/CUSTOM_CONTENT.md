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

---

## 5. Custom biomes (`Biomes:Define`)

Vanilla-shape biome JSONs for custom dimensions (verified against
`plains.json` in the 1.21.1 client jar: 11 per-step feature slots, effects,
spawners, spawn_costs, carvers). `AddSpawn`/`RemoveSpawn` only merge into an
existing virtual file — unknown ids fail fast instead of shadowing:

```lua
local Biomes = require("LuaTweaker.Biomes")

Biomes:Define("mymod:crystal_meadow", {
    temperature = 0.7,
    effects = { sky_color = 0x78A7FF, grass_color_modifier = "swamp" },
    features = { vegetal_decoration = { "luatweaker:mymod_crystal_patch" } },
})
Biomes:AddSpawn("mymod:crystal_meadow", "monster", "mymod:wisp", 10, 1, 2)
```

## 6. Structures (`Structures`)

Single-template NBT structures: register once, generate naturally via spacing/separation, or place at runtime.

```lua
local Structures = require("LuaTweaker.Structures")

Structures:Register("mymod:ancient_temple", {
    template = "mymod:structures/temple", -- data/mymod/structure/structures/temple.nbt
    spawnBiomes = { "minecraft:desert", "minecraft:badlands" },
    spacing = 32, separation = 8, salt = 12345, -- rarity IS spacing/separation/salt
})

-- Floating island temple (jigsaw placement control, all optional):
Structures:Register("mymod:sky_temple", {
    template = "mymod:structures/temple",
    spawnBiomes = { "minecraft:plains" },
    startHeight = 100,                -- ground offset added to the heightmap projection
    size = 6, maxDistance = 128,      -- jigsaw depth / piece spread (village uses 6/80)
    useExpansionHack = false,
    terrainAdaptation = "bury",       -- none | beard_thin | beard_box | bury | encapsulate
    heightmap = "WORLD_SURFACE_WG",   -- projection the offset applies to
    step = "surface_structures",      -- validated generation step
})

-- Runtime placement (returns false when the template or level is missing):
local placed = Structures:PlaceAt("mymod:ancient_temple", "minecraft:overworld",
    100, 64, 200, "clockwise_90", "none")
```

`spawnChance`/`lootTables` are validated but reserved (loot comes from the NBT itself).
Rarity has no separate chance field in vanilla `structure_set` JSON — it IS
`spacing`/`separation`/`salt`: widen spacing for rarer structures, override `salt`
to decorrelate overlapping sets. After each load `StructuresApplied` fires with
`{ applied, skipped }`.

## 7. Dimensions (`Dimensions`)

```lua
local Dimensions = require("LuaTweaker.Dimensions")

local ids = Dimensions:GetDimensionIds() -- sorted { "arcane:crystal_realm", ... }
local dim = Dimensions:GetDimension("arcane:crystal_realm") -- info table or nil
local current = Dimensions:GetEntityDimension(player) -- e.g. "minecraft:overworld" or nil

Dimensions:TeleportTo(player, "arcane:crystal_realm")
```

After each load `DimensionsMaterialized` fires with `{ dimensions = { ... } }`,
and every portal use fires `PortalUsed` with `{ blockId, targetDimension, playerName }`.

---

## 8. Advanced Items, Tools, and Animations

Custom tools, weapons, and charged items support animations, dynamic mining tiers, and lifecycle callbacks:

### 8.1 Tool Tiers and Combat Attributes

| Method | Parameters | Description |
|---|---|---|
| `:MiningLevel(level)` | `integer` (0=wood, 1=stone, 2=iron, 3=diamond, 4=netherite) | Sets harvest level |
| `:MiningSpeed(speed)` | `number` | Sets mining speed multiplier |
| `:AttackDamage(damage)`| `number` | Sets base attack damage |
| `:AttackSpeed(speed)` | `number` | Sets attack speed (e.g. 1.6 for sword, 0.8 for hammer) |
| `:Durability(uses)` | `integer` | Sets item durability |
| `:Enchantability(value)`| `integer` | Sets enchantment factor |

### 8.2 Use Animations and Lifecycle Hooks

| Method | Parameters | Description |
|---|---|---|
| `:UseAnimation(anim)` | `"bow"`, `"crossbow"`, `"eat"`, `"drink"`, `"block"`, `"spear"`, `"brush"` | Visual holding and use animation |
| `:UseDuration(ticks)` | `integer` | Duration item can be held/used (e.g. 72000 for bows) |
| `:OnUseTick(fn)` | `function(player, stack, count)` | Called every tick while holding right-click |
| `:OnReleaseUsing(fn)` | `function(player, stack, timeLeft)` | Called when player releases right-click |
| `:OnFinishUsing(fn)` | `function(player, stack)` | Called when use duration expires |
| `:OnHurtEnemy(fn)` | `function(target, attacker)` | Combat hook when hitting an entity |
| `:OnMineBlock(fn)` | `function(player, state)` | Hook when breaking a block with tool |
| `:OnSwing(fn)` | `function(player, stack)` | Hook on left-click arm swing |

```lua
local Content = require("LuaTweaker.Content")

local Warhammer = Content.NewItem("mymod:warhammer")
    :DisplayName("Seismic Warhammer")
    :Durability(1500)
    :AttackDamage(12.0)
    :AttackSpeed(0.9)
    :MiningSpeed(8.0)
    :MiningLevel(4)
    :UseAnimation("bow")
    :UseDuration(72000)
    :OnUseTick(function(player, stack, count)
        local charged = 72000 - count
        if charged % 10 == 0 and charged > 0 then
            local World = require("LuaTweaker.World")
            World:SpawnParticle("minecraft:crit", player.Position.X, player.Position.Y + 0.5, player.Position.Z, 0.2, 0.2, 0.2, 5, 0.1)
        end
    end)
    :OnReleaseUsing(function(player, stack, timeLeft)
        local charged = 72000 - timeLeft
        if charged >= 20 then
            local World = require("LuaTweaker.World")
            World:SpawnParticle("minecraft:explosion", player.Position.X, player.Position.Y, player.Position.Z, 0.5, 0.1, 0.5, 3, 0.2)
            player:sendActionBar("Seismic shockwave triggered!")
        end
        return true
    end)
    :OnHurtEnemy(function(target, attacker)
        attacker:sendActionBar("Armor crushed!")
    end)
    :Register()
```

---

## 9. Machine GUI and Interactive Widgets

Machine blocks can define container layouts with slots, FE/fluid bars, interactive buttons, dynamic text labels, and neighbor change callbacks:

### 9.1 Container and Widget Builders

| Method | Parameters | Description |
|---|---|---|
| `:Container(rows, cols, dropMode)` | `rows: int, cols: int, dropMode: "spill"|"packed"|"none"` | Turns block into a container |
| `:SlotPosition(slot, x, y)` | `slot: int, x: int, y: int` | Positions a slot on the screen |
| `:GuiBar(id, x, y, w, h, source, color)` | `id: str, x: int, y: int, w: int, h: int, source: "energy"|"fluid"|"progress", color: int` | Adds a fill bar widget |
| `:Button(id, x, y, w, h, text)` | `id: str, x: int, y: int, w: int, h: int, text: str` | Adds a clickable button |
| `:Label(x, y, text, [color])` | `x: int, y: int, text: str, color: int` | Adds text label |
| `:OnButtonClick(fn)` | `function(blockPos, buttonId)` | Server-side callback when player clicks a button |
| `:OnNeighborChanged(fn)` | `function(blockPos, neighborPos)` | Callback on block or redstone updates |

```lua
local Content = require("LuaTweaker.Content")

local Infuser = Content.NewBlock("mymod:infuser")
    :DisplayName("Arcane Infuser")
    :Container(3, 9, "spill")
    :ContainerTitle("Arcane Infuser")
    :SlotPosition(0, 44, 30)
    :SlotPosition(1, 116, 30)
    :GuiBar("energy_bar", 8, 20, 16, 50, "energy", 0xAA0000)
    :Button("start_btn", 68, 30, 40, 20, "Start")
    :Label(68, 55, "Ready", 0x44FF44)
    :OnButtonClick(function(blockPos, buttonId)
        if buttonId == "start_btn" then
            print("Process started at " .. blockPos.X .. ", " .. blockPos.Y .. ", " .. blockPos.Z)
        end
    end)
    :OnNeighborChanged(function(blockPos, neighborPos)
        -- React to neighbor block or redstone updates
    end)
    :Register()
```

---

## 10. Multiblock Structures and Resource Transfer Primitives (`World`)

Low-level primitives for checking multiblock shapes, querying redstone power, and routing energy, fluids, and items:

### 10.1 World Primitives

| Method | Parameters | Return | Description |
|---|---|---|---|
| `World:CheckPattern(level, x, y, z, pattern)` | `level: str, x: int, y: int, z: int, pattern: table` | `boolean` | Checks if pattern matches relative to origin |
| `World:GetRedstoneSignal(level, x, y, z, [dir])` | `level: str, x: int, y: int, z: int, dir: str?` | `integer` | Returns redstone signal level (0 to 15) |
| `World:TransferEnergy(level, from, to, amount, sim)` | `level: str, from: {X,Y,Z}, to: {X,Y,Z}, amount: int, sim: bool` | `integer` | Transfers FE between blocks |
| `World:TransferFluid(level, from, to, amount, sim)` | `level: str, from: {X,Y,Z}, to: {X,Y,Z}, amount: int, sim: bool` | `integer` | Transfers fluid (mB) between blocks |
| `World:TransferItem(level, from, to, slotFrom, slotTo, maxCount, sim)` | `level: str, from: {X,Y,Z}, to: {X,Y,Z}, slotFrom: int, slotTo: int, maxCount: int, sim: bool` | `integer` | Transfers items between container slots |

```lua
local World = require("LuaTweaker.World")

-- Define multiblock pattern offsets
local PATTERN = {
    { x = -1, y = -1, z = -1, block = "minecraft:iron_block" },
    { x =  0, y = -1, z =  0, block = "minecraft:obsidian" },
    { x =  1, y = -1, z =  1, block = "minecraft:iron_block" },
}

-- Check if structure is formed
local formed = World:CheckPattern("minecraft:overworld", 100, 64, 200, PATTERN)

-- Query redstone signal from below
local redstone = World:GetRedstoneSignal("minecraft:overworld", 100, 64, 200, "down")

-- Transfer energy and items
if formed and redstone > 0 then
    local movedFE = World:TransferEnergy("minecraft:overworld", {X = 100, Y = 63, Z = 200}, {X = 100, Y = 64, Z = 200}, 500, false)
    local movedItems = World:TransferItem("minecraft:overworld", {X = 100, Y = 64, Z = 200}, {X = 101, Y = 64, Z = 200}, 1, 0, 1, false)
end
```


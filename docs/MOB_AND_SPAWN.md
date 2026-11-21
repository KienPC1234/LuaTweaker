# Mobs, Equipment, Loot Tables & Spawn Logic

LuaTweaker provides a structured, module-based API for mob spawning, loot table management, equipment customization, and random utilities.

---

## 1. Module Imports

All runtime entities, loot modifications, and event listeners require explicit module imports:

```lua
local Entities = require("LuaTweaker.Entities")
local Loot     = require("LuaTweaker.Loot")
local Events   = require("LuaTweaker.Events")
local Utils    = require("LuaTweaker.Utils")
local Vector3  = require("LuaTweaker.Math.Vector3")
```

---

## 2. Custom Mob Spawning (`Entities`)

Spawn mobs with explicit positions (`Vector3`), equipment, attributes, and status effects:

```lua
Entities.SpawnMob("minecraft:zombie", Vector3.new(100, 65, 200), function(mob)
    -- Custom Name & Visibility
    mob:SetCustomName("§cRuby Realm Boss")
    mob:SetCustomNameVisible(true)

    -- Equipment
    mob:SetMainHand("luatweaker:custom_ruby_sword")
    mob:SetOffHand("minecraft:shield")
    mob:SetHelmet("minecraft:diamond_helmet")
    mob:SetChestplate("minecraft:netherite_chestplate")
    mob:SetLeggings("minecraft:netherite_leggings")
    mob:SetBoots("minecraft:diamond_boots")

    -- Attributes
    mob:SetMaxHealth(100.0)
    mob:SetSpeed(0.35)
    mob:SetAttackDamage(8.0)
    mob:SetArmor(10.0)
    mob:SetKnockbackResistance(0.5)

    -- States & Effects
    mob:SetBaby(false)
    mob:SetGlowing(true)
    mob:AddEffect("minecraft:speed", 6000, 2)
    mob:AddEffect("minecraft:regeneration", 6000, 1)
end)
```

---

## 3. Loot Table Configuration (`Loot`)

Modify mob and block drops directly with min/max count ranges and chance percentages:

```lua
-- Add 1-3 Rubies to Zombie drops with 35% chance
Loot.AddEntityDrop("minecraft:zombie", "luatweaker:custom_ruby", 1, 3, 0.35)

-- Remove Gunpowder from Creeper drops
Loot.RemoveEntityDrop("minecraft:creeper", "minecraft:gunpowder")

-- Clear default drops for Skeleton
Loot.ClearEntityDrops("minecraft:skeleton")

-- Block drops: 5% chance Stone drops a Ruby
Loot.AddBlockDrop("minecraft:stone", "luatweaker:custom_ruby", 1, 1, 0.05)
Loot.RemoveBlockDrop("minecraft:dirt", "minecraft:dirt")
Loot.ClearBlockDrops("minecraft:gravel")
```

---

## 4. Dynamic Spawn Event Hooks (`Events.OnEntitySpawned`)

Intercept entity spawn events at runtime and react to mobs dynamically based on position or type:

```lua
Events.OnEntitySpawned:Connect(function(event)
    local entity = event.Entity
    local pos = entity.Position -- Vector3(X, Y, Z)

    if entity.Type == "minecraft:creeper" then
        print(string.format("Creeper spawned at Vector3(%.1f, %.1f, %.1f)", pos.X, pos.Y, pos.Z))
    end

    if pos.Y > 120 then
        entity:SetAttribute("IsHighAltitude", "true")
    end
end)
```

---

## 5. Random Utility Helper (`Utils`)

Random generation helpers for drops, events, worldgen, and loot calculations:

```lua
-- Percentage Chance (0.25 = 25%)
if Utils.Chance(0.25) then
    print("25% chance succeeded!")
end

-- Weighted Random Selection
local drop = Utils.WeightedRandom({
    ["minecraft:diamond"] = 5,
    ["minecraft:gold_ingot"] = 25,
    ["minecraft:iron_ingot"] = 70
})

-- Random Choice from Array Table
local itemChoice = Utils.RandomChoice({"Apple", "Banana", "Cherry"})

-- Shuffle Array Table in Place
local deck = {"CardA", "CardB", "CardC", "CardD"}
Utils.Shuffle(deck)

-- Random Range (Float)
local speed = Utils.RandomRange(0.2, 0.4)
```

---

## 6. Dimension spawn control (`SpawnRules`)

Two independent ways to control spawning inside a Lua dimension. Rules go through the vanilla
natural spawner (caps, pack size, despawn and difficulty apply); handlers spawn exactly what
the script asks for, with server-side ground/collision/event validation.

### 6.1 Declarative rules (recommended for normal spawning)

`SpawnRules:AddRule` installs conditions that the **vanilla natural spawner** evaluates at
every candidate position, so you get normal mob behaviour without writing a spawn loop:

```lua
local SpawnRules = require("LuaTweaker.SpawnRules")

SpawnRules:AddRule("mymod:wilds", {
    entity    = "minecraft:zombie",
    category  = "monster",       -- optional; defaults to the entity's own category
    weight    = 20,              -- relative chance against the other matching rules
    minGroup  = 1,
    maxGroup  = 3,

    y         = { -4, 100 },     -- inclusive absolute Y range
    light     = { 0, 7 },        -- block light
    skyLight  = { 0, 4 },        -- raw sky light (0 = never saw the sky)
    time      = "night",         -- "day" | "night" | nil (any)
    weather   = "rain",          -- "clear" | "rain" | "thunder" | nil (any)
    biomes    = { "minecraft:plains", "minecraft:forest" },
    blockBelow = { "minecraft:grass_block", "#minecraft:dirt" },
    solidGround = true,

    -- Optional arbitrary predicate; the only part that runs Lua per attempt.
    check = function(ctx)
        return ctx.y < 80 and ctx.blockLight == 0
    end,
})
```

Rules are plain data except `check`; a table can live in `default_config.json` and be fed
straight in with a loop.

Semantics that matter:

- **Once a dimension has at least one rule, the biome's default spawn list is replaced** by
  the rules for that dimension. Add a rule for every category you still want.
- Rules are matched per category: a rule only participates in attempts of its entity's
  category (`monster`, `creature`, `ambient`, `water_creature`, ...).
- `weight = 0` keeps a rule registered but disables it (the weighted pick skips it).
- Vanilla still checks `SpawnPlacements` (solid ground, water, light rules), the per-category
  mob cap, pack size, and despawn. Rules only decide *which* mobs may be chosen where.
- `light` is block light and `skyLight` is raw sky light: a surface block has sky light 15
  even at night, so use `time = "night"` for night-only surface spawns and `skyLight = {0, 4}`
  for caves.
- `check(ctx)` receives `{ dimensionId, entity, x, y, z, blockLight, skyLight, time, weather,
  biome }`. Returning `false` vetoes; returning nothing (or `true`) allows.
- Rules control **natural** spawning. The worldgen pass that seeds passive animals still uses
  the dimension config's `spawnEntities` (`CREATURE` only), exactly like vanilla.

Other rule functions:

```lua
local rules = SpawnRules:GetRules("mymod:wilds")  -- array of rule tables, for inspection
SpawnRules:ClearRules("mymod:wilds")              -- remove rules, keep any handler
```

### 6.2 Full-control handler (for scripted waves, bosses, events)

```lua
SpawnRules:RegisterHandler("mymod:arena", function(dimensionId, players)
    return {
        { entity = "minecraft:zombie", x = 10, y = 65, z = 10, count = 4, spread = 3,
          maxNearby = 12 },
    }
end)
```

Every entry is validated server-side: the chunk must be loaded, the entity's spawn placement
must accept the block, its hitbox must fit, and `FinalizeSpawnEvent` can still veto the spawn.
`y` is optional (the entity's valid surface Y is used), `count` spawns a group (max 32),
`spread` randomises the horizontal offset (max 16), and `maxNearby` stops the group when that
many of the same entity already exist within 16 blocks. Entries that fail validation are
skipped; a multi-mob entry logs how many of its group made it.

`SpawnRules:OnSpawnCheck(function(entity, dimensionId, x, y, z) ... end)` is a global veto hook
for every spawn, and the events `MobSpawnAttempt` (cancellable, before the spawn) and
`MobSpawned` (after it) fire for handler spawns.

### 6.3 Inspecting spawns on a live server

```
/lt spawn list <dimension>       rules of a dimension
/lt spawn test [dimension]       evaluate the rules at your position (ALLOW/BLOCK per rule)
/lt spawn clear [dimension]      remove the rules of a dimension
/lt spawn <entity> [count]       force a validated spawn at your position
```


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

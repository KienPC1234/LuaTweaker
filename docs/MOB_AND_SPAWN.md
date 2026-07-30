# 🧟 Mobs, Equipment, Loot Tables, Spawn Rules & Random Utilities

> **Global Variables:** `entities`, `loot`, `utils`  
> **Service Lookup:** `game:GetService("Entities")`, `game:GetService("Loot")`, `game:GetService("Utils")`

LuaTweaker provides full mob spawning, equipment customization, custom loot table rules, and random utilities.

> 💡 **Service Registry Paradigm**: Access entities API via `entities` or `game:GetService("Entities")`, and loot API via `loot` or `game:GetService("Loot")`.

### Basic Custom Mob Spawn
```lua
entities:spawnMob("minecraft:zombie", 100, 65, 200, function(mob)
    -- Custom Name & Visibility
    mob:setCustomName("§c🔥 Ruby Realm Boss 🔥")
    mob:setCustomNameVisible(true)

    -- Equipment (Weapons & Armor)
    mob:setMainHand("luatweaker:custom_ruby_sword")
    mob:setOffHand("minecraft:shield")
    mob:setHelmet("minecraft:diamond_helmet")
    mob:setChestplate("minecraft:netherite_chestplate")
    mob:setLeggings("minecraft:netherite_leggings")
    mob:setBoots("minecraft:diamond_boots")

    -- Attributes
    mob:setMaxHealth(100.0)
    mob:setSpeed(0.35)
    mob:setAttackDamage(8.0)
    mob:setArmor(10.0)
    mob:setKnockbackResistance(0.5)

    -- States
    mob:setBaby(false)
    mob:setGlowing(true)
    mob:setSilent(false)

    -- Status Effects
    mob:addEffect("minecraft:speed", 6000, 2)
    mob:addEffect("minecraft:regeneration", 6000, 1)
end)
```

### Zombie Quick Spawn Shortcut
```lua
entities:spawnZombie(x, y, z, "luatweaker:custom_ruby_sword", "minecraft:iron_helmet", "minecraft:iron_chestplate")
```

---

## 2. 🍖 Mob & Block Loot Table Configuration (`loot`)

Modify mob and block drops directly with min/max count ranges and chance percentages.

```lua
-- Add 1-3 Rubies to Zombie drops with 35% chance
loot:addEntityDrop("minecraft:zombie", "luatweaker:custom_ruby", 1, 3, 0.35)

-- Remove Gunpowder from Creeper drops
loot:removeEntityDrop("minecraft:creeper", "minecraft:gunpowder")

-- Clear ALL default drops for Skeleton
loot:clearEntityDrops("minecraft:skeleton")

-- Block drops: 5% chance Stone drops a Ruby
loot:addBlockDrop("minecraft:stone", "luatweaker:custom_ruby", 1, 1, 0.05)
loot:removeBlockDrop("minecraft:dirt", "minecraft:dirt")
loot:clearBlockDrops("minecraft:gravel")
```

---

## 3. 🌍 World Mob Spawn Rules & Deny Filters (`worldgen`)

Configure natural mob spawning weights, group sizes, and block undesired mobs or entire mods from spawning in biomes.

```lua
-- Add natural spawn rule: Zombies in Overworld
-- worldgen:addSpawnRule(entityType, category, minGroup, maxGroup, weight, biomeFilter)
worldgen:addSpawnRule("minecraft:zombie", "MONSTER", 1, 4, 120, "#minecraft:is_overworld")

-- Deny specific mob spawns in Plains biome
worldgen:denySpawn("minecraft:phantom", "minecraft:plains")
worldgen:denySpawn("minecraft:creeper", "minecraft:desert")

-- Deny ALL mob spawns from a specific mod in Nether Wastes
worldgen:denyModSpawns("annoying_mod_id", "minecraft:nether_wastes")
```

---

## 4. 🛑 Dynamic Spawn Event Hooks & Deny (`events:listen("entity.spawn")`)

Intercept mob spawning events at runtime and dynamically deny/cancel spawns based on distance, time, chance, or custom logic.

```lua
events:listen("entity.spawn", function(e)
    local entityType = e:getEntityType()
    local x, y, z = e:getX(), e:getY(), e:getZ()
    local biome = e:getBiome()

    -- Example 1: Deny Creepers with 20% probability
    if entityType == "minecraft:creeper" and utils.chance(0.20) then
        print("[LuaTweaker] Dynamic deny Creeper spawn at (" .. x .. ", " .. y .. ", " .. z .. ")")
        e:deny() -- or e:cancel()
    end

    -- Example 2: Deny all monsters above Y=120
    if y > 120 and e:getSpawnReason() == "NATURAL" then
        e:deny()
    end
end)
```

---

## 5. ⚡ Entity Attribute Effect Shortcuts (`entities`)

Global attribute modifiers applied across entity types.

```lua
entities:setSpeed("minecraft:zombie", 0.30)
entities:setMaxHealth("minecraft:zombie", 40.0)
entities:setAttackDamage("minecraft:zombie", 7.0)
entities:setArmor("minecraft:zombie", 6.0)
entities:setKnockbackResistance("minecraft:zombie", 0.25)
entities:setFollowRange("minecraft:zombie", 48.0)
```

---

## 6. 🎲 High-Quality Random Utility Library (`utils`)

Powerful random generation helpers for drops, events, worldgen, and loot.

```lua
-- Percentage Chance (Accepts decimal fraction 0.25 OR percent 25)
if utils.chance(0.25) then
    print("25% chance succeeded!")
end

-- Weighted Random Selection
local drop = utils.weightedRandom({
    ["minecraft:diamond"] = 5,
    ["minecraft:gold_ingot"] = 25,
    ["minecraft:iron_ingot"] = 70
})

-- Random Choice from Array Table
local fruit = utils.randomChoice({"Apple", "Banana", "Cherry"})

-- Shuffle Array Table in Place
local myDeck = {"CardA", "CardB", "CardC", "CardD"}
utils.shuffle(myDeck)

-- Random Range (Float)
local speed = utils.randomRange(0.2, 0.4)

-- 2D Perlin Noise (-1.0 to 1.0)
local heightNoise = utils.perlinNoise(x * 0.05, z * 0.05)
```

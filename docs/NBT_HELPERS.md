# 🧪 Easy NBT Manipulation Library (`nbt`) & Enums (`enums`)

## 1. 🏷️ NBT & SNBT Helper Library (`nbt`)

LuaTweaker provides the global `nbt` library to convert seamlessly between intuitive Lua tables and Minecraft SNBT (Stringified NBT) data structures without writing complex JSON syntax.

### Create SNBT from Lua Table (`nbt.compound`)
```lua
-- Convert a Lua table into a Minecraft SNBT string
local snbtTag = nbt.compound({
    CustomName = "§c🔥 Overlord Ruby Sword 🔥",
    Damage = 0,
    Unbreakable = true,
    RubyPower = 9999,
    Attributes = {
        BonusDamage = 15.5
    }
})
-- Result -> '{CustomName:"§c🔥 Overlord Ruby Sword 🔥",Damage:0,Unbreakable:1b,RubyPower:9999,Attributes:{BonusDamage:15.5f}}'
```

### Parse SNBT to Lua Table (`nbt.parse`)
```lua
-- Parse an SNBT string into a native Lua table
local data = nbt.parse('{CustomName:"Ruby Sword",Damage:0,Unbreakable:1b}')

print(data.CustomName) -- "Ruby Sword"
print(data.Damage)     -- 0
print(data.Unbreakable) -- true
```

### Merge Tables into SNBT (`nbt.merge`)
```lua
local baseTag = '{CustomName:"Ruby Sword",Damage:0}'
local updatedTag = nbt.merge(baseTag, {
    Unbreakable = true,
    Damage = 10
})
```

### Add Enchantments to NBT (`nbt.enchant`)
```lua
local tag = "{}"
tag = nbt.enchant(tag, "minecraft:sharpness", 5)
tag = nbt.enchant(tag, "minecraft:fire_aspect", 2)
-- Result -> '{Enchantments:[{id:"minecraft:sharpness",lvl:5s},{id:"minecraft:fire_aspect",lvl:2s}]}'
```

### Set and Get NBT Values Directly
```lua
local tag = "{Damage:0}"

-- Set property
tag = nbt.set(tag, "Unbreakable", true)
tag = nbt.set(tag, "CustomName", "§aExcalibur")

-- Read property
local name = nbt.get(tag, "CustomName") -- "§aExcalibur"
```

---

## 2. 🗂️ Enum Constants Library (`enums`)

Avoid magic strings and typos by using structured Lua Enums provided by the global `enums` object.

### Available Enum Categories

| Enum Category | Example Values |
|---------------|----------------|
| `enums.Rarity` | `COMMON`, `UNCOMMON`, `RARE`, `EPIC` |
| `enums.BossBarColor` | `RED`, `BLUE`, `GREEN`, `PINK`, `PURPLE`, `YELLOW`, `WHITE` |
| `enums.BossBarStyle` | `PROGRESS`, `NOTCHES_6`, `NOTCHES_10`, `NOTCHES_12`, `NOTCHES_20` |
| `enums.Weather` | `CLEAR`, `RAIN`, `THUNDER` |
| `enums.Difficulty` | `PEACEFUL`, `EASY`, `NORMAL`, `HARD` |
| `enums.EquipmentSlot` | `MAIN_HAND` (`"mainhand"`), `OFF_HAND` (`"offhand"`), `HEAD` (`"head"`), `CHEST` (`"chest"`), `LEGS` (`"legs"`), `FEET` (`"feet"`) |
| `enums.MobCategory` | `MONSTER`, `CREATURE`, `AMBIENT`, `WATER_CREATURE` |
| `enums.Attribute` | `SPEED` (`"generic.movement_speed"`), `MAX_HEALTH` (`"generic.max_health"`), `ATTACK_DAMAGE`, `ARMOR`, `KNOCKBACK_RESISTANCE` |

### Enum Code Examples
```lua
-- BossBar Color & Style
bar:setColor(enums.BossBarColor.RED)
bar:setStyle(enums.BossBarStyle.NOTCHES_10)

-- Custom Item Rarity
startup:createItem("ruby_crystal", function(i)
    i:rarity(enums.Rarity.EPIC)
end)

-- World Difficulty & Weather
world:setDifficulty(enums.Difficulty.HARD)
world:setRain(false)

-- Mob Spawn Rule Category
worldgen:addSpawnRule("minecraft:zombie", enums.MobCategory.MONSTER, 1, 4, 120, "#minecraft:is_overworld")
```

---

## 3. 🗓️ World Game Days & Real Calendar Dates

Get the exact Minecraft Game Day, 0-indexed day count, and real-world date/time strings directly from `world`.

```lua
-- Minecraft In-Game Days
local dayZero = world:getDay()      -- 0, 1, 2, ... (0-indexed)
local gameDay = world:getGameDay()  -- Day 1, Day 2, Day 3, ... (1-indexed)

-- Jump to Day 5 directly
world:setGameDay(5)

-- Real World Calendar Dates & System Time
local dateStr     = world:getRealDate()     -- e.g. "2026-07-29"
local timeStr     = world:getRealTime()     -- e.g. "11:32:04"
local dateTimeStr = world:getRealDateTime() -- e.g. "2026-07-29 11:32:04"

print("Minecraft Game Day: Day " .. world:getGameDay() .. " | Real System Date: " .. world:getRealDate())
```

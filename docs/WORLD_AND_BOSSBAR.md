# 👑 BossBar & 🌐 World Server Data Interaction API

> **Global Variables:** `bossbar`, `world`, `workspace`  
> **Service Lookup:** `game:GetService("BossBar")`, `game:GetService("World")`, `workspace`

LuaTweaker provides a complete BossBar and World Server interaction suite.

> 💡 **Service Registry Paradigm**: Access BossBar API via `bossbar` or `game:GetService("BossBar")`, and World API via `world`, `workspace`, or `game:GetService("World")` / `game:GetService("Workspace")`.

### Create & Configure BossBar
```lua
-- Create a custom BossBar
local bar = bossbar:create("ruby_boss_bar", "§c🔥 Ruby Realm Overlord 🔥")

-- Set Color: PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE
bar:setColor("RED")

-- Set Style: PROGRESS, NOTCHES_6, NOTCHES_10, NOTCHES_12, NOTCHES_20
bar:setStyle("NOTCHES_10")

-- Set Progress (0.0 to 1.0)
bar:setProgress(1.0)

-- Screen & Ambient FX
bar:setDarkenScreen(true)
bar:setPlayBossMusic(true)
bar:setCreateWorldFog(true)

-- Show to all online players
bar:showToAll()

-- Or target specific players
bar:addPlayer("Steve")
bar:removePlayer("Alex")
```

### Animate Progress in Scheduler
```lua
scheduler:repeat_(20, 10, function(tick)
    local current = bar:getProgress()
    bar:setProgress(current - 0.1)
    if bar:getProgress() <= 0 then
        bossbar:remove("ruby_boss_bar")
    end
end)
```

---

## 2. 🌐 World Data & Server Environment API (`world`)

Interact with world time, weather, game rules, execute server commands, broadcast messages, and perform block/explosion operations directly from Lua.

### Time Operations
```lua
world:setDay()          -- Set time to 1000 ticks
world:setNoon()         -- Set time to 6000 ticks
world:setNight()        -- Set time to 13000 ticks
world:setMidnight()     -- Set time to 18000 ticks
world:setTime(12000)    -- Set exact ticks

local time = world:getTime()
local dayTime = world:getDayTime()
if world:isDay() then print("It is day!") end
if world:isNight() then print("It is night!") end
```

### Weather Control
```lua
world:setClear()        -- Clear weather
world:setRain(true)     -- Start rain
world:setThunder(true)  -- Start thunderstorm

if world:isRaining() then print("Raining!") end
if world:isThundering() then print("Thundering!") end
```

### Server Commands & GameRules
```lua
-- Execute any server command as Console
world:executeCommand("say Hello from Lua script!")
world:executeCommand("give Dev minecraft:diamond 64")

-- Broadcast server chat message
world:broadcast("§a[LuaTweaker] Event active!")

-- GameRules
world:setGameRule("doDaylightCycle", false)
world:setGameRule("keepInventory", true)
local isKeepInv = world:getGameRuleBoolean("keepInventory")
```

### Block & Explosion Operations
```lua
-- Set Block at Coordinates
world:setBlock(0, 65, 0, "minecraft:diamond_block")

-- Query Block
local id = world:getBlock(0, 65, 0) -- "minecraft:diamond_block"

-- Break Block
world:breakBlock(0, 65, 0, true) -- dropItems = true

-- Spawn Explosion (x, y, z, power, causesFire)
world:spawnExplosion(10, 65, 10, 4.0, false)
```

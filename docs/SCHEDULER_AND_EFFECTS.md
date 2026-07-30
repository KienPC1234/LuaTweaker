# ⏱️ Scheduler, 🔊 Particles & Sound, 🐲 Entity, 🧙 Enchantments & Effects

## Scheduler / Timer API (`scheduler` & `task`)

The `scheduler` global and `task` library provide tick-based delayed, repeating, and async task scheduling.

> 💡 For modern reactive task scheduling (`task.spawn`, `task.delay`, `task.defer`, `task.wait`), `Vector3` particle calculations, and `TweenService` animations, see [**SERVICE_AND_SPATIAL_MATH_API.md**](SERVICE_AND_SPATIAL_MATH_API.md).

### One-Shot Delay
```lua
-- Execute callback after 100 ticks (5 seconds)
scheduler:delay(100, function(tick)
    print("Fired at tick " .. tick)
end)
```

### Repeating Tasks
```lua
-- Execute every 20 ticks (1 second), up to 10 times
scheduler:repeat_(20, 10, function(tick)
    print("Tick: " .. tick)
end)

-- Infinite repeat (use -1)
scheduler:repeat_(40, -1, function(tick)
    -- Broadcast every 2 seconds forever
    print("Server heartbeat at tick " .. tick)
end)
```

### Named Tasks (Cancellable)
```lua
-- Schedule a named task (replaces any existing task with same name)
scheduler:named("boss_spawn", 200, function()
    entities:spawn("minecraft:wither", 0, 80, 0)
    sounds:play("minecraft:entity.wither.spawn", 0, 80, 0, 1.0, 1.0)
end)

-- Cancel it before it fires
scheduler:cancel("boss_spawn")

-- Named repeating task
scheduler:namedRepeat("health_regen", 40, -1, function()
    -- Heal all players every 2 seconds
end)
```

---

## Particle API (`particles`)

Spawn visual particle effects at world positions.

### Basic Spawn
```lua
-- spawn(type, x, y, z, count, offsetX, offsetY, offsetZ, speed)
particles:spawn("minecraft:flame", 100.5, 65.0, 200.5, 10, 0.2, 0.3, 0.1, 0.01)
```

### Quick Burst
```lua
-- burst(type, x, y, z, count) — uses default offsets
particles:burst("minecraft:heart", 50, 70, 50, 30)
```

### Line Effect
```lua
-- line(type, x1,y1,z1, x2,y2,z2, particlesPerBlock, speed)
particles:line("minecraft:end_rod", 0, 65, 0, 10, 65, 10, 20, 0.01)
```

### Circle / Ring Effect
```lua
-- circle(type, centerX, centerY, centerZ, radius, count, speed)
particles:circle("minecraft:enchant", 0, 70, 0, 3.0, 32, 0.02)
```

---

## Sound API (`sounds`)

Play sounds at world positions or to specific players.

### Positional Sound
```lua
sounds:play("minecraft:entity.ender_dragon.growl", 100.5, 65.0, 200.5, 1.0, 1.0)
```

### Player-Targeted Sound
```lua
sounds:playToPlayer("Steve", "minecraft:block.note_block.harp", 1.0, 2.0)
```

### Quick Play (Default Volume/Pitch)
```lua
sounds:playAt("minecraft:entity.experience_orb.pickup", 0, 64, 0)
```

### Stop Sound
```lua
sounds:stopAll("minecraft:music.creative")
```

---

## Entity API (`entities`)

Spawn, remove, query, and modify entities.

### Spawn
```lua
entities:spawn("minecraft:zombie", 100, 65, 200)
entities:spawnWithNbt("minecraft:armor_stand", 100, 65, 200, '{NoGravity:1b}')
entities:spawnMultiple("minecraft:skeleton", 100, 65, 200, 5, 3.0)
```

### Remove
```lua
-- Remove all creepers within 32 blocks
entities:removeAll("minecraft:creeper", 100, 65, 200, 32)
```

### Modify Attributes
```lua
entities:modifyAttribute("minecraft:zombie", "generic.max_health", 40.0)
entities:modifyAttribute("minecraft:zombie", "generic.movement_speed", 0.35)
```

### Count Nearby
```lua
local count = entities:countNearby("minecraft:zombie", 100, 65, 200, 50)
if count > 10 then
    print("Too many zombies!")
end
```

---

## Enchantment Builder (`startup:createEnchantment`)

Register custom enchantments with full configuration.

```lua
startup:createEnchantment("ruby_fortune", function(e)
    e:maxLevel(5)
    e:rarity("RARE")             -- COMMON, UNCOMMON, RARE, VERY_RARE
    e:addSlot("mainhand")
    e:treasure(false)
    e:curse(false)
    e:cost(10, 50)               -- Enchanting cost range
end)
```

---

## Mob Effect Builder (`startup:createMobEffect`)

Register custom status effects with attribute modifiers.

```lua
startup:createMobEffect("ruby_speed", function(e)
    e:color(0xFF0000)
    e:beneficial(true)
    e:category("BENEFICIAL")     -- BENEFICIAL, HARMFUL, NEUTRAL
    e:attribute("generic.movement_speed", 0.4, "ADD_MULTIPLIED_TOTAL")
    e:particle("minecraft:happy_villager")
end)
```

---

## Potion Builder (`startup:createPotion`)

Register custom potions combining a mob effect with duration and amplifier.

```lua
startup:createPotion("ruby_swiftness", "luatweaker:ruby_speed", 3600, 1)
```

---

## Creative Tab Builder (`startup:createCreativeTab`)

Register custom creative inventory tabs.

```lua
startup:createCreativeTab("ruby_tab", function(tab)
    tab:title("Ruby Collection")
    tab:icon("luatweaker:custom_ruby")
    tab:addItem("luatweaker:custom_ruby")
    tab:addItem("luatweaker:custom_ruby_block")
    tab:searchBar(true)
    tab:background("luatweaker:textures/gui/ruby_tab.png")
end)
```

---

## Utility Library (`utils`)

Built-in helper functions available via the `utils` global.

### UUID & Random
```lua
local id = utils.uuid()                    -- "550e8400-e29b-..."
local roll = utils.randomInt(1, 100)       -- Random integer 1-100
local chance = utils.randomFloat(0, 1.0)   -- Random float 0.0-1.0
```

### Math
```lua
local clamped = utils.clamp(150, 0, 100)   -- 100
local lerped = utils.lerp(0, 100, 0.5)     -- 50.0
local dist = utils.distance(0,0,0, 3,4,0)  -- 5.0
utils.floor(3.7)   -- 3
utils.ceil(3.2)    -- 4
utils.round(3.5)   -- 4
```

### Strings
```lua
utils.split("a,b,c", ",")                  -- {"a","b","c"}
utils.startsWith("hello world", "hello")   -- true
utils.endsWith("hello.lua", ".lua")        -- true
utils.trim("  hello  ")                    -- "hello"
utils.lower("HELLO")                       -- "hello"
utils.upper("hello")                       -- "HELLO"
```

### Tables
```lua
local keys = utils.keys({a=1, b=2})        -- {"a","b"}
local vals = utils.values({a=1, b=2})      -- {1,2}
local sz = utils.size({a=1, b=2, c=3})     -- 3
local copy = utils.deepCopy(originalTable)
```

### Colors
```lua
utils.color(255, 0, 128)                   -- "#FF0080"
utils.colorInt(255, 128, 0)                -- 16744448
```

---

## Player API (Expanded)

The `Player` object now provides comprehensive player state management.

```lua
events:listen("player.join", function(player)
    -- Identity
    print(player:getUsername())

    -- Health
    player:heal(5)
    player:damage(2)
    player:setMaxHealth(40)
    if player:isAlive() then print("Still alive!") end

    -- Position & Teleport
    local x, y, z = player:getX(), player:getY(), player:getZ()
    player:teleport(0, 100, 0)
    player:teleportDimension("minecraft:the_nether", 0, 128, 0)
    local dist = player:distanceTo(100, 64, 100)

    -- Experience
    player:addXP(500)
    player:setXPLevel(30)

    -- Food
    player:setFoodLevel(20)
    player:setSaturation(5.0)

    -- Gamemode
    player:setGamemode("creative")
    if player:isCreative() then print("Creative mode!") end
    if player:isSurvival() then print("Survival!") end

    -- Effects
    player:addEffect("minecraft:speed", 600, 2)
    player:removeEffect("minecraft:speed")
    player:clearEffects()

    -- Messaging
    player:sendMessage("§aWelcome!")
    player:sendActionBar("§eAction bar text")
    player:sendTitle("§6Welcome", "§7to the server", 10, 70, 20)
    player:playSound("minecraft:entity.player.levelup", 1.0, 1.0)
end)
```

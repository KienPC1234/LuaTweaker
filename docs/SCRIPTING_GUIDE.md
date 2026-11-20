# Scripting Guide

How LuaTweaker Lua is shaped: explicit modules, Builder DSLs for static
definitions, Signals for runtime. If you only read one conventions doc,
read this one.

## 1. Rules (LuaTweaker style)

1. **`local` everything.** No implicit globals (engine-provided `mod`, `Mod`,
   `Events`/`Content`/… are the only exceptions).
2. **Explicit imports.** Every library enters via `require("LuaTweaker.X")` —
   `Content`, `Recipe`, `Events`, `Task`, `Brain`, `GuiService`, …
   (`LuaTweaker.Brain` also exists as the `Brain` global.)
3. **Builders end with `:Register()`.** Static things (items, recipes) are
   declared with chainable PascalCase builders in `src/startup`.
4. **Runtime is reactive.** Game logic lives in `Events:Listen(...)`,
   entity `onTick`/`onHurt`/`onDeath` hooks, `Task:delay/spawn`, and `Signal`s.
5. **Tunables live in config.** Costs, cooldowns, radii, geometry come from
   `mod:GetConfig()` (backed by `default_config.json` → `luaconfig/<id>.json`)
   with a sane Lua-side fallback — never hardcode.
6. **Naming:** `local` vars `camelCase`, API exports `PascalCase`,
   constants `UPPER_SNAKE_CASE`, booleans `is*/has*/can*`, callbacks `on*`.
   Max ~120 cols, `:` method calls, chains of ~3 max.

File shape: banner → `require`s → config → constants → private fns →
public API → final `print()`.

## 2. Static definitions (`Content`, `Recipe`)

Startup only. Builders declare data; they do no game logic.

```lua
local Content = require("LuaTweaker.Content")

Content.NewItem("ruby_gem")
    :DisplayName("Ruby Gem")
    :MaxStackSize(16)
    :Rarity("EPIC")
    :Register()

Content.NewBlock("ruby_block")
    :DisplayName("Ruby Block")
    :Hardness(3.0)
    :Resistance(12.0)
    :Register()

local Recipe = require("LuaTweaker.Recipe")
Recipe.Shaped("ruby_block_craft")
    :Pattern({ "RRR", "RRR", "RRR" })
    :Key("R", ingredient("luatweaker:ruby_gem"))
    :Output(item("luatweaker:ruby_block", 1))
    :Register()
```

Entities use `createEntity` + `rig()` (see [CUSTOM_CONTENT](CUSTOM_CONTENT.md)):

```lua
Content.createEntity("crystal_wisp", function(e)
    e:category("MONSTER"):rig("quadruped")
     :texture("luatweaker:textures/entity/crystal_beast.png")
     :dimensions(0.9, 1.4):maxHealth(40):movementSpeed(0.3)
     :tickRate(5)
     :onTick(function(ent) -- per-type AI end)
     :onHurt(function(ent, amount, attacker) return amount end)
end)
```

## 3. Runtime (`Events`, `Task`, `Signal`, entity API)

```lua
local Events = require("LuaTweaker.Events")
local Task   = require("LuaTweaker.Task")

Events:Listen("ServerTick", function()
    -- server tick logic; throttle expensive scans (see Brain doc cost rules)
end)

Task:delay(2.0, function()
    print("Delayed task fired!")
end)

local Signal = require("LuaTweaker.Signal")
local onReady = Signal.new()
local conn = onReady:Connect(function(name) print("ready: " .. name) end)
onReady:Fire("wisp")      -- listeners run on the NEXT tick
onReady:FireSync("wisp")  -- listeners run inline (render thread / HUD only)
conn:Disconnect()
```

One event bus, shared across engines: `Listen` **replaces** the previous
engine's listener (reload-safe); `Fire` is async-next-tick, `FireSync` is
inline (required for `OnRenderHUD`). Full semantics: [EVENTS](EVENTS.md).

Entities expose **one** table with both call styles:

```lua
player:setHealth(40)
player.Health = player.Health - 5   -- same field, property style
player:lookAtEntity(target)
player:jump()
ent:getGameTime()                   -- tick clock for AI; never wall-clock
```

## 4. Config and mod identity

```lua
local cfg = mod:GetConfig()
local radius = (cfg.brain_demo and cfg.brain_demo.scan_radius) or 32.0

local info = mod:GetUpdateStatus("my_mod")  -- read-only, engine-fetched feed
```

`optional_dependencies`, `mod:HasMod(id)` / `mod:GetModVersion(id)` for
cross-mod checks. Update/HTTP rules: [LUA_MOD_SYSTEM](LUA_MOD_SYSTEM.md).

## 5. Where next

- NPC decision-making: [BEHAVIOR_TREES](BEHAVIOR_TREES.md) (`Brain`).
- Sensing/goals/loot: [MOB_AND_SPAWN](MOB_AND_SPAWN.md).
- Worldgen/biomes/structures: [WORLDGEN](WORLDGEN.md).
- HUD/keybinds: [CLIENT_AND_SOUND_API](CLIENT_AND_SOUND_API.md).
- Failures: per-mod errors in `/lt doctor` + `logs/luatweaker/` —
  [TROUBLESHOOTING](TROUBLESHOOTING.md).

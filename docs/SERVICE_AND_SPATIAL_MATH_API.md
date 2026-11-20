# Runtime Services & Spatial Math Reference Manual

LuaTweaker provides explicit module imports (`require("LuaTweaker.ModuleName")`), asynchronous task scheduling (`Task`), property interpolation (`TweenService`), and spatial math objects (`Vector3`, `Vector2`, `Color3`).

---

## 1. Module Imports

All runtime logic requires explicit module imports without unanchored global magic:

```lua
local Task         = require("LuaTweaker.Task")
local TweenService = require("LuaTweaker.TweenService")
local Vector3      = require("LuaTweaker.Math.Vector3")
local Vector2      = require("LuaTweaker.Math.Vector2")
local Color3       = require("LuaTweaker.Math.Color3")
```

---

## 2. Asynchronous Task Scheduler (`Task`)

| Method Signature | Description |
| :--- | :--- |
| `Task.Spawn(fn, ...args)` | Queues the callback for the next game tick. Returns a handle. |
| `Task.Delay(seconds, fn, ...args)` | Queues the callback after `seconds` of monotonic time. Returns a handle. |
| `Task.Defer(fn, ...args)` | Alias of `Task.Spawn` (next tick). |
| `Task.Wait(seconds)` | Yields the **calling coroutine** until the delay elapses. Only valid inside a task. |
| `Task.Cancel(handle)` | Cancels a queued task that has not started. Returns `false` once it started or was already cancelled. |
| `Task.GetTimeClock()` | Wall-clock seconds (timestamps). Not used for scheduling. |

### Threading model

`Task.Spawn` does **not** run the callback on a background worker. Every task runs on the
thread that owns the Lua VM:

- dedicated server, or a single-player/LAN host: the **server tick** thread;
- a client connected to a remote server: the **client tick** thread.

That is deliberate: server scripts touch `ServerLevel` and entities, which may only be
mutated from the server thread. There is no API to run Lua on an arbitrary thread.

A task runs synchronously until it calls `Task.Wait`. A callback that loops forever without
yielding blocks the tick exactly like a blocking loop would. Always yield in long loops:

```lua
Task.Spawn(function()
    while true do
        Task.Wait(0.5)          -- release the tick thread between iterations
        print("Periodic task tick")
    end
end)
```

`Task.Wait` outside a task (for example directly in `main.lua`, or from `Signal:FireSync`
on the render thread) cannot yield: it prints a warning and returns immediately.

### Ordering, budget and timing

- Tasks queued during a tick (including tasks queued by a task) run on the **next** tick, so
  a task cannot recurse forever inside one tick.
- At most **1,000 tasks run per tick**. Anything over the budget is carried to the next tick,
  not dropped; carried tasks have not started, so `Task.Cancel` still works on them.
- `Task.Delay` deadlines use a monotonic clock, so an NTP correction or a user changing the
  system clock cannot fire a task early or strand it.
- `Task.GetTimeClock()` stays a wall clock for script timestamps; do not diff it around a
  system clock change.

```lua
-- Spawn and Delay return the same handle shape
local handle = Task.Delay(0.5, function() print("0.5 seconds elapsed!") end)
Task.Cancel(handle)             -- true if it had not started yet
```

`/lt doctor` and `task._stats()` report `{ queued, deferred, delayed, executed, budget }`.

---

## 3. Property Interpolation (`TweenService`)

Interpolate properties smoothly over time:

```lua
local TweenService = require("LuaTweaker.TweenService")
local Content      = require("LuaTweaker.Content")

local info = TweenInfo.new(3.0, "Sine", "Out")
local bossbar = Content.GetBossBar("dragon_bar")

local tween = TweenService:Create(bossbar, info, { Percent = 0.0 })
tween:Play()
```

`TweenInfo.new(time, style, direction, repeatCount, reverses, delayTime)` — all six
fields are honored (`repeatCount = -1` loops forever, `reverses = true` ping-pongs).
Styles: `Linear`, `Sine`, `Quad`, `Cubic`, `Quart`, `Quint`, `Expo`, `Circ`, `Back`,
`Elastic`, `Bounce`; directions: `In`, `Out`, `InOut`.

---

## 4. Spatial Math API (`Vector3`, `Vector2`, `Color3`)

### `Vector3` Arithmetic

```lua
local posA = Vector3.new(10, 64, -50)
local posB = Vector3.new(20, 64, -40)

local distance = (posB - posA).Magnitude
local direction = (posB - posA).Unit
local midpoint = posA + (direction * (distance / 2))
```

### `Color3` Engine

```lua
local gold = Color3.fromHex("#FFD700")
local red  = Color3.fromRGB(255, 0, 0)
local lerpedColor = gold:Lerp(red, 0.5)
```

### `Vector2` Screen Geometry

```lua
local screenCenter = Vector2.new(1920 / 2, 1080 / 2)
```

---

## 5. Extended Standard Math Functions (`math`)

```lua
local clamped = math.clamp(150, 0, 100) -- 100
local rounded = math.round(4.6)         -- 5
local signed  = math.sign(-50)          -- -1
local lerped  = math.lerp(10, 20, 0.5)   -- 15
```

---

## 6. Deterministic Noise (`Noise`)

Seeded 2D noise for terrain plus 3D noise for caves, floating islands and
overhangs (all in [-1, 1], reproducible per `SetSeed`):

```lua
local Noise = require("LuaTweaker.Noise")
Noise:SetSeed(1337)

local hills = Noise:fbm(x * 0.01, z * 0.01, 4)       -- 2D mountains
local island = Noise:fbm3(x * 0.02, y * 0.02, z * 0.02, 3) -- 3D density
if island > 0.35 then
    -- solid ground, else carve air
end
```

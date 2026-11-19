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
| `Task.Spawn(fn, ...args)` | Executes callback function asynchronously on a background worker thread. |
| `Task.Delay(seconds, fn)` | Schedules callback execution after specified delay in seconds. |
| `Task.Defer(fn, ...args)` | Defers callback execution until the end of the current tick. |
| `Task.Wait(seconds)` | Pauses execution for specified seconds (default: 0.05s). |
| `Task.Cancel(handle)` | Cancels a pending delayed or deferred task. `Task.Spawn/Delay/Defer` return the handle; `Cancel` returns false once the task started. |

```lua
-- 1. Task.Spawn: Run async task immediately
Task.Spawn(function(msg)
    print("Async task started:", msg)
end, "Magic Staff System")

-- 2. Task.Delay: Delayed execution
Task.Delay(0.5, function()
    print("0.5 seconds elapsed!")
end)

-- 3. Task.Wait: Yield execution in loop
Task.Spawn(function()
    while true do
        Task.Wait(0.5)
        print("Periodic task tick")
    end
end)
```

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

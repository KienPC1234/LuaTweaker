# LuaTweaker - Service-Oriented Reactive Scripting & Spatial Math API Reference Manual

> **Minecraft 1.21.1 (NeoForge) Advanced Scripting Engine**

LuaTweaker provides a high-level, service-oriented reactive scripting model with built-in spatial math utilities (`Vector3`, `Vector2`, `Color3`), asynchronous task scheduling, persistent DataStores, smooth tweening, and hierarchical instance management for Minecraft modpack creators.

---

## 📚 1. Service Registry Architecture Pattern

Access system capabilities and APIs using the unified service lookup paradigm:

```lua
local recipes          = game:GetService("Recipes")
local dataStoreService = game:GetService("DataStoreService")
local tweenService     = game:GetService("TweenService")
local taskService      = game:GetService("Task")
local workspace        = game:GetService("Workspace") -- Or global `workspace`
```

> 💡 `Mod:GetService("...")` and `game:GetService("...")` are interchangeable aliases.

---

## 💡 2. Practical Applications of `Vector3`, `Vector2`, and `Color3` in Minecraft

### 📐 `Vector3` Applications in Minecraft:
1. **Particle Spawning Geometry**: Calculate 3D points for circles, spheres, spirals, or beam lines between entities/players:
   ```lua
   local startPos = Vector3.new(player.x, player.y, player.z)
   local targetPos = Vector3.new(boss.x, boss.y, boss.z)
   local direction = (targetPos - startPos).Unit
   
   -- Spawn particle line towards boss
   for i = 1, 10 do
       local point = startPos + (direction * i)
       particles:spawn("minecraft:end_rod", point.X, point.Y, point.Z)
   end
   ```
2. **Knockback & Velocity Direction**: Calculate directional vectors for custom spell knockback or explosion impulses:
   ```lua
   local distance = startPos:Dot(targetPos)
   local angle = startPos:Angle(targetPos)
   ```
3. **Region & Bounding Box Checks**: Calculate distance thresholds and bounding box limits:
   ```lua
   local dist = (posA - posB).Magnitude
   if dist <= 15 then
       print("Player is within spell radius!")
   end
   ```

### 🎨 `Color3` Applications in Minecraft:
1. **Dynamic Custom Item & HUD Colors**: Tint item glint, potion effects, bossbars, or custom HUD text:
   ```lua
   local goldColor = Color3.fromHex("#FFD700")
   local packedRGB = goldColor:ToRGBInt() -- Converted for Minecraft render pipeline (0xFFD700)
   ```
2. **Time-of-Day Ambient Sky & Lighting Lerp**: Smoothly interpolate ambient lighting/fog colors based on game time:
   ```lua
   local dayColor   = Color3.fromRGB(255, 240, 200)
   local nightColor = Color3.fromRGB(20, 30, 60)
   local currentAmbient = dayColor:Lerp(nightColor, 0.7)
   ```
3. **HSV Rainbow Shift**: Animate bossbar or tooltip text colors dynamically using HSV rotation:
   ```lua
   local rainbowColor = Color3.fromHSV((currentTick % 360) / 360, 1.0, 1.0)
   ```

### 🖼️ `Vector2` Applications in Minecraft:
1. **Client HUD & GUI Coordinates**: Position UI elements, icons, texture UV offsets, or custom inventory widgets on 2D screens:
   ```lua
   local screenPos = Vector2.new(1920 / 2, 1080 / 2)
   ```

---

## ⏱️ 3. Asynchronous Task Scheduler (`task`)

```lua
-- 1. task.spawn: Execute async callback immediately
task.spawn(function(msg)
    print("Async task executed:", msg)
end, "Startup")

-- 2. task.delay: Delay callback execution by seconds or server ticks
local taskId = task.delay(2.5, function()
    print("2.5 seconds elapsed!")
end)

-- 3. task.defer: Defer callback execution until end of current tick
task.defer(function()
    print("Deferred task executed at end of frame.")
end)

-- 4. task.wait: Return execution delay time
task.wait(1.0)

-- 5. task.cancel: Cancel a running task
task.cancel(taskId)
```

---

## 💾 4. Persistent Storage (`DataStoreService`)

```lua
local dataStoreService = game:GetService("DataStoreService")
local coinsStore = dataStoreService:GetDataStore("PlayerCoins")

-- SetAsync: Save value
coinsStore:SetAsync("KienDev", 5000)

-- GetAsync: Read value
local coins = coinsStore:GetAsync("KienDev")

-- IncrementAsync: Atomically increment or decrement numerical values
local newTotal = coinsStore:IncrementAsync("KienDev", 500) -- -> 5500

-- UpdateAsync: Transform existing value with callback
coinsStore:UpdateAsync("KienDev", function(oldCoins)
    return (oldCoins or 0) + 100
end)

-- RemoveAsync: Delete key
coinsStore:RemoveAsync("KienDev")
```

---

## 🎬 5. Smooth Property Interpolation (`TweenService`)

```lua
local tweenService = game:GetService("TweenService")

-- TweenInfo(durationSeconds, EasingStyle, EasingDirection)
-- EasingStyles: "Linear", "Sine", "Quad", "Cubic", "Bounce", "Elastic"
-- EasingDirections: "In", "Out", "InOut"
local info = TweenInfo.new(3.0, "Sine", "Out")

local bossbar = game:GetService("BossBar"):create("dragon_bar", "Ender Dragon", "RED", "PROGRESS")
local tween = tweenService:Create(bossbar, info, { Percent = 0.0 })

tween:Play()
-- tween:Cancel()
```

---

## ⚡ 6. Event Signal Engine (`Signal` & `RBXScriptConnection`)

```lua
local onBossDefeated = Signal.new()

-- Connect event listener
local connection = onBossDefeated:Connect(function(bossName, rewardXp)
    print("Boss defeated:", bossName, "XP:", rewardXp)
end)

-- Fire signal event
onBossDefeated:Fire("Wither", 5000)

-- Disconnect listener
connection:Disconnect()

-- One-time listener
onBossDefeated:Once(function()
    print("Triggers only once!")
end)

-- Synchronously wait for next event signal
-- local bossName, xp = onBossDefeated:Wait()
```

---

## 🏗️ 7. Hierarchical Object Trees (`Instance.new`)

```lua
local folder = Instance.new("Folder")
folder.Name = "QuestSystem"

local part = Instance.new("Part", folder)
part.Name = "Objective1"

-- Query & Hierarchy traversal
local found = folder:FindFirstChild("Objective1")
print("Found child:", found.Name)

local children = folder:GetChildren()
print("Total children:", #children)

-- Clone & Destroy
local clonedFolder = folder:Clone()
folder:Destroy()
```

---

## 🧮 8. Extended Standard Math Library (`math`)

Built-in standard Lua `math` functions (`math.sin`, `math.cos`, `math.sqrt`, `math.pi`, `math.random`) are supplemented with extended spatial math functions:

```lua
local clamped = math.clamp(150, 0, 100) -- 100
local rounded = math.round(4.6)         -- 5
local signed  = math.sign(-50)          -- -1
local lerped  = math.lerp(10, 20, 0.5)   -- 15
local noise   = math.noise(x, y, z)     -- 3D Perlin Noise (-1.0 to 1.0)
```

---

## 🎭 10. Advanced Client Render Shaders & Screen Shake (`shaders` / `render`)

LuaTweaker provides a high-performance Client Render API that far surpasses KubeJS, allowing full post-processing shader effects, camera shaking on boss encounters, film grain, chromatic aberration, vignette color tints, blur, and custom GLSL post-shaders:

```lua
local shaders = game:GetService("Shaders") -- Or global `shaders` / `render`

-- 1. Camera Shake on Boss Encounter
shaders:shakeCamera(2.0, 3.0) -- Shake with intensity 2.0 for 3 seconds

-- 2. Post-Processing FX
shaders:setFilmGrain(0.4)                           -- Retro film grain noise
shaders:setChromaticAberration(0.08)                -- Lens color fringe
shaders:setVignette(0.7, Color3.fromRGB(255, 0, 0)) -- Red vignette tint
shaders:setColorCorrection(1.1, 1.2, 0.9)           -- Brightness, Contrast, Saturation

-- 3. Render Filters
shaders:setFilter("sepia")        -- "sepia", "monochrome", "invert", "none"

-- 4. Custom GLSL Shader JSON
shaders:loadPostShader("luatweaker:shaders/post/boss_encounter.json")
shaders:setUniform("Intensity", 0.8)

-- 5. Clear FX
shaders:clearAllEffects()
```

---

## 🔌 11. Third-Party Plugin Addon Development (`ILuaTweakerPlugin`)

Third-party Minecraft mods can bind custom services into the service registry:

```java
@LuaTweakerPlugin("mekanism")
public class MekanismLuaAddon implements ILuaTweakerPlugin {

    @Override
    public String getPluginId() {
        return "mekanism_addon";
    }

    @Override
    public void onRegisterGlobals(LuaEngine engine) {}

    @Override
    public void onRegisterServices(LuaServicesLib services) {
        LuaTable mekanismApi = new LuaTable();
        services.registerService("Mekanism", mekanismApi);
    }
}
```

In Lua:
```lua
local mekanism = game:GetService("Mekanism")
```

# Signal-Based Event System (`LuaTweaker.Events`)

LuaTweaker provides a reactive event system modeled after Roblox Studio's Luau engine (`RBXScriptSignal` / `RBXScriptConnection`). All game events pass structured, typed event objects to listeners.

---

## 1. Module Import & Signal API

```lua
local Events  = require("LuaTweaker.Events")
local Signal  = require("LuaTweaker.Signal")
```

### Signal Method Reference

| Method Signature | `camelCase` Alias | Description |
| :--- | :--- | :--- |
| `Signal:Connect(fn)` | `Signal:connect(fn)` | Subscribes a callback listener. Returns a Connection object. |
| `Signal:Once(fn)` | `Signal:once(fn)` | Subscribes a one-time listener (auto-disconnects after first fire). |
| `Signal:Fire(...)` | `Signal:fire(...)` | Fires the signal asynchronously with arbitrary arguments. |
| `Signal:Wait()` | `Signal:wait()` | Yields execution until the signal is next fired, returning arguments. |
| `Connection:Disconnect()` | `Connection:disconnect()` | Unsubscribes the listener from future signal fires. |

### Custom Signal Example

```lua
local onBossDefeated = Signal.new()

local connection = onBossDefeated:Connect(function(bossName, rewardXp)
    print(string.format("Boss defeated: %s (+%d XP)", bossName, rewardXp))
end)

onBossDefeated:Fire("Wither", 5000)
connection:Disconnect()
```

---

## 2. Built-In Game Event Signals (`Events`)

### Entity Damaged Event (`Events.OnEntityDamaged`)

```lua
local Events = require("LuaTweaker.Events")
local World  = require("LuaTweaker.World")

Events.OnEntityDamaged:Connect(function(event)
    local attacker = event.Attacker
    local target   = event.Target

    if attacker:IsPlayer() and target:IsAlive() then
        print(string.format("%s attacked %s", attacker.Name, target.Name))
    end
end)
```

### Entity Spawned Event (`Events.OnEntitySpawned`)

```lua
Events.OnEntitySpawned:Connect(function(event)
    local entity = event.Entity
    if entity:IsPlayer() then
        entity:SendMessage("§aWelcome to the server, " .. entity.Name .. "!")
        entity:SendTitle("§6WELCOME!", "§eLuaTweaker Engine Active")
    end
end)
```

### Block Break Event (`Events.OnBlockBreak`)

```lua
local Vector3 = require("LuaTweaker.Math.Vector3")

Events.OnBlockBreak:Connect(function(event)
    local block = event.Block
    local pos   = block.Position -- Vector3(X, Y, Z)

    if block.Id == "minecraft:diamond_ore" then
        World:SpawnParticle("minecraft:happy_villager", pos + Vector3.new(0.5, 0.5, 0.5))
    end
end)
```

---

## 3. Network Signals (`Network.GetOrCreateRemoteEvent`)

Inter-process server/client network channels:

```lua
local Network = require("LuaTweaker.Network")

local swapSkillEvent = Network.GetOrCreateRemoteEvent("StaffSwapSkill")

swapSkillEvent.OnServerEvent:Connect(function(player, skillIndex)
    if player then
        player:SendMessage("§e[Skill Swapped] Selected skill #" .. tostring(skillIndex))
    end
end)
```

---

## 4. Headless Server Execution Rules (`Dist.DEDICATED_SERVER`)

LuaTweaker automatically detects whether the runtime is executing on a **Dedicated Headless Server** or a **Singleplayer / Client** instance:

* **On Dedicated Headless Servers (`Dist.DEDICATED_SERVER`):**
  - Visual APIs (`Camera:Shake`, `ClientEffects:FlashScreen`, `Shaders`) log diagnostic info and operate as safe no-ops without throwing `ClassNotFound` or render crash errors.
  - Client keybind inputs (`UserInputService:IsKeyDown`) cleanly return `false`.
* **On Client Instances (`Dist.CLIENT`):**
  - Visual shaders, screen flashes, camera shakes, and keybind listeners render directly to OpenGL / Minecraft pose stack.

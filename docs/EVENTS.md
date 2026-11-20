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
| `Signal:Fire(...)` | `Signal:fire(...)` | Queues every listener on the **next tick** with the given arguments. |
| `Signal:FireSync(...)` | `Signal:fireSync(...)` | Runs every listener **inline on the calling thread**. |
| `Signal:Wait()` | `Signal:wait()` | Yields the calling coroutine until the signal is next fired, returning arguments. |
| `Connection:Disconnect()` | `Connection:disconnect()` | Unsubscribes the listener from future signal fires. |

### Threading and ordering

- `Fire` snapshots the listener list, then queues each connected listener through
  `Task.Spawn`. Listeners run on the next tick, on the thread that owns the Lua VM
  (server tick thread when hosting, client tick thread on a remote client). A listener that
  disconnects itself or another listener during the queueing pass cannot skip the next one.
- `FireSync` runs listeners immediately, in order, on the calling thread. Use it only where
  the caller's context is required — HUD and world rendering need the live `GuiGraphics`
  context and the render thread. Each listener is `pcall`-wrapped, so one error does not
  stop the rest.
- `Wait` can only yield inside a task (`Task.Spawn` / `Task.Delay`). Called from the main
  chunk or from `FireSync`, it prints a warning and returns `nil` instead of raising.
- `Once` latches before invoking, so two fires in the same tick cannot run it twice.

`Events` (the Java event bus) is different from `Signal`: `Events:Listen` registers a
listener that the game invokes **synchronously on the thread that fired the event**, not on
the next tick. A reload replaces listeners registered by the previous engine; several
listeners from the same engine may all run.

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

Payloads are sent as a compact binary stream, chunked when larger than 256 KiB, and
serverbound events are rate-limited per player (120 burst / 60 per second). Channel names
starting with `LuaTweaker:` are reserved. See [NETWORK.md](NETWORK.md) for the wire format,
limits, and RemoteFunction budgets.

---

## 4. Pipeline Notification Events (Listen bus)

Datapack materialization and portal use fire string-named events on the shared
bus after the work completes. Subscribe with `Listen` (same bus as
`DimensionEntered`):

```lua
local Events = require("LuaTweaker.Events")

Events:Listen("WorldgenApplied", function(p)
    -- { ores=.., vegetation=.., removals=.., biomeModifiers=.. }
    print("worldgen files: " .. tostring(p.biomeModifiers))
end)

Events:Listen("StructuresApplied", function(p)
    -- { applied=.., skipped=.. } (skipped = template NBT missing)
    print("structures: " .. tostring(p.applied))
end)

Events:Listen("DimensionsMaterialized", function(p)
    -- { dimensions = { "arcane:crystal_realm", ... } }
    print("dimensions: " .. tostring(#p.dimensions))
end)

Events:Listen("PortalUsed", function(p)
    -- { blockId=.., targetDimension=.., playerName=.. }
    print(p.playerName .. " used portal " .. p.blockId)
end)
```

## 6. Client keybinds (`Client.registerKeyBinding`)

Real `KeyMapping`s registered through the standard `RegisterKeyMappingsEvent`,
so binds show in Controls, rebind, and report conflicts like vanilla:

```lua
-- Keyboard, string names (raw GLFW ints still work):
Client.registerKeyBinding("staff_cast", "Cast", "Ruby Mod", "G", "StaffCastSkill")
-- Mouse button + Shift combo (modifier: none/shift/ctrl/alt, full form only):
Client.registerKeyBinding("staff_aoe", "AoE", "Ruby Mod", "MOUSE_RIGHT", "StaffAoe", "shift")

Client.OnKeyBindPressed:Connect(function(id, payload)
    print("pressed " .. id) -- payload "[]" or "" = client-side only
end)
Client.OnKeyBindReleased:Connect(function(id, payload)
    print("released " .. id) -- also fires while a GUI is open (physical truth)
end)
```

Rules: one fire per press (no OS auto-repeat flood); presses are ignored
while any screen is open (same as vanilla); a combo only counts when the
modifier is already held as the main key goes down. Binds registered after
startup (e.g. `/lt reload`) work with their default key but only appear in the
Controls GUI after a restart — a Forge one-shot-event limit, logged loudly.

## 5. Headless Server Execution Rules (`Dist.DEDICATED_SERVER`)

LuaTweaker automatically detects whether the runtime is executing on a **Dedicated Headless Server** or a **Singleplayer / Client** instance:

* **On Dedicated Headless Servers (`Dist.DEDICATED_SERVER`):**
  - Visual APIs (`Camera:Shake`, `ClientEffects:FlashScreen`, `Shaders`) log diagnostic info and operate as safe no-ops without throwing `ClassNotFound` or render crash errors.
  - Client keybind inputs (`UserInputService:IsKeyDown`) cleanly return `false`.
* **On Client Instances (`Dist.CLIENT`):**
  - Visual shaders, screen flashes, camera shakes, and keybind listeners render directly to OpenGL / Minecraft pose stack.

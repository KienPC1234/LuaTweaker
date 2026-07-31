# Signal-Based Event System (`Signal`, `EntityService`, `RemoteEvent`)

> **Stage:** `server/` or `client/` scripts
> **Global Variables:** `Signal` | **Service Lookup:** `Mod:GetService("EntityService")`, `Mod:GetService("NetworkService")`

LuaTweaker uses a Roblox-style reactive event system. All built-in game events are exposed as **Signal** objects that you subscribe to with `Signal:Connect(...)`, and you can create your own signals with `Signal.new()`.

---

## 1. Signal API (`Signal`)

```lua
local onBossDefeated = Signal.new()

-- Subscribe to the signal
local connection = onBossDefeated:Connect(function(bossName, rewardXp)
    print(string.format("Boss defeated: %s (+%d XP)", bossName, rewardXp))
end)

-- Fire the signal
onBossDefeated:Fire("Wither", 5000)

-- Unsubscribe
connection:Disconnect()

-- One-time listener (auto-disconnects after first fire)
onBossDefeated:Once(function()
    print("Triggers only once!")
end)

-- Wait for the next fire (yields the coroutine)
-- local bossName, xp = onBossDefeated:Wait()
```

---

## 2. Built-In Game Signals

### Entity & Player Signals

Subscribe to entity spawns and react to players via the `EntityService`:

```lua
local EntityService = Mod:GetService("EntityService")

EntityService.EntitySpawned:Connect(function(entity)
    -- Roblox-style properties: Name, Type, Health, Position
    print("[Events] Entity spawned: " .. entity.Name .. " (" .. entity.Type .. ")")

    -- Direct HUD actions on players
    if entity.Type == "minecraft:player" then
        entity:SendMessage("§aWelcome to the server, " .. entity.Name .. "!")
        entity:SendTitle("§6WELCOME!", "§eLuaTweaker Engine v1.0 Active")
        entity:SendOverlayMessage("§b+100 Bonus Coins Granted!")
        entity:GiveItem("minecraft:diamond", 3)
    end
end)
```

### Network Signals (`RemoteEvent`)

Roblox-style server/client event channels via `NetworkService`:

```lua
local NetworkService = Mod:GetService("NetworkService")

local actionEvent = NetworkService:GetOrCreateRemoteEvent("PlayerActionEvent")

actionEvent.OnServerEvent:Connect(function(player, actionType, keyName)
    local playerName = player and player.Name or "Unknown Player"
    print("[Network] Received action: " .. tostring(actionType) .. " key=" .. tostring(keyName))

    if player then
        player:SendMessage("§e[Keybind] Server processed action key '" .. tostring(keyName) .. "'")
        player:GiveItem("minecraft:emerald", 1)
    end
end)
```

RemoteFunction server invoke handlers:

```lua
local requestHealth = NetworkService:GetOrCreateRemoteFunction("RequestPlayerHealth")
requestHealth.OnServerInvoke = function(player)
    return 100.0
end
```

### Client Input Signals

```lua
UserInputService.InputBegan:Connect(function(keyCode, isTyping)
    print("[Client] Key pressed: " .. tostring(keyCode))
end)
```

### Attribute Change Signals

Blocks, Items, and Entities expose an `AttributeChanged` signal fired when attributes are set:

```lua
entity:SetAttribute("Phase", "2") -- fires entity.AttributeChanged("Phase", "2")

entity.AttributeChanged:Connect(function(name, value)
    print("[Events] Attribute " .. name .. " set to " .. value)
end)
```

---

## 3. String Event Channels (`events:listen` / `events:post`)

For lightweight pub/sub between scripts and Java addons, the string-based channel API is also available:

```lua
local events = Mod:GetService("Events")

-- Post a custom event payload
events:post("myaddon.custom_event", {
    author = "Kien",
    score = 100,
    timestamp = os.time()
})

-- Listen for custom events
events:listen("myaddon.custom_event", function(event)
    print("Received custom event from author: " .. event.author)
end)
```

### Built-In String Event Payloads

| Event Name | Payload Fields |
| :--- | :--- |
| `player.join` / `player.login` | `username`, `uuid`, `x`, `y`, `z` |
| `player.leave` / `player.logout` | `username`, `uuid` |
| `player.chat` | `sender`, `message`, `rawText` |
| `player.death` / `player.died` | `victim`, `source`, `killer` |
| `block.break` | `player`, `block`, `x`, `y`, `z`, `exp` |
| `block.place` | `block`, `x`, `y`, `z`, `entity` |
| `entity.spawn` | `entityId`, `x`, `y`, `z` |
| `server.tick` | `tick` |

> For most game events, prefer the Signal-based API (Section 2) — it passes live wrapped objects instead of flat payload tables.

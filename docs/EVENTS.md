# ⚡ Event Hooks & Custom Channels (`events` & `Signal`)

> **Stage:** `server_scripts/` or `client_scripts/`  
> **Global Variables:** `events`, `Signal` | **Service Lookup:** `local events = game:GetService("Events")`

The `events` API and `Signal` engine provide real-time event listeners for game events, reactive signal handles (`Signal:Connect`, `Signal:Wait`), and custom event channels for third-party mod addons.

> 💡 **Service Registry Paradigm**: Access event bus via `events` or `game:GetService("Events")` / `Mod:GetService("Events")`. Create reactive event signals via `Signal.new()`.

---

## 🎧 1. Listening to Game Events (`events:listen`)

```lua
-- Listen to player chat
events:listen("player.chat", function(event)
    print(string.format("Player %s sent chat: %s", event.sender, event.message))
end)

-- Listen to player death
events:listen("player.death", function(event)
    print(string.format("Player %s was killed by %s!", event.victim, event.killer or "environment"))
end)

-- Listen to item crafting
events:listen("player.craft_item", function(event)
    print(string.format("Player %s crafted %d x %s", event.username, event.count, event.item))
end)

-- Listen to block break
events:listen("block.break", function(event)
    print(string.format("Player %s broke block %s at (%d, %d, %d)", event.player, event.block, event.x, event.y, event.z))
end)
```

---

## 📡 2. Custom Event Channels (`events:post`)

Post custom event payloads across all registered Lua scripts and Java addon plugins:

```lua
-- Post custom event payload
events:post("myaddon.custom_event", {
    author = "Kien",
    score = 100,
    timestamp = os.time()
})

-- Listen for custom event
events:listen("myaddon.custom_event", function(event)
    print("Received custom event from author: " .. event.author)
end)
```

---

## 📋 Comprehensive Built-In Event Channels Matrix

### 👤 Player Events
| Event Name | Description | Event Payload Fields |
| :--- | :--- | :--- |
| `player.join` / `player.login` | Player joins server | `username`, `uuid`, `x`, `y`, `z` |
| `player.leave` / `player.logout` | Player leaves server | `username`, `uuid` |
| `player.respawn` | Player respawns | `username`, `isEndConquered` |
| `player.chat` | Player sends chat | `sender`, `message`, `rawText` |
| `player.craft_item` / `player.itemCrafted` | Player crafts item | `username`, `item`, `count` |
| `player.smelt_item` / `player.itemSmelted` | Player smelts item | `username`, `item`, `count` |
| `player.pickup_item` / `player.itemPickup` | Player picks up item | `username`, `item`, `count` |
| `player.right_click_block` | Player right-clicks block | `username`, `x`, `y`, `z`, `hand` |
| `player.right_click_item` | Player right-clicks item | `username`, `item`, `hand` |
| `player.left_click_block` | Player left-clicks block | `username`, `x`, `y`, `z` |
| `player.advancement` | Player earns advancement | `username`, `advancementId` |
| `player.hurt` / `player.damage` | Player receives damage | `victim`, `amount`, `source` |
| `player.death` / `player.died` | Player dies | `victim`, `source`, `killer` |

### 🐲 Entity Events
| Event Name | Description | Event Payload Fields |
| :--- | :--- | :--- |
| `entity.spawn` | Entity spawns in world | `entityId`, `x`, `y`, `z` |
| `entity.hurt` | Mob / Entity takes damage | `victim`, `amount`, `source` |
| `entity.death` | Mob / Entity dies | `victim`, `source`, `killer` |
| `entity.tame` | Player tames animal | `animal`, `owner` |

### 🧊 Block Events
| Event Name | Description | Event Payload Fields |
| :--- | :--- | :--- |
| `block.break` | Player breaks block | `player`, `block`, `x`, `y`, `z`, `exp` |
| `block.place` | Player/Entity places block | `block`, `x`, `y`, `z`, `entity` |

### 🌐 World & Server Events
| Event Name | Description | Event Payload Fields |
| :--- | :--- | :--- |
| `server.start` / `server.starting` | Dedicated server boots | `status` |
| `server.stop` / `server.stopping` | Dedicated server stops | `status` |
| `server.tick` | Every server tick (20 TPS) | `tick` |
| `world.load` | World level loads | `level` |
| `world.save` | World level saves | `level` |

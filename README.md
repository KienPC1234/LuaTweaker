<div align="center">

# LuaTweaker

**Lua Scripting Engine & Modding Framework for Minecraft 1.21.1 (NeoForge)**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![PAL](https://img.shields.io/badge/Loader-Agnostic%20(PAL)-orange.svg?style=for-the-badge)](docs/ARCHITECTURE_AND_SECURITY.md)
[![Java](https://img.shields.io/badge/Java-21-blue.svg?style=for-the-badge&logo=openjdk)](https://adoptium.net)

</div>

---

## Overview

**LuaTweaker** is a scripting engine for **Minecraft 1.21.1** that lets you write Lua scripts directly in your game directory and see results on the next reload — no Java compilation, no game restart.

The engine is **loader-agnostic**. All platform access goes through the **PAL (Platform Abstraction Layer)**, so the core engine and feature modules never touch a mod loader API directly. The reference implementation targets **NeoForge**; new loaders can be added by implementing the PAL interfaces without touching core code.

The runtime is **Lua 5.2 / Cobalt** with a Roblox (Luau)-flavored API: services, signals, task scheduling, and fluent content builders.

---

## Key Features

- **Autonomous Lua Mods (`luamods/`)**
  - One folder per mod with `manifest.json` + `main.lua`, or a `.zip` package.
  - `default_config.json` → copied to `luaconfig/<mod_id>.json` (tunables live in config, not code).
  - Auto-mounted virtual resource packs & data packs (`assets/`, `data/`).

- **Dynamic Content Registration (startup)**
  - Custom items, blocks, fluids, armor sets, creative tabs, entity types and keybindings via fluent builders: `Content.NewItem("id"):MaxStackSize(64):Register()`.
  - Custom entity types (`Content.createEntity(...)`) with vanilla-mob adapters, attributes, renderers and boss bars.

- **Recipe Management (server)**
  - Shaped, shapeless, smelting, blasting, smoking, campfire, smithing, stonecutting, brewing, anvil and villager trades — add, remove, replace.
  - Chainable builders: `Recipe.Shaped("id"):Pattern(...):Key(...):Output(...):Register()`.

- **Roblox-style Runtime APIs**
  - Services loaded explicitly: `require("LuaTweaker.Players")`, `require("LuaTweaker.Events")`, `require("LuaTweaker.Network")`, `require("LuaTweaker.GuiService")`, ...
  - Signals (`Signal.new()`, `:Connect`, `:Once`, `:Wait`), task scheduling (`Task:spawn`, `Task:delay`, `Task:wait`), remote events (`Network:GetOrCreateRemoteEvent(...)`).
  - **Unified entity API**: one entity/player table with BOTH method style (`entity:setHealth(50)`) and Roblox property style (`entity.Health = 50`).

- **Client HUD & Effects**
  - `GuiService` with `OnRenderHUD`, `DrawRect`, `DrawText`, `DrawProgressBar`, `DrawOutline`, `DrawTexture`, `GetScreenSize`.
  - Keybinding registration with Lua-side `Client.OnKeyBindPressed` dispatch.

- **Automatic IDE Autocomplete**
  - Reflection-based `@LuaDoc` parser generates EmmyLua stubs (`run/.luatweaker/stubs/luatweaker-api.lua`) with class inheritance (`---@class Player: Entity`) for VS Code and IntelliJ.

- **Server-authoritative by design**
  - Item/block interactions dispatch Lua events server-side only; clients receive effects via network packets.
  - Shared event bus routes dispatches from the startup engine to the current runtime engine.

---

## Architecture

Strict **Separation of Concerns** across decoupled modules:

```text
LuaTweaker/
├── common-api/         # Pure Java 21: PAL interfaces, VM interfaces, @LuaDoc / @LuaDefault annotations
├── core-engine/        # Cobalt VM wrapper, LuaBinder (auto-binding), async logger, linter, stub generator
├── modules/
│   ├── module-content/     # Content builders (items, blocks, fluids, entities, tabs, keybinds)
│   ├── module-recipes/     # Recipe manipulation
│   ├── module-events/      # Shared event bus
│   ├── module-entities/    # Unified entity/player wrappers + AI goals
│   ├── module-interaction/ # World/block interaction + projectile firing
│   ├── module-network/     # Remote events / remote functions
│   ├── module-client/      # GuiService, ClientEffects, keybinds, RunService
│   ├── module-storage/     # Roblox-style data stores (world/player/session)
│   ├── module-tasks/       # Task scheduler bridge
│   ├── module-math/        # Vector3 / Vector2 / Color3 / math extensions
│   └── module-interception/# Anvil / brewing / villager trade interception
└── neoforge-platform/  # Reference PAL implementation (NeoForge launcher, registrars, render)
```

The `core-engine` and `modules/*` depend only on `common-api`. New loaders = a new platform module implementing PAL; no core changes.

---

## Game Directory Layout

On launch, LuaTweaker generates its workspace in your game directory:

```text
minecraft-instance/
├── luamods/                  # Autonomous Lua mods (folders or .zip)
│   └── my_mod/
│       ├── manifest.json     # id, name, author, version, main, permissions
│       ├── default_config.json  # copied to luaconfig/my_mod.json on first run
│       ├── main.lua          # single entrypoint
│       └── src/
│           ├── startup/      # content registration (items, blocks, entities...)
│           ├── server/       # runtime logic (recipes, events, AI, skills)
│           └── client/       # HUD, keybind feedback, client effects
├── luaconfig/                # per-mod config JSON (editable, merged with defaults)
├── .luatweaker/stubs/        # auto-generated EmmyLua stubs
└── logs/luatweaker/          # engine log + per-mod logs
```

> The dev workspace lives in `neoforge-platform/luamods/` and is synced to `run/luamods/` by the `syncLuaMods` Gradle task.

---

## Quick Start (Development)

```sh
# full build (CI)
./gradlew build

# dev build + asset sync (fast iteration)
./gradlew :neoforge-platform:classes :neoforge-platform:syncLua

# run client or server
./gradlew :neoforge-platform:runClient
./gradlew :neoforge-platform:runServer

# tests (no Minecraft required — engine tests use mocks)
./gradlew test
```

Requirements: **Java 21 (Temurin)**, Gradle via the wrapper, NeoForge 21.1.242+.

---

## Lua Code Examples

All scripts follow the Roblox (Luau) module style. Load services explicitly with `require`.

### 1. Register Content (startup)

```lua
-- luamods/ruby_mod/src/startup/ruby_content.lua
local Content = require("LuaTweaker.Content")

local CustomRuby = Content.NewItem("custom_ruby")
    :MaxStackSize(64)
    :Rarity("EPIC")
    :DisplayName("Enchanted Ruby Gem")
    :CreativeTab("ruby_tab")
    :Register()

-- Custom boss entity (zombie adapter)
Content.createEntity("ruby_boss", function(entity)
    entity:parentMob("zombie")
    entity:maxHealth(300)
    entity:dimensions(0.9, 2.5)
end)

-- Custom projectile definition (damage / explosion power actually applied)
Content.registerProjectile("luatweaker:ruby_orb", { damage = 25, explosionPower = 2, trailParticle = "minecraft:flame" })
```

### 2. Recipes (server)

```lua
local Recipe = require("LuaTweaker.Recipe")
Recipe.Shaped("ruby_block_craft")
    :Pattern({ "RRR", "RRR", "RRR" })
    :Key("R", ingredient("luatweaker:custom_ruby"))
    :Output(item("luatweaker:ruby_block", 1))
    :Register()
```

### 3. Events, Tasks & Entity API (server)

```lua
local Events = require("LuaTweaker.Events")
local Task = require("LuaTweaker.Task")

Events:Listen("MagicStaffUsed", function(payload)
    local player = payload.player
    -- unified entity API: method style AND property style
    player:shootProjectile("luatweaker:ruby_orb", 1.8)
    player.Health = player.Health - 20
end)

Task:delay(2.0, function()
    print("Delayed task fired!")
end)
```

### 4. Client HUD (client)

```lua
local GuiService = require("LuaTweaker.GuiService")
GuiService.OnRenderHUD:Connect(function(dt)
    local size = GuiService:GetScreenSize()
    GuiService:DrawTextCentered("Mana: 100/100", math.floor(size.Width / 2), 20, 0xFFFFFFFF, true)
end)
```

---

## In-Game Commands

| Command | Description |
| :--- | :--- |
| `/lt reload` | Hot-reload all Lua scripts, regenerate stubs, re-apply recipes. |
| `/lt doctor` | Health diagnostics: loaded mods, per-mod load errors, engine + service status. |
| `/lt hand` | Inspect the item/block currently held in hand. |
| `/lt syntax <file>` | Syntax-check a Lua file. |
| `/lt list` | List loaded Lua mods / files. |
| `/lt debug [on|off]` | Toggle debug logging. |

---

## Documentation Index

See [`docs/`](docs/README.md):

- [GETTING_STARTED.md](docs/GETTING_STARTED.md) — Environment & IDE setup
- [ARCHITECTURE_AND_SECURITY.md](docs/ARCHITECTURE_AND_SECURITY.md) — PAL, sandboxing, module boundaries
- [SCRIPTING_GUIDE.md](docs/SCRIPTING_GUIDE.md) — Lua API conventions
- [RECIPES.md](docs/RECIPES.md) — Recipe manipulation
- [CUSTOM_CONTENT.md](docs/CUSTOM_CONTENT.md) — Items, blocks, entities, tabs
- [EVENTS.md](docs/EVENTS.md) — Built-in events
- [GUI_GRAPHICS.md](docs/GUI_GRAPHICS.md) — HUD rendering
- [MOB_AND_SPAWN.md](docs/MOB_AND_SPAWN.md) — Entity API & AI goals
- [LUA_MOD_SYSTEM.md](docs/LUA_MOD_SYSTEM.md) — Packaging autonomous Lua mods
- [TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) — Debugging & known issues

---

## License

This project is licensed under the **[Apache License 2.0](LICENSE.txt)**.

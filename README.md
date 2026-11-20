<div align="center">

<img src="logo.png" alt="LuaTweaker Logo" />

**Lua Scripting Engine & Modding Framework for Minecraft 1.21.1 (NeoForge)**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![PAL](https://img.shields.io/badge/Loader-Agnostic%20(PAL)-orange.svg?style=for-the-badge)](docs/ARCHITECTURE_AND_SECURITY.md)
[![Java](https://img.shields.io/badge/Java-21-blue.svg?style=for-the-badge&logo=openjdk)](https://adoptium.net)

</div>

---

## What it is

**LuaTweaker** is a Lua 5.4 (luava) scripting engine for **Minecraft 1.21.1**.
You write Lua in your game folder, run `/lt reload`, and see results —
no Java compilation, no game restart.

Design rules:

- **Low-level primitives, not high-level wrappers.** Java is a thin bridge
  (move, look, jump, raytrace, spawn); game logic lives in Lua.
  See `AGENTS.md` §1.
- **Loader-agnostic.** `common-api` + `core-engine` + `modules/*` never touch
  `net.minecraft.*` / `net.neoforged.*`. Only `neoforge-platform` does (PAL).
- **One mod = one folder.** `luamods/<id>/` with `manifest.json` + `main.lua`
  + `src/{startup,server,client}` + `default_config.json`.

---

## Quick start

```sh
./gradlew :neoforge-platform:runClient   # or :neoforge-platform:runServer
./gradlew :neoforge-platform:classes :neoforge-platform:syncLua  # fast Lua-only iteration
./gradlew test                            # all tests (no Minecraft needed, mocks only)
/lt reload   # in-game: reload scripts, regenerate stubs, re-apply recipes
/lt doctor   # in-game: mods + load errors + engine/service status
```

Requirements: **Java 21 (Temurin)**, Gradle wrapper, NeoForge 21.1.242.
Full setup: [`docs/GETTING_STARTED.md`](docs/GETTING_STARTED.md).

Game-folder layout (generated on launch):

```text
luamods/<id>/           manifest.json, main.lua, src/startup|server|client,
                        default_config.json, assets/, data/
luaconfig/<id>.json     per-mod config (editable, merged over defaults)
.luatweaker/stubs/      auto-generated EmmyLua stubs (VS Code / IntelliJ)
logs/luatweaker/        engine log + per-mod logs
```

---

## Lua in 60 seconds

```lua
-- src/startup/content.lua
local Content = require("LuaTweaker.Content")
Content.NewItem("ruby_gem"):DisplayName("Ruby Gem"):MaxStackSize(16):Register()

Content.createEntity("crystal_wisp", function(e)
    e:category("MONSTER"):rig("quadruped")
     :texture("luatweaker:textures/entity/crystal_beast.png")
     :dimensions(0.9, 1.4):maxHealth(40):movementSpeed(0.3)
     :tickRate(5)
     :onTick(function(ent) -- server-side AI goes here end)
end)
```

```lua
-- src/server/main.lua
local Events = require("LuaTweaker.Events")
local Brain  = require("LuaTweaker.Brain")

local tree = Brain.Selector("guard", {
    Brain.Sequence("survive", {
        Brain.Condition("dying", function(ctx)
            return Brain.HealthFraction(ctx.ent) < 0.2
        end),
        Brain.Action("flee", function(ctx) -- ... moveTo away ... end),
    }),
    Brain.Action("patrol", function(ctx) -- ... waypoint walk ... end),
})
```

Conventions: `local` everything, explicit `require("LuaTweaker.X")`,
colon methods (`obj:method()`), PascalCase services, `UPPER_SNAKE` constants.
Details: [`docs/SCRIPTING_GUIDE.md`](docs/SCRIPTING_GUIDE.md).

---

## What you can build

| Area | Entry point | Doc |
| :--- | :--- | :--- |
| Items / blocks / fluids / entities | `Content.NewItem / NewBlock / NewFluid / createEntity` | [CUSTOM_CONTENT](docs/CUSTOM_CONTENT.md) |
| Recipes (shaped, shapeless, furnace, smithing, brewing, anvil, trades) | `Recipe.Shaped(...)` | [RECIPES](docs/RECIPES.md) |
| Events + signals | `Events:Listen("ServerTick", ...)`, `Signal.new()` | [EVENTS](docs/EVENTS.md) |
| NPC brains (Selector/Sequence/Parallel/Utility/Latch + blackboards + packs) | `require("LuaTweaker.Brain")` | [BEHAVIOR_TREES](docs/BEHAVIOR_TREES.md) |
| Entity AI goals + sensing | `AIGoals`, `ent:canSee/canSee` | [MOB_AND_SPAWN](docs/MOB_AND_SPAWN.md) |
| Ore / vegetation / biome mods, custom biomes, structures | `Worldgen`, `Biomes`, `Structures` | [WORLDGEN](docs/WORLDGEN.md) |
| Loot tables | `Loot:AddMobDrop(...)` | [MOB_AND_SPAWN](docs/MOB_AND_SPAWN.md) |
| Commands (`/lt ...` + own `/<cmd>`) | `Commands:Register(...)` | [COMMANDS](docs/COMMANDS.md) |
| Client HUD / keybinds / sounds / shaders | `GuiService`, `KeyBindService`, `ClientEffects`, `Shaders` | [CLIENT_AND_SOUND_API](docs/CLIENT_AND_SOUND_API.md), [SHADER_API](docs/SHADER_API.md) |
| Storage / tasks / math | `Storage`, `Task:delay/spawn/wait`, `Vector3` | [SERVICE_AND_SPATIAL_MATH_API](docs/SERVICE_AND_SPATIAL_MATH_API.md), [SCRIPTING_GUIDE](docs/SCRIPTING_GUIDE.md) |
| Network (RemoteEvent/Function) | `NetworkService` | [NETWORK](docs/NETWORK.md) |
| Update checks + gated HTTP | `update_url` in manifest, `Net:HttpGet` + `net.http` | [COMMANDS](docs/COMMANDS.md), [LUA_MOD_SYSTEM](docs/LUA_MOD_SYSTEM.md) |
| Java plugins (third-party mods) | `ILuaTweakerPlugin` | [ADDON_DEVELOPER_GUIDE](docs/ADDON_DEVELOPER_GUIDE.md) |

---

## In-game commands

| Command | Description |
| :--- | :--- |
| `/lt reload [data\|resources\|all]` | Hot-reload scripts + stubs + recipes. |
| `/lt doctor` | Mods, per-mod load errors, engine/service status. |
| `/lt hand` | Inspect held item / targeted block. |
| `/lt syntax <file>` | Syntax-check a Lua file without running it. |
| `/lt list` | List loaded Lua mods / scripts. |
| `/lt debug [on\|off]` | Toggle debug logging. |
| `/lt help` | Core commands + every Lua-registered `/<cmd>`. |

Details: [`docs/COMMANDS.md`](docs/COMMANDS.md).

---

## Project structure

```text
common-api/          Pure Java 21: PAL, VM interfaces, @LuaDoc / @LuaDefault
core-engine/         Luava VM wrapper, LuaBinder, DynamicJavaProxy, logger, stubs
modules/module-*     One domain each (content, recipes, events, entities, brain.lua,
                     interaction, network, client, storage, tasks, math, ... )
neoforge-platform/   ONLY runnable module: PAL impl, registrars, commands, luamods/
```

`core-engine` and `modules/*` depend only on `common-api`.
New loader = new platform module implementing PAL; no core changes.

---

## Documentation

Start here: [`docs/README.md`](docs/README.md) (full index) ·
[`docs/GETTING_STARTED.md`](docs/GETTING_STARTED.md) ·
[`docs/SCRIPTING_GUIDE.md`](docs/SCRIPTING_GUIDE.md) ·
[`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md)

> Some docs under `docs/` describe older API surfaces and are marked
> `stale` at the top. The `docs/README.md` index labels them —
> do not implement from stale files.

---

## License

[Apache License 2.0](LICENSE.txt).

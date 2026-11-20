# Getting Started

Target: **Minecraft 1.21.1 (NeoForge)** · **Java 21 (Temurin)** · **Lua 5.4 (luava 0.2.5)**.

## 1. Run it

```sh
./gradlew :neoforge-platform:runClient   # or :neoforge-platform:runServer
./gradlew :neoforge-platform:classes :neoforge-platform:syncLua  # fast Lua-only iteration
./gradlew test                            # all tests (mocks only, no Minecraft needed)
./gradlew build                           # full CI build
```

On launch the game folder gains:

```text
luamods/<id>/           manifest.json, main.lua, src/startup|server|client,
                        default_config.json, assets/, data/
luaconfig/<id>.json     per-mod config (editable, merged over defaults)
.luatweaker/stubs/      auto-generated EmmyLua stubs for your editor
logs/luatweaker/        engine log + per-mod logs
```

Dev workspace `neoforge-platform/luamods/` syncs to `run/luamods/` via `syncLuaMods`.
The shipped example mod is `luatweaker_test` (content + recipes + brain demos).

## 2. Your first mod (5 minutes)

`luamods/hello/manifest.json`:

```json
{ "id": "hello", "name": "Hello", "version": "1.0.0", "main": "main.lua" }
```

`luamods/hello/main.lua` (single entrypoint, requires the phase files):

```lua
require(".src.startup.content")
require(".src.server.logic")
```

`luamods/hello/src/startup/content.lua`:

```lua
local Content = require("LuaTweaker.Content")
Content.NewItem("hello_gem"):DisplayName("Hello Gem"):MaxStackSize(16):Register()
```

`luamods/hello/src/server/logic.lua`:

```lua
local Events = require("LuaTweaker.Events")
Events:Listen("ServerTick", function()
    -- per-tick server logic; throttle scans with tickRate / CooldownReady
end)
```

In-game: `/lt reload`, then `/lt doctor` to confirm `hello` loaded with no errors.

## 3. IDE autocomplete

Stubs regenerate on every launch/reload into `.luatweaker/stubs/`
(`luatweaker-api.lua`, with `---@class Player: Entity` inheritance).
Point your Lua language server at that folder:

```json
{ "Lua.workspace.library": [ "<game-dir>/.luatweaker/stubs" ] }
```

## 4. Daily commands

| Command | Use |
| :--- | :--- |
| `/lt reload` | After editing any Lua: reloads scripts, regenerates stubs, re-applies recipes. |
| `/lt doctor` | Something broke? Mods + per-mod load errors + services, all in one dump. |
| `/lt hand` | Inspect held item / targeted block (ID, NBT, tags). |
| `/lt syntax <file>` | Check a file without running it. |
| `/lt list` | List loaded mods / scripts. |
| `/lt debug [on\|off]` | Verbose logging while debugging. |

Full reference: [COMMANDS](COMMANDS.md). When stuck: [TROUBLESHOOTING](TROUBLESHOOTING.md).
Next: [SCRIPTING_GUIDE](SCRIPTING_GUIDE.md) for conventions, then a system doc
from the [index](README.md).

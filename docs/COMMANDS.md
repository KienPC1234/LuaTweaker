# In-Game Commands Guide (`/lt` & `/luatweaker`)

LuaTweaker provides health diagnostics and hot-reloading for modpack creators, plus top-level commands that Lua mods register themselves via `Commands:Register` (see `ICommandService`).

---

## Command Reference Table

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/lt reload [data\|resources\|all]` | op (2) | Hot-reloads LuaMods, regenerates EmmyLua stubs, syncs newly declared Lua commands to online players, and triggers server datapack reload. |
| `/lt hand` | op (2), players only | Inspects main-hand item (ID, count, NBT, tags). Empty hand inspects the targeted block (ID, state, tags) with clickable Lua snippets. |
| `/lt syntax [path\|<mod_id>]` | op (2) | Syntax-checks Lua files without executing (defaults to `luamods/`). Paths are contained inside `luamods/`. |
| `/lt list [server\|client\|startup\|<mod_id>]` | all (0) | Lists discovered LuaMods (default) or `.lua` scripts inside a phase/mod folder under `luamods/`. |
| `/lt debug [on\|off]` | owner (4) | Toggles debug mode (no arg prints state). |
| `/lt doctor` | all (0) | Health diagnostics: engine, loaded mods, per-mod load errors, update checks, services, Lua tasks, Lua-registered commands. |
| `/lt dimension <list\|info <id>\|tp <id> [playerName]\|bench <id> [radius]>` | op (2) | Lists Lua-registered dimensions, prints config, teleports the executing player or a named online player (console must supply `playerName`), and measures the Lua terrain pipeline (`bench`, radius 0-8). |
| `/lt tp <dimensionId> [playerName]` | op (2) | Shortcut for `/lt dimension tp`. Console must supply an online player name. |
| `/lt spawn <list <id>\|clear [id]\|test [id]\|spawn <entity> [count]>` | op (2) | Inspects a dimension's declarative spawn rules, evaluates them at your position, clears them, or force-spawns a validated entity at your feet. `test`/`spawn` are players only. |
| `/lt gui [jei] [title] [width] [height]` | op (2), players only | Opens the in-game visual GUI Editor (or JEI category editor with `jei`). |
| `/lt help` | all (0) | Lists core commands plus every live Lua-registered command (`/<name>`, mod, op level). It lists all op levels; tab completion only suggests commands the sender's permission allows. |
| `/<mod-command> [args]` | per definition | Top-level commands from `Commands:Register("name", {...})`; slash-separated names nest (`shop/buy`). Hidden from tab-complete and rejected at parse time without the required op level. |

---

## Lua-Mod Commands (`Commands:Register`)

```lua
local Commands = require("LuaTweaker.Commands") -- service name is "Commands"

Commands:Register("greet", {
    Description = "Say hello",     -- shown in /lt help
    PermissionLevel = 0,          -- 0 all, 2 op (default), 4 owner; must be an integer
    ConsoleAllowed = true,         -- false = players only (default true)
    Usage = "/greet <name>",       -- sent to the sender when the handler fails
    Aliases = { "hi" },            -- extra paths to the same command
    Suggestions = { "steve" },     -- or function(sender, args) -> table
    Handler = function(sender, args, raw)
        sender:SendMessage("Hi " .. (args[1] or "stranger"))
        return true                -- false = Brigadier failure (+ Usage hint)
    end,
})
```

Rules: same mod re-registering replaces (reload-safe); another mod's name/path/alias is rejected loudly. Reserved names (`lt`, `luatweaker`, `reload`, `hand`, `syntax`, `list`, `debug`, `doctor`, `help`, `gui`, `dimension`, `tp`, `spawn`) are rejected at registration; a name already owned by vanilla or another mod is accepted by `Commands:Register` but rejected later when the Brigadier tree is built. `Commands:Unregister("name")` removes the definition and its aliases (aliases resolve to the primary, so unregistering by alias works too); the Brigadier node itself persists until the next datapack reload and reports "not registered" meanwhile.

**Permissions:** core commands use op level 2 by default (`list`, `doctor` and `help` are level 0; `debug` is level 4). In a singleplayer world with cheats disabled the player has level 0, so only `help`, `list` and `doctor` are suggested/completable; the other `/lt` subcommands are hidden by Brigadier's `requires()` gate. Enable cheats or promote the player to use them.

---

## Doctor Diagnostics Output

`/lt doctor` prints engine state, loaded LuaMods with per-mod load errors (including `net.http` permission holders), declared/available update checks, service count, Lua task queue stats, and every Lua-registered command with mod, op level, console flag and aliases.

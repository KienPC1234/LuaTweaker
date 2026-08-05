# 🏬 Autonomous Self-Contained Lua Mod System (`luamods/`)

> **Location:** Root Game Directory → `luamods/`  
> **Format:** Uncompressed Folders **and** `.zip` Archives

Think of a modern high-tech industrial park: every company (Mod) occupies its own building with its own security system (Memory Sandbox), visitors can only enter through a single lobby (`main.lua`), and companies do business with their neighbors over standard internal phone lines (Export / Import API) — never by climbing over each other's walls.

LuaTweaker introduces a **Self-Contained Lua Mod Architecture** — a standard as rigorous as Fabric/Forge, but written in Lua. Each mod is an independent unit with isolated memory, its own resources, and full control over its execution flow through **one single entrypoint** (`main.lua`). There are no shared global script folders like KubeJS (`startup_scripts/`, `server_scripts/`, `client_scripts/`).

---

## 📂 1. Directory Structure

### Folder Mod (Development Mode)

```
luamods/
├── mod_sieu_nhan/               # Mod ID = folder name
│   ├── manifest.json            # Mod identity card
│   ├── default_config.json      # Default config (auto-copied to config/luatweaker/)
│   ├── main.lua                 # THE SINGLE ENTRYPOINT
│   ├── src/                     # Code modules (loaded ONLY via require)
│   │   ├── abilities.lua
│   │   └── boss_ai.lua
│   ├── assets/                  # Virtual ResourcePack (auto-mounted!)
│   │   └── textures/gui/icon.png
│   └── data/                    # Virtual Datapack (auto-mounted!)
│       └── loot_tables/zombie.json
│
└── mod_economy.zip              # ZIP Package (ready to distribute)
```

### ZIP Package (Distribution Mode)

```
mod_economy.zip
├── manifest.json
├── default_config.json
├── main.lua
├── src/
│   └── shop.lua
├── assets/
│   └── textures/items/coin.png
└── data/
    └── recipes/coin_recipe.json
```

> **💡 Core Principle:** LuaTweaker **NEVER** auto-scans or auto-executes files inside `src/` or any subfolder. `main.lua` holds 100% control and actively loads its modules with `require("src.abilities")`.

> **💡 Tip:** LuaTweaker reads ZIP archives directly in-memory. No unzipping to disk required!

---

## 📝 2. `manifest.json` Format

Every Lua Mod **must** contain a `manifest.json` at its root:

```json
{
    "id": "mod_sieu_nhan",
    "name": "Superhero Capabilities Mod",
    "author": "KienDev",
    "version": "2.0.0",
    "main": "main.lua",
    "description": "Adds superhero abilities, custom items, and boss encounters.",
    "icon": "textures/gui/icon.png",
    "dependencies": ["mod_economy"],
    "minLuaTweakerVersion": "1.0.0",
    "permissions": ["runtime.reflection", "runtime.bytecode_hook"]
}
```

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `id` | ✅ | folder/zip name | Unique mod identifier |
| `name` | ✅ | — | Display name |
| `author` | ❌ | `"Unknown"` | Author name |
| `version` | ❌ | `"1.0.0"` | Semantic version |
| `main` | ❌ | `"main.lua"` | Single entry script |
| `description` | ❌ | `""` | Human-readable description |
| `icon` | ❌ | `""` | Icon path inside `assets/` (e.g. `textures/gui/icon.png`) |
| `dependencies` | ❌ | `[]` | Required LuaMod IDs (validated at load) |
| `minLuaTweakerVersion` | ❌ | `""` | Minimum LuaTweaker version required |
| `permissions` | ❌ | `[]` | Runtime permissions granted to the mod (e.g. `runtime.reflection`, `runtime.bytecode_hook`, `net.http`). Denied at load time if undeclared. See [**JAVA_PATCHER.md**](JAVA_PATCHER.md) |
| `update_url` | ❌ | — | **HTTPS** URL of a JSON update feed (see [Update Checking](#-update-checking--network-security)) |

---

## 🛡️ Update Checking & Network Security

The engine treats outbound network access like a smart mailbox: the postman may
only drop the right postcard into the slot, never walk into the living room.

### Layer 1 — Declarative Update Checks (default, 0 lines of Lua)

```json
{
  "id": "gt_addon",
  "version": "1.0.2",
  "environment": "universal",
  "update_url": "https://raw.githubusercontent.com/.../update.json"
}
```

The engine downloads the feed **on the Java side** (never inside the Lua
sandbox) at mod load time, compares versions and shows a chat notice when a
player joins. Only `https://` feeds are accepted — plain HTTP is rejected.

Expected feed JSON:

```json
{
  "version": "1.1.0",
  "name": "1.1.0 - New features",
  "download_url": "https://example.com/gt_addon-1.1.0.jar",
  "changelog": "- Fixed everything"
}
```

Only `version` is required. Comparison is segment-based numeric (so `1.10`
beats `1.9`), with lexicographic fallback for non-numeric segments.

### Layer 2 — Safe Read-Only Status API (for custom UIs)

The engine caches the feed result in RAM; Lua only reads it:

```lua
local updateInfo = mod:GetUpdateStatus("gt_addon")   -- or Update:GetStatus("gt_addon")
if updateInfo and updateInfo.HasUpdate then
    print("New version: " .. updateInfo.LatestVersion)
end
-- Fields: HasUpdate, LatestVersion, CurrentVersion, UpdateUrl, DownloadUrl,
--         Changelog, Name, Checking, Error
local all = Update:GetUpdates()  -- table of all available updates keyed by mod id
```

### Layer 3 — Permission-Gated HTTP GET (locked by default)

Free HTTP is **denied by default**. The permission is **per installation**:
the Lua VM cannot attribute a runtime call to the mod that made it (the
`mod` global is only trustworthy while an entrypoint runs and is spoofable
from Lua), so the grant is granted by the Java-side registry whenever **any**
loaded mod declares it:

```json
{
  "permissions": ["net.http"]
}
```

- `Net:HttpGet(url, timeoutSeconds)` returns `{Success, StatusCode, Body, Json, Error}`.
  `Json` is the parsed body when it is valid JSON.
- The call is synchronous on the calling thread; keep `timeoutSeconds` low
  (default 5, clamped to 1–60) and never call it from hot paths.
- With no granting mod loaded, every call is rejected loudly — regardless of
  what a script writes into the `mod` global.
- Granting mods are listed in a loud warning on first HTTP use and in chat at
  player login, so malicious or careless mods cannot reach the internet
  silently.

---

## ⚙️ 3. Unified Config System

The old split between an internal `config.json` and an external `luaconfig/` system is gone. **All** user-facing settings for every mod live in **one central place**:

```
[ UNIFIED CONFIG SYSTEM FLOW ]
   │
   ├──> 1. FIRST BOOT
   │      └──> If `config/luatweaker/<mod_id>.json` does not exist, auto-copy it from
   │           `default_config.json` inside the mod.
   │
   └──> 2. RUNTIME
          └──> Every read/write performed by the mod operates directly on
               `config/luatweaker/<mod_id>.json`.
```

### Usage in Lua (`main.lua` or any module)

```lua
-- 1. Read config (auto-loaded from config/luatweaker/mod_sieu_nhan.json)
local Config = mod:GetConfig()
print("Boss shake intensity:", Config.boss_shake_intensity)

-- 2. Modify and save back down to disk
Config.debug_mode = true
mod:SaveConfig(Config)
```

### Example `default_config.json`

```json
{
    "boss_shake_intensity": 2.0,
    "boss_shake_duration": 3.0,
    "film_grain": 0.3,
    "vignette_intensity": 0.5,
    "vignette_color": "#FF1E3C",
    "welcome_message": "§6§l✨ Welcome! ✨",
    "custom_drops_enabled": true,
    "zombie_diamond_chance": 0.05,
    "debug_mode": false
}
```

### Supported Value Types

| Type | JSON Example | Lua Type |
|------|-------------|----------|
| String | `"hello"` | `string` |
| Number (integer) | `42` | `number` |
| Number (float) | `0.05` | `number` |
| Boolean | `true` / `false` | `boolean` |

---

## 🔒 4. Sandbox & Inter-Mod IPC

Each mod owns an **independent `LuaEngine` sandbox**. Globals defined by Mod A never affect Mod B — no more "global variable collision" like KubeJS, where every script shares one JavaScript runtime:

```lua
-- In mod_sieu_nhan/main.lua:
POWER_LEVEL = 9001  -- Only visible inside mod_sieu_nhan!

-- In mod_economy/main.lua:
POWER_LEVEL = 100   -- A completely different variable! No conflict!
```

To share data or functions between mods **safely**, LuaTweaker provides the **Export / Import API**:

```
[ INTER-MOD IPC ]
   │
   ├──> Mod A (mod_economy) --(Export API)--> [ GLOBAL MOD BUS ] --(Import API)--> Mod B (mod_sieu_nhan)
```

### Publishing an API — Mod A (`mod_economy/main.lua`)

```lua
local EconomyAPI = {}

function EconomyAPI.AddCoins(player, amount)
    -- Logic to add coins
    print("Added " .. amount .. " coins to " .. player.Name)
end

-- Publish a public API for other mods to use
mod:ExportAPI("Economy", EconomyAPI)
```

### Consuming an API — Mod B (`mod_sieu_nhan/main.lua`)

```lua
-- Load the API from mod_economy (dependencies are auto-validated by the system)
local Economy = mod:ImportAPI("mod_economy", "Economy")

if Economy then
    Economy.AddCoins(player, 500)
end
```

---

## ⚡ 5. The 5-Step Execution Pipeline

When Minecraft boots, LuaTweaker runs this pipeline **sequentially** for every mod in `luamods/`:

```
┌─────────────────────────────────────────────────────────────────┐
│  Step 1: DISCOVERY (Discovery & Rollcall)                       │
│  • Scan luamods/ for folders AND .zip files                     │
│  • Read manifest.json, validate dependency list                 │
├─────────────────────────────────────────────────────────────────┤
│  Step 2: MEMORY ISOLATION (Sandbox Creation)                    │
│  • Create an independent LuaEngine per mod                      │
│  • Mod A's globals ≠ Mod B's globals                            │
├─────────────────────────────────────────────────────────────────┤
│  Step 3: ASSET & DATA MOUNTING (Asset Mounting)                 │
│  • Mount assets/ → Virtual ResourcePack                         │
│  • Mount data/   → Virtual Datapack                             │
│  • Works for both folders (disk) and ZIPs (in-memory)           │
├─────────────────────────────────────────────────────────────────┤
│  Step 4: CONFIG INITIALIZATION (Config Sync)                    │
│  • Check for config/luatweaker/<mod_id>.json                    │
│  • Auto-copy from default_config.json if missing                │
├─────────────────────────────────────────────────────────────────┤
│  Step 5: ACTIVATION (Single Entrypoint Execution)               │
│  • Run ONLY main.lua                                            │
│  • main.lua coordinates Content, Events, and AI loading in code │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🚀 6. `main.lua` Reference Structure

A standard `main.lua` — clear, self-contained, with a clean split between static registration and dynamic runtime logic:

```lua
-- ==========================================
-- MOD: Superhero Capabilities (mod_sieu_nhan)
-- ENTRYPOINT: main.lua
-- ==========================================

-- 1. LOAD SUB-MODULES EXPLICITLY WITH REQUIRE (never auto-run)
local Content   = require("LuaTweaker.Content")
local Events    = require("LuaTweaker.Events")
local Abilities = require("src.abilities")  -- Your own module in src/
local BossAI    = require("src.boss_ai")

-- 2. STARTUP: STATIC CONTENT REGISTRATION
local CapedChestplate = Content.NewItem("mod_sieu_nhan:hero_cape")
    :DisplayName("§cHero Cape")
    :MaxStackSize(1)
    :Register()

-- 3. RUNTIME: DYNAMIC LOGIC & EVENTS
Events.OnEntitySpawned:Connect(function(event)
    local entity = event.Entity

    if entity.Id == "minecraft:wither" then
        -- Attach custom AI from the BossAI module
        BossAI.AttachSuperArmor(entity)
    end
end)

-- 4. LIFECYCLE
function mod.OnEnable()
    print("§a[mod_sieu_nhan] Mod loaded successfully! Version: " .. mod.Version)
end

function mod.OnDisable()
    print("§c[mod_sieu_nhan] Cleaning up memory...")
end
```

### `require()` search paths

```lua
require("src.abilities")      → mod/src/abilities.lua
require("lib.utils")          → mod/lib/utils.lua
require("LuaTweaker.Content") → Core engine API (Content, Events, Recipe, ...)
```

### Full `mod` table API

```lua
-- ========== METADATA ==========
mod.Id            -- "mod_sieu_nhan"
mod.Name          -- "Superhero Capabilities Mod"
mod.Author        -- "KienDev"
mod.Version       -- "2.0.0"
mod.Description   -- "Adds superhero abilities, custom items, and boss encounters."
mod.Icon          -- "textures/gui/icon.png"
mod.Main          -- "main.lua"
mod.Type          -- "folder" or "zip"
mod.Dependencies  -- {"mod_economy", ...}

-- ========== UNIFIED CONFIG (config/luatweaker/<id>.json) ==========
local Config = mod:GetConfig()          -- Auto-loaded; falls back to default_config.json
Config.debug_mode = true
mod:SaveConfig(Config)                  -- Write back to disk

-- ========== INTER-MOD IPC ==========
mod:ExportAPI("Economy", EconomyAPI)    -- Publish a public API
local api = mod:ImportAPI("mod_economy", "Economy")  -- Import a neighbor's API

-- ========== STATUS ==========
mod:IsEnabled()     -- true/false

-- ========== RELOAD ==========
mod:Reload()        -- Re-run main.lua (folder mods only)

-- ========== FILE OPERATIONS (folder mods only) ==========
mod:ListFiles()                    -- {"main.lua", "src/recipes.lua", ...}
mod:ListFiles(".lua")              -- Filter by extension
mod:ReadFile("config.json")        -- Read file content as string
mod:WriteFile("data.txt", "hello") -- Write content to file
mod:Exists("manifest.json")        -- Check if file exists
```

---

## 📦 7. ZIP Package Support

### How ZIP Loading Works

1. LuaTweaker scans `luamods/` for `.zip` files.
2. Opens the ZIP in-memory using `java.util.zip.ZipFile` (no disk extraction).
3. Searches for `manifest.json` at root, or nested one level (e.g. `mod_name/manifest.json`).
4. Reads all `assets/**` entries → Virtual ResourcePack.
5. Reads all `data/**` entries → Virtual Datapack.
6. Executes **only `main.lua`** in the isolated sandbox (all other code is loaded via `require`).

### Safety Checks (Error Prevention)

- ❌ **No `manifest.json`?** → ZIP is skipped with a warning log.
- ❌ **Malformed JSON?** → Graceful fallback: mod ID derived from filename, version defaults to `1.0.0`.
- ❌ **Missing dependencies / permissions?** → Load aborted with a clear error. Other mods continue loading normally.
- ❌ **Corrupt ZIP?** → Caught by `try-catch`, logged, and skipped. Other mods continue loading normally.
- ❌ **Lua script error?** → Logged with filename and line number. The mod is disabled gracefully; other mods are unaffected.
- ✅ **Duplicate mod IDs?** → Later mod overwrites earlier one (last-in wins), with a warning.
- ✅ **Disabled mods?** → Skipped gracefully with informative log.

### Creating a ZIP Package

```bash
cd luamods/mod_sieu_nhan/
zip -r ../mod_sieu_nhan.zip .
```

---

## 🖥️ 8. Enable / Disable & Management GUI

On the Title Screen, click the **LuaMods** button next to Mods to open the management screen:

- 📋 **Scrollable mod list** with mod icons, names, versions, authors, and descriptions
- 🟢 **Enable/Disable status** visible per mod (disabled mods shown with overlay)
- ℹ️ **Sidebar details** when a mod is selected: full description, metadata
- 📂 **Open Folder button** to quickly access the `luamods/` directory
- 🔄 **Changes apply after restart** (like standard Minecraft mods)

### Commands

```
/lt luamod list                    — List all LuaMods with status
/lt luamod info <id>               — Show detailed mod info
/lt luamod enable <id>             — Enable a mod (restart to apply)
/lt luamod disable <id>            — Disable a mod (restart to apply)
/lt luamod reload <id>             — Hot-reload a folder mod (re-run main.lua)
/lt luamod config reload <id>      — Reload config/luatweaker/<id>.json from disk
/lt luamod config path <id>        — Show path to config/luatweaker/<id>.json
/lt reload                         — Full hot-reload: scripts + all LuaMods
/lt reload <modId>                 — Hot-reload a specific folder LuaMod
```

Enabled/disabled state is stored in `config/luatweaker_mods.json`.

---

## 🚀 9. Quick Start Tutorial

### Step 1: Create Mod Folder

```
luamods/
└── my_first_mod/
    ├── manifest.json
    └── main.lua
```

### Step 2: Write `manifest.json`

```json
{
    "id": "my_first_mod",
    "name": "My First Lua Mod",
    "author": "YourName",
    "version": "1.0.0"
}
```

### Step 3: Write `main.lua`

```lua
print("[my_first_mod] Hello from my first autonomous Lua Mod!")

local EntityService = Mod:GetService("EntityService")

EntityService.EntitySpawned:Connect(function(entity)
    if entity.Type == "minecraft:player" then
        print("[my_first_mod] Welcome, " .. entity.Name .. "!")
        entity:SendMessage("§aWelcome to the server, " .. entity.Name .. "!")
    end
end)
```

### Step 4: Boot Minecraft

The mod loads automatically with a log message:

```
[LuaModManager] 📦 Discovered Folder Lua Mod 'My First Lua Mod' (my_first_mod) v1.0.0 by YourName
[LuaModManager] ⚡ Executed single entry 'main.lua' for mod 'my_first_mod'
```

---

## 🆚 10. Comparison with KubeJS

| Feature | LuaTweaker `luamods/` | KubeJS |
|---------|----------------------|--------|
| Mod Isolation | ✅ Separate sandbox per mod | ❌ All scripts share 1 runtime |
| Single Entrypoint | ✅ Only `main.lua` runs; modules via `require` | ❌ Auto-runs all script folders |
| Unified Config | ✅ `default_config.json` + `config/luatweaker/<id>.json` | ❌ No unified config |
| Inter-Mod IPC | ✅ `mod:ExportAPI()` / `mod:ImportAPI()` | ❌ No safe IPC model |
| ZIP Packaging | ✅ Drop `.zip` into folder | ❌ Manual file copy only |
| Auto Asset Mounting | ✅ `assets/` auto-loaded | ❌ Separate resource pack |
| Auto Datapack Mounting | ✅ `data/` auto-loaded | ❌ Separate datapack |
| Manifest Metadata | ✅ `manifest.json` with full fields | ❌ No metadata system |
| Permission Gate | ✅ `permissions` in manifest | ❌ No permission model |
| Distribution | ✅ Single `.zip` file | ❌ Copy multiple folders |
| Error Containment | ✅ One mod crashes, others OK | ❌ One error breaks all |
| GUI Management | ✅ In-game LuaMods screen | ❌ No management UI |
| Enable/Disable | ✅ Per-mod toggle with UI | ❌ No toggle system |
| Dependency Checks | ✅ `dependencies` validated | ❌ No dependency system |
| Hot Reload | ✅ Per-mod + full reload | ❌ Only file watch |
| File API | ✅ readFile/writeFile/listFiles | ❌ No file access |
| Command Line | ✅ `/lt luamod` subcommands | ❌ Limited CLI |

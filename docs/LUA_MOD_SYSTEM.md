# 🏬 Autonomous Self-Contained Lua Mod System (`luamods/`)

> **Location:** Root Game Directory → `luamods/`  
> **Format:** Uncompressed Folders **and** `.zip` Archives  
Unlike KubeJS, which forces scripts into shared global folders (`startup_scripts/`, `server_scripts/`, `client_scripts/`), LuaTweaker introduces a **Self-Contained Lua Mod Architecture** where each mod lives in its own isolated apartment — with its own scripts, assets, datapacks, and memory sandbox.

---

## 📂 1. Directory Structure

### Folder Mod (Development Mode)
```
luamods/
├── mod_sieu_nhan/               # Mod ID = folder name
│   ├── manifest.json            # Mod Identity Card
│   ├── config.json              # ⚙️ Per-Mod Configuration (tunable settings!)
│   ├── main.lua                 # Primary Entrypoint
│   ├── src/                     # Additional Lua Modules
│   │   ├── abilities.lua
│   │   └── boss_ai.lua
│   ├── assets/                  # Virtual ResourcePack (auto-mounted!)
│   │   └── textures/
│   │       └── gui/banner.png
│   └── data/                    # Virtual Datapack (auto-mounted!)
│       └── loot_tables/
│           └── entities/zombie.json
│
└── mod_economy.zip              # ZIP Package (1-second installation!)
```

### ZIP Package (Distribution Mode)
```
mod_economy.zip
├── manifest.json
├── main.lua
├── src/
│   └── shop.lua
├── assets/
│   └── textures/items/coin.png
└── data/
    └── recipes/coin_recipe.json
```

> **💡 Tip:** LuaTweaker reads ZIP archives directly in-memory. No unzipping to disk required!

---

## 🏗️ Standard Mod Architecture

```
my_mod/
├── manifest.json          # Mod metadata
├── config.json            # Default config → auto-copied to luaconfig/
├── main.lua               # Entry + lifecycle hooks (onEnable/onDisable)
├── startup/               # ★ Stage 1: Items, blocks, fluids
├── src/                   # ★ Stage 2: Shared modules (legacy compat)
├── server/                # ★ Stage 3: Recipes, events, storage
├── client/                # ★ Stage 4: Shaders, GUI (client-only)
├── lib/                   # Libraries (require("lib.utils"))
├── assets/                # Virtual ResourcePack
└── data/                  # Virtual Datapack
```

### Lifecycle in main.lua

```lua
function onEnable()
    print("Ready!")
end
function onDisable()
    print("Cleaning up...")
end
```

**Load order**: `main.lua` → `startup/` → `src/` → `server/` → `client/` → `onEnable()`

### require() search paths

```lua
require("lib.utils")  → mod/lib/utils.lua
require("src.foo")    → mod/src/foo.lua
```



## 📝 2. `manifest.json` Format

Every Lua Mod must contain a `manifest.json` at its root:

```json
{
    "id": "mod_sieu_nhan",
    "name": "Superhero Capabilities Mod",
    "author": "KienDev",
    "version": "2.0.0",
    "main": "main.lua",
    "description": "Adds superhero abilities, custom items, and boss encounters.",
    "icon": "textures/gui/mod_icon.png",
    "dependencies": ["mod_example"],
    "minLuaTweakerVersion": "1.0.0"
}
```

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| `id` | ✅ | folder/zip name | Unique mod identifier |
| `name` | ✅ | — | Display name |
| `author` | ❌ | `"Unknown"` | Author name |
| `version` | ❌ | `"1.0.0"` | Semantic version |
| `main` | ❌ | `"main.lua"` | Primary entry script |
| `description` | ❌ | `""` | Human-readable description |
| `icon` | ❌ | `""` | Icon path inside assets/ (e.g. `textures/gui/mod_icon.png`) |
| `dependencies` | ❌ | `[]` | Required LuaMod IDs (validated at load) |
| `minLuaTweakerVersion` | ❌ | `""` | Minimum LuaTweaker version required |

---

## 📁 3. Global LuaConfig System (`luaconfig/`)

> **Location:** Root Game Directory → `luaconfig/<mod_id>.json`

A separate config system for modpack makers to configure LuaMods **without touching** the mod's internal files.

```
game_root/
├── luaconfig/
│   ├── mod_example.json       # Global config for mod_example
│   ├── mod_sieu_nhan.json
│   └── my_first_mod/
│       ├── settings.json      # Multi-file config per mod
│       └── data.json
├── luamods/                   # Actual Lua Mods
└── ...
```

### Reading in Lua
```lua
local cfg = mod:getLuaConfig()     -- Reads luaconfig/<mod_id>.json
print(cfg.some_setting)

cfg.new_setting = "hello"
mod:saveLuaConfig(cfg)            -- Writes back to luaconfig/<mod_id>.json

local dir = mod:getLuaConfigDir() -- Returns luaconfig/<mod_id>/ folder path
local path = mod:getLuaConfigFile() -- Returns luaconfig/<mod_id>.json path
```

### Commands
```
/lt luamod config reload <id>   — Reload luaconfig from disk
/lt luamod config path <id>     — Show path to luaconfig file
```

### Config File
Each mod's luaconfig is stored at `luaconfig/<mod_id>.json`. If the file doesn't exist, `mod:getLuaConfig()` returns an empty table.

---

## ⚙️ 4. Enable / Disable LuaMods

### In-Game GUI
On the Title Screen, click the **LuaMods** button next to Mods to open the management screen:
- View all loaded LuaMods with icons, names, versions, authors
- See descriptions and enable/disable status
- Open the `luamods/` folder directly
- Changes take effect after game restart

### Commands
```
/lt luamod list                    — List all LuaMods with status
/lt luamod info <id>               — Show detailed mod info
/lt luamod enable <id>             — Enable a mod (restart to apply)
/lt luamod disable <id>            — Disable a mod (restart to apply)
/lt luamod reload <id>             — Hot-reload a folder mod (re-run all scripts)
/lt luamod config reload <id>      — Reload luaconfig from disk
/lt luamod config path <id>        — Show path to luaconfig file
/lt reload                         — Full hot-reload: scripts + all LuaMods
/lt reload <modId>                 — Hot-reload a specific folder LuaMod
```

### Status File
Enabled/disabled state is stored in `config/luatweaker_mods.json`.

---

## ⚙️ 4. Per-Mod Configuration (`config.json`)

Each mod can have its own `config.json` file at its root. Modpack users can tweak settings **without editing Lua code**.

### Example `config.json`
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

### Full Lua API for `mod` table

```lua
-- ========== METADATA ==========
mod.id              -- "mod_example"
mod.name            -- "LuaTweaker Example Mod"
mod.author          -- "KienDev"
mod.version         -- "1.0.0"
mod.description     -- "Complete example..."
mod.icon            -- "textures/gui/mod_icon.png"
mod.main            -- "main.lua"
mod.type            -- "folder" or "zip"
mod.path            -- Absolute path to mod directory
mod.dependencies    -- {"mod_example", ...}

-- ========== INTERNAL CONFIG (inside mod folder) ==========
local cfg = mod:loadConfig()    -- Read mod's own config.json
cfg.debug_mode = true
mod:saveConfig(cfg)             -- Write back to config.json

-- ========== GLOBAL LUA CONFIG (luaconfig/<id>.json) ==========
local lcfg = mod:getLuaConfig()       -- Read from luaconfig/<id>.json
lcfg.my_setting = 42
mod:saveLuaConfig(lcfg)               -- Write to luaconfig/<id>.json
mod:getLuaConfigDir()                 -- "luaconfig/mod_example/"
mod:getLuaConfigFile()                -- "luaconfig/mod_example.json"

-- ========== STATUS ==========
mod:isEnabled()     -- true/false (based on config/luatweaker_mods.json)

-- ========== RELOAD ==========
mod:reload()        -- Re-run main.lua and scripts (folder mods only)

-- ========== FILE OPERATIONS (folder mods only) ==========
mod:listFiles()              -- {"main.lua", "src/recipes.lua", ...}
mod:listFiles(".lua")        -- Filter by extension
mod:readFile("config.json")  -- Read file content as string
mod:writeFile("data.txt", "hello")  -- Write content to file
mod:exists("manifest.json")  -- Check if file exists
mod:getModDir()              -- Get absolute mod directory path
```

### Saving Config at Runtime
```lua
local config = mod:loadConfig()
config.debug_mode = true
mod:saveConfig(config)
```

### Supported Value Types
| Type | JSON Example | Lua Type |
|------|-------------|----------|
| String | `"hello"` | `string` |
| Number (integer) | `42` | `number` |
| Number (float) | `0.05` | `number` |
| Boolean | `true` / `false` | `boolean` |

---

## ⚡ 5. The 4-Step Java Execution Pipeline

When Minecraft boots, LuaTweaker runs this pipeline for **every** mod in `luamods/`:

```
┌─────────────────────────────────────────────────────────────────┐
│  Step 1: ROLLCALL (Discovery)                                   │
│  Scan luamods/ for directories AND .zip files                   │
│  Read manifest.json from each                                   │
│  Check enabled/disabled status                                  │
├─────────────────────────────────────────────────────────────────┤
│  Step 2: MEMORY ISOLATION (Sandbox)                             │
│  Create isolated LuaEngine per mod                              │
│  Mod A's globals ≠ Mod B's globals (no conflicts!)              │
├─────────────────────────────────────────────────────────────────┤
│  Step 3: ASSET & DATA MOUNTING                                  │
│  Mount assets/ → Virtual ResourcePack                           │
│  Mount data/   → Virtual Datapack                               │
│  Works for both folders (disk) and ZIPs (in-memory)             │
├─────────────────────────────────────────────────────────────────┤
│  Step 4: ACTIVATION (Script Execution)                          │
│  Execute main.lua first                                         │
│  Then execute all .lua files in src/                             │
├─────────────────────────────────────────────────────────────────┤
│  Step 5: LUA CONFIG LOADING                                      │
│  Read luaconfig/<mod_id>.json from game root                    │
│  Expose via mod:getLuaConfig() API                              │
├─────────────────────────────────────────────────────────────────┤
│  Step 6: DEPENDENCY RESOLUTION                                  │
│  Log warnings for missing dependencies                          │
│  Check minLuaTweakerVersion                                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📦 6. ZIP Package Support (Detail)

### How ZIP Loading Works
1. LuaTweaker scans `luamods/` for `.zip` files.
2. Opens the ZIP in-memory using `java.util.zip.ZipFile` (no disk extraction).
3. Searches for `manifest.json` at root, or nested one level (e.g. `mod_name/manifest.json`).
4. Reads all `assets/**` entries → Virtual ResourcePack.
5. Reads all `data/**` entries → Virtual Datapack.
6. Evaluates all `.lua` entries as scripts in the isolated sandbox.

### Safety Checks (Error Prevention)
- ❌ **No `manifest.json`?** → ZIP is skipped with a warning log.
- ❌ **Malformed JSON?** → Graceful fallback: mod ID derived from filename, version defaults to `1.0.0`.
- ❌ **Corrupt ZIP?** → Caught by `try-catch`, logged, and skipped. Other mods continue loading normally.
- ❌ **Lua script error?** → Logged with filename and line number. Other scripts in the mod continue executing.
- ✅ **Duplicate mod IDs?** → Later mod overwrites earlier one (last-in wins), with a warning.
- ✅ **Disabled mods?** → Skipped gracefully with informative log.

### Creating a ZIP Package
```bash
cd luamods/mod_sieu_nhan/
zip -r ../mod_sieu_nhan.zip .
```

---

## 🔒 7. Memory Isolation (Sandbox)

Each Lua Mod gets its **own** `LuaEngine` instance with isolated global variables:

```lua
-- In mod_sieu_nhan/main.lua:
POWER_LEVEL = 9001  -- Only visible inside mod_sieu_nhan!

-- In mod_economy/main.lua:
POWER_LEVEL = 100   -- Different variable! No conflict!
```

This prevents the "global variable collision" problem that plagues KubeJS, where all scripts share one JavaScript runtime.

---

## 🖥️ 8. LuaMod Management GUI

The **LuaMod List Screen** (accessible from the Title Screen's **LuaMods** button) provides:

- 📋 **Scrollable mod list** with mod icons, names, versions, authors, and descriptions
- 🟢 **Enable/Disable status** visible per mod (disabled mods shown with overlay)
- ℹ️ **Sidebar details** when a mod is selected: full description, metadata
- 📂 **Open Folder button** to quickly access the `luamods/` directory
- 🔄 **Changes apply after restart** (like standard Minecraft mods)

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
[LuaModManager] ⚡ Executed main entry 'main.lua' for mod 'my_first_mod'
```

---

## 🆚 10. Comparison with KubeJS

| Feature | LuaTweaker `luamods/` | KubeJS |
|---------|----------------------|--------|
| Mod Isolation | ✅ Separate sandbox per mod | ❌ All scripts share 1 runtime |
| Per-Mod Config | ✅ `config.json` + `mod:loadConfig()` | ❌ No per-script config |
| ZIP Packaging | ✅ Drop `.zip` into folder | ❌ Manual file copy only |
| Auto Asset Mounting | ✅ `assets/` auto-loaded | ❌ Separate resource pack |
| Auto Datapack Mounting | ✅ `data/` auto-loaded | ❌ Separate datapack |
| Manifest Metadata | ✅ `manifest.json` with full fields | ❌ No metadata system |
| Distribution | ✅ Single `.zip` file | ❌ Copy multiple folders |
| Error Containment | ✅ One mod crashes, others OK | ❌ One error breaks all |
| GUI Management | ✅ In-game LuaMods screen | ❌ No management UI |
| Enable/Disable | ✅ Per-mod toggle with UI | ❌ No toggle system |
| Global LuaConfig | ✅ `luaconfig/<id>.json` system | ❌ No global config |
| Dependency Checks | ✅ `dependencies` validated | ❌ No dependency system |
| Hot Reload | ✅ Per-mod + full reload | ❌ Only file watch |
| File API | ✅ readFile/writeFile/listFiles | ❌ No file access |
| Command Line | ✅ `/lt luamod` subcommands | ❌ Limited CLI |

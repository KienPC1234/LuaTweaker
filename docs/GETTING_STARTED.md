# 🚀 Getting Started with LuaTweaker (`kien.LuaTweaker`)

> **Target Version:** Minecraft 1.21.1 (NeoForge) | **Java Version:** 21 | **Lua Version:** 5.1 / 5.2 (LuaJ 3.0.1)

Welcome to **LuaTweaker**, an ultra-lightweight, high-performance Lua scripting engine for Minecraft modpack creators and developers.

---

## 🏗️ 1. Root `lua/` Directory Layout

Matching CraftTweaker `scripts/` and KubeJS `kubejs/` conventions, all LuaTweaker folders reside directly in the game root folder **`lua/`** (`.minecraft/lua/` or `run/lua/`):

```
lua/
├── startup_scripts/          # ⚡ Mod Bootstrap Stage (create items, blocks, fluids)
├── server_scripts/           # ⚔️ Server Load Stage (recipes, tags, loot tables, events, commands)
├── client_scripts/           # 🎨 Client Load Stage (tooltips, client events, UI rendering)
├── assets/                   # 🖼️ Auto-Mounted ResourcePack (textures, models, blockstates, lang)
├── data/                     # 📦 Auto-Mounted Datapack (custom JSON tags, loot tables, advancements)
├── dumps/                    # 📄 Registry Dumps (/lt dump mod <modid>)
├── logs/                     # 📝 Execution log file (luatweaker.log)
└── stubs/                    # 💡 Auto-Generated LLS Type Definition Stubs (luatweaker.d.lua)
```

---

## ⚙️ 2. Configuration File (`config/luatweaker.json`)

Modpack configuration is managed via `config/luatweaker.json`:

```json
{
  "rootScriptDir": "lua",
  "customModId": "luatweaker",
  "maxInstructionLimit": 500000,
  "enableHotReload": true,
  "autoGenerateStubs": true
}
```

- **`rootScriptDir`**: Directory name at game root (default `"lua"`).
- **`customModId`**: Mod ID prefix for created items/blocks (default `"luatweaker"`).
- **`maxInstructionLimit`**: Instruction watchdog limit for infinite loops (default `500000`).

---

## 🎨 3. Placing Textures & Models (`lua/assets/`)

Files placed inside `lua/assets/` are **automatically mounted into Minecraft's Resource Pack repository** at startup:

- **Custom Item Textures:** `lua/assets/luatweaker/textures/item/custom_ruby.png`
- **Custom Block Textures:** `lua/assets/luatweaker/textures/block/custom_ruby_block.png`
- **Custom Block Models:** `lua/assets/luatweaker/models/block/custom_ruby_block.json`
- **Custom Language Files:** `lua/assets/luatweaker/lang/en_us.json`

---

## 💡 4. IDE Autocompletion Setup (VS Code / IntelliJ IDEA)

LuaTweaker automatically generates type definition stubs at startup:
`lua/stubs/luatweaker.d.lua`

### VS Code Setup
In your workspace `.vscode/settings.json`, add:
```json
{
  "Lua.workspace.library": [
    "lua/stubs"
  ]
}
```

### IntelliJ IDEA Setup
Run the Gradle task to generate native IDEA run configurations:
```bash
./gradlew genIntellijRuns
```
Select **`runClient`** in IntelliJ IDEA and press **Shift + F9** to debug Minecraft directly!

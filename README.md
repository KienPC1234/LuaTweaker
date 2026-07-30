<div align="center">

<img src="logo.png" alt="LuaTweaker Logo" width="500"/>

# 🌙 LuaTweaker Engine

**Ultra-Lightweight, High-Performance Lua Scripting Engine & Modding Framework for Minecraft 1.21.1 (NeoForge)**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.242-orange.svg?style=for-the-badge)](https://neoforged.net)
[![Java](https://img.shields.io/badge/Java-21-blue.svg?style=for-the-badge&logo=openjdk)](https://adoptium.net)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](LICENSE.txt)

---

</div>

## 📌 Overview

**LuaTweaker** is an ultra-lightweight, high-performance scripting engine for **Minecraft 1.21.1 (NeoForge)** designed specifically for **Modpack Creators** and **Modders**.

Instead of dealing with heavy frameworks or verbose languages, LuaTweaker delivers a seamless developer experience using **Lua 5.2 / Cobalt**. It features per-addon memory sandboxing, automatic LSP / EmmyLua autocomplete stub generation, and deep Minecraft bytecode interception—all without needing to compile Java code or restart your client.

---

## 🔥 Key Features

- 🔨 **Comprehensive Recipe Management**:
  - Add, modify, or remove **Shaped 3x3**, **Shapeless**, **Smelting**, **Blasting**, **Smoking**, **Campfire**, **Smithing**, **Stonecutting**, **Brewing**, **Anvil**, and **Villager Trades**.
  - Perform global ingredient replacements across all recipe types.

- 🎨 **Dynamic Custom Content Creation**:
  - Register custom Items, Blocks, Fluids, Toolsets, and Armor sets dynamically at runtime (`startup:createItem()`, `startup:createBlock()`).
  - Auto-mount textures and datapack resources seamlessly without writing Java mods.

- 🏬 **Autonomous Lua Mod System (`luamods/`)**:
  - Modular project architecture with `manifest.json`.
  - **Direct ZIP Package loading** for 1-second Lua mod installation and community distribution.
  - Automatic isolation and mounting of Virtual ResourcePacks & Datapacks.

- ⚡ **Event Hooks & Task Scheduler**:
  - Subscribe to a wide array of built-in game events (`player.join`, `block.break`, `entity.spawn`, `server.tick`).
  - Support for event cancellation and custom channel broadcasting (`events:post`).
  - Built-in task scheduler for delayed and repeating tasks (`scheduler`, `task.wait/delay`).

- 🎨 **Graphics, VFX/SFX & Shaders**:
  - Built-in post-processing screen shader system (Film Grain, Screen Shake, CRT Scanlines, Chromatic Aberration, Vignette).
  - Advanced 2D GUI rendering via `GuiGraphics` (PoseStack matrix operations, gradient fills, 2D textures, item icons, and tooltips).

- 🛠️ **Dynamic Java Patching & Mixins**:
  - Flexible bytecode interception with `patcher:hookMethod` and `@Inject` Mixin hooks (`mixin`).
  - Low-level Java class loading (`Java.loadClass`) and interface proxies (`java:proxy`).

- 🤖 **Service-Oriented Reactive Architecture (Roblox-like APIs)**:
  - Object-oriented Service Registry (`game:GetService("TweenService")`, `DataStoreService`, `Signal`).
  - Spatial math vectors (`Vector3`, `Vector2`), color engine (`Color3`), and persistent key-value storage (`DataStore`).

- 💡 **Automatic IDE Autocomplete (LSP / EmmyLua)**:
  - Reflection-based `@LuaDoc` parser automatically generates type-safe stubs (`luatweaker-api.lua`) for **VS Code** and **IntelliJ IDEA**.

---

## 🏗️ LTVM Engine Architecture

LuaTweaker enforces strict **Separation of Concerns** across 4 decoupled modules:

```text
Luatweaker-root/
├── common-api/         # Pure Java 21: PAL Registry, Abstract Objects, VM Interfaces, LuaDoc Annotations
├── core-engine/        # LTVM Cobalt Engine Wrapper, Async Logger, Linter, EmmyLua Generator
├── modules/
│   └── module-recipes/ # Feature Module: Engine-agnostic Recipe manipulation logic
└── neoforge-platform/  # NeoForge Platform Launcher, concrete PAL implementations & Reload Listeners
```

---

## 📂 Lua Folder Layout

Upon launching Minecraft with LuaTweaker installed, the root `lua/` directory is automatically generated in your game folder:

```text
minecraft-instance/
├── lua/
│   ├── startup/        # Executed during Mod Loading phase (Item, Block & Fluid Registration)
│   ├── server/         # Executed on Server/World initialization (Recipes, Events, WorldGen, Commands)
│   ├── client/         # Executed on Client side (GUI Rendering, Keybinds, Shaders)
│   ├── luamods/        # Autonomous Lua Mods directory (Folders or .ZIP packages)
│   ├── assets/         # Virtual ResourcePack assets (Textures, Models, Lang)
│   ├── data/           # Virtual Datapack resources
│   └── logs/           # Engine output log (luatweaker.log)
```

---

## 🚀 Quick Start

### 1. Installation
1. Ensure you are running **Minecraft 1.21.1** with **NeoForge 21.1.242** or higher.
2. Drop the **LuaTweaker** `.jar` file into your `mods/` directory.
3. Launch Minecraft once to generate the `lua/` workspace and autocomplete stubs.

### 2. Setting Up Autocomplete
- **VS Code**: Install the `Lua` extension (by Sumneko / Lua Language Server).
- **IntelliJ IDEA**: Install the `EmmyLua` plugin.
- Open the root `lua/` folder in your IDE. Autocomplete stubs at `lua/.luatweaker/stubs/luatweaker-api.lua` will be loaded automatically.

---

## 📝 Code Examples

### 1. Recipe Modification
```lua
-- lua/server/recipes.lua
local recipes = Mod:GetService("Recipes")

-- Remove vanilla recipe
recipes:removeByOutput("minecraft:diamond_sword")

-- Add a new 3x3 Shaped recipe
recipes:addShaped("minecraft:diamond_sword", 1, {
    { "",  "minecraft:diamond", "" },
    { "",  "minecraft:diamond", "" },
    { "",  "minecraft:stick",   "" }
})

-- Add a Smelting recipe
recipes:addSmelting("minecraft:iron_ingot", "minecraft:raw_iron", 0.7, 200)
```

### 2. Custom Item & Block Registration (Startup Phase)
```lua
-- lua/startup/content.lua
local startup = Mod:GetService("Startup")

-- Create a custom item
startup:createItem("ruby", {
    displayName = "Ruby",
    maxStackSize = 64,
    rarity = "EPIC"
})

-- Create a custom block
startup:createBlock("ruby_block", {
    displayName = "Block of Ruby",
    hardness = 3.0,
    resistance = 6.0,
    requiresTool = true
})
```

### 3. Event Hooks & Task Scheduling
```lua
-- lua/server/events.lua
local events = Mod:GetService("Events")
local scheduler = Mod:GetService("Scheduler")

-- Listen to player join event
events:listen("player.join", function(event)
    local player = event.player
    player:sendMessage("Welcome " .. player:getName() .. " to the server!")
    
    -- Schedule a delayed task (after 3 seconds / 60 ticks)
    scheduler:schedule(60, function()
        player:addEffect("minecraft:speed", 200, 1)
    end)
end)
```

---

## 🛠️ In-Game Commands

LuaTweaker provides a rich `/lt` command suite for live debugging and inspection:

| Command | Description |
| :--- | :--- |
| `/lt hand` | Inspect item/block details currently held in hand (ID, NBT, Tags). |
| `/lt dump` | Dump registered Items, Blocks, Fluids, and Recipes to log files. |
| `/lt doctor` | Run health diagnostics on all loaded Lua scripts to detect syntax/performance issues. |
| `/lt reload` | Hot-reload all Lua scripts instantly without restarting the game. |

---

## 📚 Documentation Index

Explore the comprehensive topic guides in the [`docs/`](docs/README.md) directory:

- 🚀 [**GETTING_STARTED.md**](docs/GETTING_STARTED.md) - Complete environment and IDE setup guide.
- 🏗️ [**ARCHITECTURE_AND_SECURITY.md**](docs/ARCHITECTURE_AND_SECURITY.md) - Sandboxing & Security Layer.
- 🔨 [**RECIPES.md**](docs/RECIPES.md) - Full recipe manipulation reference.
- 🎨 [**CUSTOM_CONTENT.md**](docs/CUSTOM_CONTENT.md) - Registering items, blocks, fluids & datapacks.
- 🏬 [**LUA_MOD_SYSTEM.md**](docs/LUA_MOD_SYSTEM.md) - Building and packaging autonomous Lua Mods (.ZIP).
- 🛠️ [**JAVA_PATCHER.md**](docs/JAVA_PATCHER.md) & [**LOW_LEVEL_JAVA_AND_MIXIN.md**](docs/LOW_LEVEL_JAVA_AND_MIXIN.md) - Dynamic Bytecode Patching.
- ⚡ [**EVENTS.md**](docs/EVENTS.md) - Built-in game event hook catalog.
- 🎨 [**GUI_GRAPHICS.md**](docs/GUI_GRAPHICS.md) & [**SHADER_API.md**](docs/SHADER_API.md) - Custom UI rendering & screen shaders.
- 🤖 [**SERVICE_AND_SPATIAL_MATH_API.md**](docs/SERVICE_AND_SPATIAL_MATH_API.md) - Roblox-like APIs (`TweenService`, `Vector3`, `DataStore`).
- 🔌 [**ADDON_DEVELOPER_GUIDE.md**](docs/ADDON_DEVELOPER_GUIDE.md) - Building Java plugins for LuaTweaker.

---

## 📜 License

This project is licensed under the **[Apache License 2.0](LICENSE.txt)**.

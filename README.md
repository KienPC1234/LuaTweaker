<div align="center">

<img src="logo.png" alt="LuaTweaker Logo" width="500"/>

# LuaTweaker

**Ultra-Lightweight, High-Performance Lua Scripting Engine & Modding Framework for Minecraft 1.21.1**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![PAL](https://img.shields.io/badge/Loader-Agnostic%20(PAL)-orange.svg?style=for-the-badge)](docs/ARCHITECTURE_AND_SECURITY.md)
[![Java](https://img.shields.io/badge/Java-21-blue.svg?style=for-the-badge&logo=openjdk)](https://adoptium.net)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](LICENSE.txt)

</div>

---

## Overview

**LuaTweaker** is an ultra-lightweight, high-performance scripting engine for **Minecraft 1.21.1**, built specifically for **Modpack Creators** and **Modders**. Instead of wrestling with heavy frameworks or verbose languages, you write Lua scripts directly in your game directory and see results on the next reload.

The engine is **loader-agnostic**. All platform access goes through the **PAL (Platform Abstraction Layer)**, so the core engine and feature modules never touch any mod loader API directly. The current reference implementation targets NeoForge, and additional loaders (Fabric, Forge, ...) can be added by implementing the PAL interface without changing any core code.

The engine runs on **Lua 5.2 / Cobalt** and features per-addon memory sandboxing, automatic LSP / EmmyLua autocomplete stub generation, and deep Minecraft bytecode interception all without compiling Java code or restarting your client.

---

## Key Features

- **Comprehensive Recipe Management**:
  - Add, modify, or remove **Shaped 3x3**, **Shapeless**, **Smelting**, **Blasting**, **Smoking**, **Campfire**, **Smithing**, **Stonecutting**, **Brewing**, **Anvil**, and **Villager Trades** recipes.
  - Perform global ingredient replacements across all recipe types.

- **Dynamic Custom Content Creation**:
  - Register custom Items, Blocks, Fluids, Toolsets, and Armor sets at runtime (`startup:createItem()`, `startup:createBlock()`).
  - Auto-mount textures and datapack resources seamlessly without writing Java mods.

- **Autonomous Lua Mod System (`luamods/`)**:
  - Modular project architecture with `manifest.json`.
  - Direct **ZIP package loading** for 1-second Lua mod installation and community distribution.
  - Automatic isolation and mounting of Virtual ResourcePacks & Datapacks.

- **Signal-Based Event System (Roblox-style)**:
  - Reactive signal handles (`Signal.new()`, `Signal:Connect`, `Signal:Once`, `Signal:Wait`).
  - Built-in game signals (`EntityService.EntitySpawned`, `RemoteEvent.OnServerEvent`, `UserInputService.InputBegan`).
  - Task scheduler for delayed and repeating tasks (`Task:spawn`, `Task:delay`, `Task:wait`).

- **Graphics, VFX/SFX & Shaders**:
  - Built-in post-processing screen shader system (Film Grain, Screen Shake, CRT Scanlines, Chromatic Aberration, Vignette).
  - Advanced 2D GUI rendering via `GuiGraphics` (PoseStack matrix operations, gradient fills, 2D textures, item icons, and tooltips).

- **Dynamic Java Patching & Mixins**:
  - Unified `LuaTweaker.Runtime` namespace for JVM access (explicit `require`, no floating globals).
  - Bytecode interception with `Runtime.Hook(...):InjectHead` / `:InjectReturn` / `:Overwrite`.
  - Java class loading (`Runtime.Class`) and interface proxies (`Runtime.Proxy`).
  - Permission-gated via `manifest.json` (`runtime.reflection`, `runtime.bytecode_hook`).

- **Service-Oriented Reactive Architecture (Roblox-like APIs)**:
  - Object-oriented Service Registry (`game:GetService("TweenService")`, `DataStoreService`, `Signal`).
  - Spatial math vectors (`Vector3`, `Vector2`), color engine (`Color3`), and persistent key-value storage (`DataStore`).

- **Automatic IDE Autocomplete (LSP / EmmyLua)**:
  - Reflection-based `@LuaDoc` parser automatically generates type-safe stubs (`luatweaker-api.lua`) for **VS Code** and **IntelliJ IDEA**.

---

## LTVM Engine Architecture

LuaTweaker enforces strict **Separation of Concerns** across 4 decoupled modules:

```text
Luatweaker-root/
├── common-api/         # Pure Java 21: PAL Registry, Abstract Objects, VM Interfaces, LuaDoc Annotations
├── core-engine/        # LTVM Cobalt Engine Wrapper, Async Logger, Linter, EmmyLua Generator
├── modules/
│   └── module-recipes/ # Feature Module: Engine-agnostic Recipe manipulation logic
└── neoforge-platform/  # Reference PAL implementation (NeoForge Launcher & Reload Listeners)
```

The core-engine and `modules/*` depend only on the PAL interfaces defined in `common-api`. New loaders are supported by adding a new platform module that implements PAL => no changes to the core or modules.

---

## Lua Folder Layout

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

## Quick Start

### 1. Installation

1. Ensure you are running **Minecraft 1.21.1** with a supported loader. The current build targets **NeoForge 21.1.242** or higher.
2. Drop the **LuaTweaker** `.jar` file into your `mods/` directory.
3. Launch Minecraft once to generate the `lua/` workspace and autocomplete stubs.

### 2. Setting Up Autocomplete

- **VS Code**: Install the `Lua` extension (by Sumneko / Lua Language Server).
- **IntelliJ IDEA**: Install the `EmmyLua` plugin.
- Open the root `lua/` folder in your IDE. Autocomplete stubs at `lua/.luatweaker/stubs/luatweaker-api.lua` will be loaded automatically.

---

## Code Examples

All examples follow the Roblox (Luau) module style used in the bundled `lua/` scripts. Full API references live in [`docs/`](docs/README.md).

### 1. Recipe Modification

```lua
-- lua/server/ruby_recipes.lua
local recipes = Mod:GetService("Recipes")

local ruby = "luatweaker:custom_ruby"
local stick = "minecraft:stick"

-- Remove vanilla recipe
recipes:removeByOutput("minecraft:diamond_sword")

-- Add a 3x3 Shaped recipe
recipes:addShaped("ruby_block_craft", item("luatweaker:ruby_block", 1), {
    "RRR",
    "RRR",
    "RRR"
}, {
    R = ingredient(ruby)
})

-- Add a Smelting recipe
recipes:addSmelting("ruby_ore_smelt", item(ruby, 1), ingredient("luatweaker:ruby_ore"), 1.0, 200)
```

### 2. Custom Item & Block Registration (Startup Phase)

```lua
-- lua/startup/ruby_content.lua
local startup = Mod:GetService("Startup")

-- Create a custom item (fluent builder API)
startup:createItem("custom_ruby", function(item)
    item:maxStackSize(64)
        :rarity("EPIC")
        :burnTime(400)
        :displayName("Enchanted Ruby Gem")
        :tag("c:gems/ruby")
end)

-- Create a custom block
startup:createBlock("ruby_ore", function(block)
    block:hardness(4.5)
         :resistance(15.0)
         :lightLevel(3)
         :mineableWith("PICKAXE")
         :drop("luatweaker:custom_ruby", 1, 2)
end)
```

### 3. Signal-Based Events & Task Scheduling

```lua
-- lua/server/events.lua
local EntityService = Mod:GetService("EntityService")
local Task = Mod:GetService("Task")

-- Reactive signal-style event (Roblox `Signal:Connect`)
EntityService.EntitySpawned:Connect(function(entity)
    if entity.Type == "minecraft:player" then
        entity:SendMessage("§aWelcome to the server, " .. entity.Name .. "!")
        entity:GiveItem("minecraft:diamond", 3)
    end
end)

-- Custom signal (Roblox `Signal.new()`)
local onBossDefeated = Signal.new()
onBossDefeated:Connect(function(bossName, rewardXp)
    print("[Events] Boss defeated: " .. bossName .. " (+" .. rewardXp .. " XP)")
end)

-- Run a delayed task (after 3 seconds)
Task:delay(3.0, function()
    print("[Events] Delayed task fired!")
end)
```

---

## In-Game Commands

LuaTweaker provides a rich `/lt` command suite for live debugging and inspection:

| Command | Description |
| :--- | :--- |
| `/lt hand` | Inspect item/block details currently held in hand (ID, NBT, Tags). |
| `/lt dump` | Dump registered Items, Blocks, Fluids, and Recipes to log files. |
| `/lt doctor` | Run health diagnostics on all loaded Lua scripts to detect syntax/performance issues. |
| `/lt reload` | Hot-reload all Lua scripts instantly without restarting the game. |

---

## Documentation Index

Explore the comprehensive topic guides in the [`docs/`](docs/README.md) directory:

- [**GETTING_STARTED.md**](docs/GETTING_STARTED.md) - Complete environment and IDE setup guide.
- [**ARCHITECTURE_AND_SECURITY.md**](docs/ARCHITECTURE_AND_SECURITY.md) - Sandboxing & Security Layer.
- [**RECIPES.md**](docs/RECIPES.md) - Full recipe manipulation reference.
- [**CUSTOM_CONTENT.md**](docs/CUSTOM_CONTENT.md) - Registering items, blocks, fluids & datapacks.
- [**LUA_MOD_SYSTEM.md**](docs/LUA_MOD_SYSTEM.md) - Building and packaging autonomous Lua Mods (.ZIP).
- [**JAVA_PATCHER.md**](docs/JAVA_PATCHER.md) - Low-Level Java Runtime (`LuaTweaker.Runtime`) & Dynamic Bytecode Patching.
- [**EVENTS.md**](docs/EVENTS.md) - Built-in game event hook catalog.
- [**GUI_GRAPHICS.md**](docs/GUI_GRAPHICS.md) & [**SHADER_API.md**](docs/SHADER_API.md) - Custom UI rendering & screen shaders.
- [**SERVICE_AND_SPATIAL_MATH_API.md**](docs/SERVICE_AND_SPATIAL_MATH_API.md) - Roblox-like APIs (`TweenService`, `Vector3`, `DataStore`).
- [**ADDON_DEVELOPER_GUIDE.md**](docs/ADDON_DEVELOPER_GUIDE.md) - Building Java plugins for LuaTweaker.

---

## License

This project is licensed under the **[Apache License 2.0](LICENSE.txt)**.

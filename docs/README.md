# 📚 LuaTweaker Documentation Portal (`docs/`)

Welcome to the **LuaTweaker** documentation portal! LuaTweaker is an ultra-lightweight, high-performance Lua scripting engine for Minecraft 1.21.1 (NeoForge) designed for modpack creators.

---

## 🗺️ Documentation Directory Index

Select a topic guide below to explore LuaTweaker's features, APIs, and tutorials:

### 🚀 Getting Started, Setup & Architecture
*   📖 [**GETTING_STARTED.md**](GETTING_STARTED.md)  
    Onboarding guide, root `lua/` directory layout, IntelliJ IDEA setup (`genIntellijRuns`), and VS Code LLS autocompletion.

*   📖 [**ARCHITECTURE_AND_SECURITY.md**](ARCHITECTURE_AND_SECURITY.md)  
    Engine architecture, Per-Addon Isolated Sandboxes, Service Registry paradigm (`Mod:GetService`), Protected Metatables, and scoped execution stages.

---

### 🛠️ Dynamic Java Method Patching & Bytecode Hooking
*   📖 [**JAVA_PATCHER.md**](JAVA_PATCHER.md)  
    Dynamic Java method interceptor (`patcher:hookMethod`), argument overriding, method bypasses, and Java security class filtering.

*   📖 [**LOW_LEVEL_JAVA_AND_MIXIN.md**](LOW_LEVEL_JAVA_AND_MIXIN.md)  
    Low-level Java Class loading (`Java.loadClass`), Interface Proxies (`java:proxy`), `@Inject` HEAD/RETURN Mixin bytecode hooks (`mixin`), and KubeJS parity matrix.

---

### 🔨 Recipe Management
*   📖 [**RECIPES.md**](RECIPES.md)  
    Complete guide for Shaped 3x3 grid recipes, Shapeless recipes, Smithing, Stonecutting, Anvil, Brewing, Smelting, Blasting, Smoking, Campfire, Villager Trades, and Global Replacements.

---

### 🎨 Custom Content Creation
*   📖 [**CUSTOM_CONTENT.md**](CUSTOM_CONTENT.md)  
    Dynamic item creation (`startup:createItem()`), block creation (`startup:createBlock()`), custom fluids (`startup:createFluid()`), tool/armor material sets, auto-mounted textures (`lua/assets/`), and datapacks (`lua/data/`).

---

### 🌍 World Generation & Structures
*   📖 [**WORLDGEN.md**](WORLDGEN.md)  
    Dynamic ore generation (`worldgen:addOre`), NBT structure placement (`worldgen:addStructure`), and biome feature removal (`worldgen:removeFeature`).

---

### ⚡ Event Hooks & Custom Channels
*   📖 [**EVENTS.md**](EVENTS.md)  
    Roblox-style signal events (`Signal:Connect`, `EntityService.EntitySpawned`, `RemoteEvent.OnServerEvent`, `UserInputService.InputBegan`), custom signal creation (`Signal.new()`), and lightweight string channels (`events:listen` / `events:post`).

---

### ⏱️ Scheduler, VFX/SFX, Mobs & World Systems
*   📖 [**SCHEDULER_AND_EFFECTS.md**](SCHEDULER_AND_EFFECTS.md)  
    Tick-based task scheduler (`scheduler`), particle VFX (`particles`), 3D sound SFX (`sounds`), custom enchantments, mob effects, potions, and creative inventory tabs.

*   📖 [**MOB_AND_SPAWN.md**](MOB_AND_SPAWN.md)  
    Mob equipment & zombie gear (`entities:spawnMob`), mob loot table custom drops (`loot:addEntityDrop`), world spawn rules, mob deny filters (`worldgen:denySpawn`), dynamic spawn event hooks (`EntityService.EntitySpawned`), and random utilities (`utils.chance`, `utils.weightedRandom`).

*   📖 [**WORLD_AND_BOSSBAR.md**](WORLD_AND_BOSSBAR.md)  
    Custom BossBar API (`bossbar:create`), Minecraft Game Days (`world:getGameDay()`), real calendar dates, weather control, console command execution (`world:executeCommand`), game rules, block manipulation, and explosions.

*   📖 [**NBT_HELPERS.md**](NBT_HELPERS.md)  
    Easy NBT manipulation library (`nbt.compound`, `nbt.parse`, `nbt.merge`, `nbt.enchant`), structured Lua enum constants (`enums.Rarity`, `enums.BossBarColor`, `enums.EquipmentSlot`), and calendar day getters.

---

### 🔌 Mod Integrations & Advanced APIs
*   📖 [**MOD_INTEGRATIONS.md**](MOD_INTEGRATIONS.md)  
    Mod query API (`mods`), registry search & filters (`registry`), JEI / REI / EMI item hiding (`jei`), Async HTTP (`http`), Network Messaging (`network`), Tooltips, Loot, Tags, Commands, and `unsafe`.

---

### 🏬 Autonomous Self-Contained Lua Mod System (`luamods/`)
*   📖 [**LUA_MOD_SYSTEM.md**](LUA_MOD_SYSTEM.md)  
    Self-contained Lua Mod architecture (`luamods/`), `manifest.json` format, 4-Step Java Execution Pipeline, Memory Isolation Sandboxes, auto-mounted Virtual ResourcePacks & Datapacks, **ZIP package support** for 1-second installation, and community mod distribution.

---

### 🎨 Client-side Rendering & Visuals
*   📖 [**SHADER_API.md**](SHADER_API.md)  
    Post-processing shader system (`game:GetService("Shaders")`), Film Grain, Screen Shake, Pixelation, Chromatic Aberration, Vignette, Color Correction, CRT Scanlines, and custom GLSL shader loading.

*   📖 [**GUI_GRAPHICS.md**](GUI_GRAPHICS.md)  
    Advanced GUI rendering API (`game:GetService("GuiGraphics")`), PoseStack transformations, fill & gradient rectangles, 2D texture rendering, centered text, item icon rendering, tooltips, and push/pop pose matrix operations.

---

### 🛠️ In-Game Commands & Debugging
*   📖 [**COMMANDS.md**](COMMANDS.md)  
    Complete command manual (`/lt hand` item & block target inspector, `/lt dump`, `/lt doctor` script health diagnostics, `/lt reload`).

*   📖 [**TROUBLESHOOTING.md**](TROUBLESHOOTING.md)  
    Log file diagnostics (`lua/logs/luatweaker.log`), error line tracebacks, path traversal guards, and infinite loop watchdog limit settings.

---

### 🤖 Service-Oriented Reactive Architecture & Spatial Math
*   📖 [**SERVICE_AND_SPATIAL_MATH_API.md**](SERVICE_AND_SPATIAL_MATH_API.md)  
    Complete guide for Service-Oriented APIs (`game:GetService`), Task Scheduler (`task`), DataStore Persistent Storage (`DataStoreService`), Smooth Property Interpolation (`TweenService`), Spatial Vector Math (`Vector3`, `Vector2`), Color Engine (`Color3`), Reactive Event Signals (`Signal`), and Object Trees (`Instance.new`).

---

### 🔌 Addon Developers
*   📖 [**ADDON_DEVELOPER_GUIDE.md**](ADDON_DEVELOPER_GUIDE.md)  
    Complete guide for third-party mod developers building LuaTweaker extensions (`@LuaTweakerPlugin`, `ILuaTweakerPlugin`).

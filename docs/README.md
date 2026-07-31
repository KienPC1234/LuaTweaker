# LuaTweaker Documentation Portal (`docs/`)

LuaTweaker is a high-performance Lua 5.1/Luau scripting engine for Minecraft 1.21.1 (NeoForge).

> **LuaTweaker Architecture Style:**
> **TƯ DUY LẮP RÁP (MODULE & BUILDER) + NGỮ PHÁP TỰ NHIÊN CỦA LUA**
> - **Static Content Registration:** Chainable Builder DSLs ending with `:Register()`.
> - **Runtime Logic & AI:** Explicit `require("LuaTweaker.ModuleName")` imports, Signals, and Services.
> - **Zero Floating Globals:** Every API is imported explicitly from its module.

---

## Documentation Directory Index

### Core Architecture & Onboarding
* [**GETTING_STARTED.md**](GETTING_STARTED.md) — Setup guide, directory structure, IDE launcher tasks, and LSP autocompletion.
* [**ARCHITECTURE_AND_SECURITY.md**](ARCHITECTURE_AND_SECURITY.md) — Submodule architecture, PAL specification, sandbox security, LuaTweaker Architecture Style, and technical roadmap.

### Static Content & Recipe Registration
* [**CUSTOM_CONTENT.md**](CUSTOM_CONTENT.md) — `Content` Builder DSL for Items (`Content.NewItem`), Blocks (`Content.NewBlock`), Fluids (`Content.NewFluid`), Storage, and Datapacks.
* [**RECIPES.md**](RECIPES.md) — `Recipe` Builder DSL for Shaped (`Recipe.Shaped`), Shapeless, Smelting, Smithing, Anvil, Brewing, and Replacements.
* [**WORLDGEN.md**](WORLDGEN.md) — Ore generation, structure placement, and biome feature management.
* [**LUA_MOD_SYSTEM.md**](LUA_MOD_SYSTEM.md) — Autonomous `luamods/` architecture: single `main.lua` entrypoint, unified config, sandbox & inter-mod IPC, and 5-step execution pipeline.

### Runtime Logic, Signals & Spatial Math
* [**SCRIPTING_GUIDE.md**](SCRIPTING_GUIDE.md) — Comprehensive guide covering explicit `require` imports, Content/Recipe Builders, and Event Signals.
* [**EVENTS.md**](EVENTS.md) — Reactive signal engine (`Events.OnEntityDamaged`, `Events.OnEntitySpawned`, `Events.OnBlockBreak`), custom signals, and Network RemoteEvents.
* [**SERVICE_AND_SPATIAL_MATH_API.md**](SERVICE_AND_SPATIAL_MATH_API.md) — Runtime services (`Task`, `TweenService`), spatial math (`Vector3`, `Vector2`, `Color3`), and extended `math` library.
* [**MOB_AND_SPAWN.md**](MOB_AND_SPAWN.md) — Entity spawning (`Entities.SpawnMob`), loot table rules (`Loot`), and random utilities (`Utils`).

### Specialized Systems & Commands
* [**JAVA_PATCHER.md**](JAVA_PATCHER.md) — `LuaTweaker.Runtime`: class resolution, field mutation, Java proxies, and permission-gated bytecode hooks (`InjectHead`/`InjectReturn`/`Overwrite`).
* [**COMMANDS.md**](COMMANDS.md) — In-game diagnostic commands (`/lt hand`, `/lt dump`, `/lt doctor`, `/lt reload`).
* [**TROUBLESHOOTING.md**](TROUBLESHOOTING.md) — Log diagnostics (`lua/logs/luatweaker.log`), error tracebacks, and loop limit settings.
* [**ADDON_DEVELOPER_GUIDE.md**](ADDON_DEVELOPER_GUIDE.md) — Third-party Java mod plugin interface (`@LuaTweakerPlugin`, `ILuaTweakerPlugin`).

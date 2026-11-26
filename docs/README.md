# LuaTweaker Documentation (`docs/`)

Start here: **GETTING_STARTED** (setup) -> **SCRIPTING_GUIDE** (conventions) ->
pick a system below.

## Start

| Doc | Read when you want to... |
| :--- | :--- |
| [GETTING_STARTED](GETTING_STARTED.md) | Install, run NeoForge/Fabric client or server, set up IDE stubs, use `/lt` commands. |
| [SCRIPTING_GUIDE](SCRIPTING_GUIDE.md) | Learn the `require` + Builder + Signal conventions with correct examples. |
| [LUA_MOD_SYSTEM](LUA_MOD_SYSTEM.md) | Package an autonomous `luamods/` mod (manifest, config, lifecycle, IPC). |
| [JSON_API](JSON_API.md) | `Json` service (encode/decode), `mod:Files():ReadJson`, `mod:Exports():WriteJson`. |
| [ARCHITECTURE_AND_SECURITY](ARCHITECTURE_AND_SECURITY.md) | Understand Architectury PAL, module boundaries, sandbox rules. |
| [TROUBLESHOOTING](TROUBLESHOOTING.md) | Diagnose load errors, logs, loop limits. |

## Build content & recipes

| Doc | Covers |
| :--- | :--- |
| [CUSTOM_CONTENT](CUSTOM_CONTENT.md) | `Content` builders: items, blocks, fluids, entities (`rig()`), storage, datapacks. |
| [GECKOLIB_ANIMATIONS](GECKOLIB_ANIMATIONS.md) | GeckoLib 4 custom 3D animated models and animation triggers for items, blocks, entities. |
| [RECIPES](RECIPES.md) | `Recipe` builders: shaped/shapeless, smelting, smithing, anvil, brewing, trades. |
| [DATA_DRIVEN_RECIPES_AND_CAPABILITIES](DATA_DRIVEN_RECIPES_AND_CAPABILITIES.md) | Generic data-driven recipes (Mekanism/Create) and OOP block capability transfer API. |
| [WORLDGEN](WORLDGEN.md) | `Worldgen` (ore/vegetation/biome mods), `Biomes:Define`, structures, mixed veins. |
| [TEXT_ENCHANT_EFFECT_PARTICLE_API](TEXT_ENCHANT_EFFECT_PARTICLE_API.md) | Rich text components, data-driven enchantments, custom mob effects/potions, particle shapes, anti-cheat primitives. |
| [CLAIM_TOOL_EXAMPLE](CLAIM_TOOL_EXAMPLE.md) | Claim tool composed from primitives: `Region3` boxes, `Storage` claims, break/place/interact/explosion protection, `/claim` command. |
| [MOB_AND_SPAWN](MOB_AND_SPAWN.md) | Spawning, AI goals, loot tables (`Loot`). |
| [BLOCK_CAPABILITY_API](BLOCK_CAPABILITY_API.md) | RF/FE machines: energy/fluid/item capabilities. |

## Runtime logic & AI

| Doc | Covers |
| :--- | :--- |
| [EVENTS](EVENTS.md) | `Events:Listen`, `Signal`, threading (`Fire` vs `FireSync`), built-in events. |
| [BEHAVIOR_TREES](BEHAVIOR_TREES.md) | Pure-Lua `Brain`: Selector/Sequence/Parallel/Utility, decorators, blackboards, Squad packs. |
| [SERVICE_AND_SPATIAL_MATH_API](SERVICE_AND_SPATIAL_MATH_API.md) | `Task` (spawn/delay/wait), `TweenService`, `Vector3/Vector2/Color3`, math extensions. |
| [NETWORK](NETWORK.md) | RemoteEvent/RemoteFunction wire format, budgets, byte contract. |
| [SERVER_MOD_PACKS](SERVER_MOD_PACKS.md) | Server-advertised LuaMod zips: per-bundle folders, prompt + consent, hash-pinned download (never auto-loaded). |

## Client, commands, integration

| Doc | Covers |
| :--- | :--- |
| [CLIENT_AND_SOUND_API](CLIENT_AND_SOUND_API.md) | `GuiService` HUD, `KeyBindService`, `ClientEffects`, sounds. |
| [SHADER_API](SHADER_API.md) | `Shaders` service: built-in screen/camera effects + custom GLSL/SPIR-V post passes. |
| [IMGUI_API](IMGUI_API.md) | `ImGui` advanced panels: `OnFrame` rule, widgets, item slots, input gating. |
| [JEI_AND_GUI_EDITOR](JEI_AND_GUI_EDITOR.md) | In-game Dear ImGui visual GUI designer (`/lt gui`) and custom JEI recipe categories. |
| [COMMANDS](COMMANDS.md) | `/lt` diagnostics + `Commands:Register` for Lua-owned `/<cmd>`. |
| [ADDON_DEVELOPER_GUIDE](ADDON_DEVELOPER_GUIDE.md) | Third-party Java plugins (`ILuaTweakerPlugin`), templates. |
| [JAVA_PATCHER](JAVA_PATCHER.md) | `LuaTweaker.Runtime`: class resolution, proxies, gated bytecode hooks. |

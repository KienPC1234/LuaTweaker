# LuaTweaker Documentation (`docs/`)

Start here: **GETTING_STARTED** (setup) → **SCRIPTING_GUIDE** (conventions) →
pick a system below. Files marked `stale` describe an older API surface —
do not implement from them.

## Start

| Doc | Read when you want to... |
| :--- | :--- |
| [GETTING_STARTED](GETTING_STARTED.md) | Install, run client/server, set up IDE stubs, use `/lt` commands. |
| [SCRIPTING_GUIDE](SCRIPTING_GUIDE.md) | Learn the `require` + Builder + Signal conventions with correct examples. |
| [LUA_MOD_SYSTEM](LUA_MOD_SYSTEM.md) | Package an autonomous `luamods/` mod (manifest, config, lifecycle, IPC). |
| [ARCHITECTURE_AND_SECURITY](ARCHITECTURE_AND_SECURITY.md) | Understand PAL, module boundaries, sandbox rules. |
| [TROUBLESHOOTING](TROUBLESHOOTING.md) | Diagnose load errors, logs, loop limits. |

## Build content & recipes

| Doc | Covers |
| :--- | :--- |
| [CUSTOM_CONTENT](CUSTOM_CONTENT.md) | `Content` builders: items, blocks, fluids, entities (`rig()`), storage, datapacks. |
| [GECKOLIB_ANIMATIONS](GECKOLIB_ANIMATIONS.md) | GeckoLib 4 custom 3D animated models and animation triggers for items, blocks, entities. |
| [RECIPES](RECIPES.md) | `Recipe` builders: shaped/shapeless, smelting, smithing, anvil, brewing, trades. |
| [WORLDGEN](WORLDGEN.md) | `Worldgen` (ore/vegetation/biome mods), `Biomes:Define`, structures, mixed veins. |
| [MOB_AND_SPAWN](MOB_AND_SPAWN.md) | Spawning, AI goals, loot tables (`Loot`). |
| [BLOCK_CAPABILITY_API](BLOCK_CAPABILITY_API.md) | RF/FE machines: energy/fluid/item capabilities. |

## Runtime logic & AI

| Doc | Covers |
| :--- | :--- |
| [EVENTS](EVENTS.md) | `Events:Listen`, `Signal`, threading (`Fire` vs `FireSync`), built-in events. |
| [BEHAVIOR_TREES](BEHAVIOR_TREES.md) | Pure-Lua `Brain`: Selector/Sequence/Parallel/Utility, decorators, blackboards, Squad packs. |
| [SERVICE_AND_SPATIAL_MATH_API](SERVICE_AND_SPATIAL_MATH_API.md) | `Task` (spawn/delay/wait), `TweenService`, `Vector3/Vector2/Color3`, math extensions. |
| [NETWORK](NETWORK.md) | RemoteEvent/RemoteFunction wire format, budgets, byte contract. |

## Client, commands, integration

| Doc | Covers |
| :--- | :--- |
| [CLIENT_AND_SOUND_API](CLIENT_AND_SOUND_API.md) | `GuiService` HUD, `KeyBindService`, `ClientEffects`, sounds. |
| [COMMANDS](COMMANDS.md) | `/lt` diagnostics + `Commands:Register` for Lua-owned `/<cmd>`. |
| [ADDON_DEVELOPER_GUIDE](ADDON_DEVELOPER_GUIDE.md) | Third-party Java plugins (`ILuaTweakerPlugin`), templates. |
| [JAVA_PATCHER](JAVA_PATCHER.md) | `LuaTweaker.Runtime`: class resolution, proxies, gated bytecode hooks. |

## Stale — do not implement from these

Historical drafts that predate the `require()` system, `GuiService`,
`Task` service, sandbox, or the Luava migration. Kept for archaeology only.

| Doc | Why stale |
| :--- | :--- |
| [GUI_GRAPHICS](GUI_GRAPHICS.md) | Draft; `GuiGraphicsAPI.java` does not exist (real API: `GuiService`). |
| [SHADER_API](SHADER_API.md) | `Shaders` service: 24 named effects + custom GLSL/SPIR-V post passes. |
| [NBT_HELPERS](NBT_HELPERS.md) | `nbt`/`enums` globals do not exist (real path: `NbtCodec`). |
| [SCHEDULER_AND_EFFECTS](SCHEDULER_AND_EFFECTS.md) | Predates the `Task` service and bootstrap conventions. |
| [MOD_INTEGRATIONS](MOD_INTEGRATIONS.md) | `mods`/`registry`/`jei`/`http`/`unsafe` globals predate the sandbox. |
| [WORLD_AND_BOSSBAR](WORLD_AND_BOSSBAR.md) | `bossbar`/`world` globals predate current APIs. |
| [LTVM_DOCUMENTATION](LTVM_DOCUMENTATION.md) | VM details predate the Luava migration. |

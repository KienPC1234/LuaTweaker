# Multi-Platform Minecraft & Modding Inspection Toolkit (`mc_inspect`)

Cross-platform CLI and interactive REPL toolkit (Windows, Linux, macOS) written in pure standard Python 3 with zero external pip dependencies.

Key Features:
* **Forward-Compatible Multi-Version and Multi-Platform**: Automatically discovers Minecraft versions (1.21.1, 1.21.2+, 1.22+) and mod loaders (NeoForge, Fabric, Forge, and Vanilla mapped jars).
* **Automated Workspace Scanning**: Auto-discovers artifacts across all workspace subprojects (`*-platform`, `moddev/artifacts`, `.gradle/loom-cache`, `build/libs`) and global Gradle caches (`fabric-loom`, `forge_gradle`, `neoforged`, `modules-2`).
* **Source & Bytecode Inspection**: Reads official Mojang-mapped Java sources directly from `sources.jar` and disassembles bytecode instructions via `javap`.
* **Fast Lookup for Registries, Gameplay Primitives, and Capabilities**: Instantly inspects game components, sound events, particles, mob effects, entity types, tags, network packets, and capabilities.

---

## 1. Quick Start

* **Windows**: `tools\mc <command> [args]` or `python tools/mc_inspect.py <command> [args]`
* **Linux / macOS**: `./tools/mc <command> [args]`
* **Interactive Shell / REPL**: `tools\mc shell` (or `tools\mc repl`)
* **Manual Platform Override**: Pass `--platform <neoforge|fabric|forge>` or `--sources <path>` / `--jar <path>`
* **Fuzzy Command Suggestions**: Automatically suggests the closest matching command on typos (e.g. `mc sorce` -> suggests `source`).

> Smart Short Name Resolution: Any command accepting a class name supports unqualified short names (e.g. `Items` automatically resolves to `net.minecraft.world.item.Items` with 1,333 items rather than `net.minecraft.references.Items`).

---

## 2. Command Reference

| Command | Domain | Description | Practical Example |
|---|---|---|---|
| `status` / `platform` | Environment | Displays active platform, Minecraft version, sources JAR, merged JAR, and classpath | `tools\mc status` |
| `source <class> [-m fn]` | Mojmap Source | Displays original Java source from `sources.jar` (supports extracting single methods or line ranges) | `tools\mc source MobEffect -m applyEffectTick` |
| `disasm <class> [-c] [-m]` | Bytecode | Disassembles bytecode instructions via `javap` with resolved classpath | `tools\mc disasm 'Util$OS' -m openFile -c` |
| `effects [query]` | Gameplay | Inspects built-in `MobEffects` (constant, ID, category Beneficial/Harmful/Neutral, color hex) | `tools\mc effects speed` |
| `potions [query]` | Gameplay | Inspects built-in `Potions` (`minecraft:healing`, `swiftness`, etc.) | `tools\mc potions healing` |
| `sounds [query]` | Audio | Inspects 1,580+ built-in `SoundEvents` identifiers | `tools\mc sounds anvil` |
| `particles [query]` | Visual | Inspects 100+ built-in `ParticleTypes` (`flame`, `crit`, `smoke`, etc.) | `tools\mc particles flame` |
| `entities [query]` | Entities | Inspects 130+ built-in `EntityType` constants, Java classes, and MobCategory | `tools\mc entities zombie` |
| `capabilities [query]` | Capabilities | Inspects platform capabilities (NeoForge: `ItemHandler`, `EnergyStorage`, `FluidHandler`; Fabric: Transfer API) | `tools\mc capabilities item` |
| `events [query]` | Events | Inspects platform events (including `@Cancelable`, `@HasResult`, and getter methods) | `tools\mc events damage` |
| `extensions [query]` | Extensions | Inspects platform extension interfaces (`IItemExtension`, etc.) | `tools\mc extensions item` |
| `components [query]` | Data | Inspects Minecraft 1.20.5+ `DataComponents` (NBT replacement: `CUSTOM_DATA`, `FOOD`, etc.) | `tools\mc components food` |
| `attributes [query]` | Entity Stats | Inspects built-in entity `Attributes` (default values, min, max ranges) | `tools\mc attributes health` |
| `properties [query]` | Block States | Inspects 112+ `BlockStateProperties` (`FACING`, `WATERLOGGED`, `POWERED`, etc.) | `tools\mc properties facing` |
| `packets [query]` | Network | Inspects 158+ network protocol packets (`Clientbound*`, `Serverbound*`) | `tools\mc packets block` |
| `registries [query]` | Core | Inspects built-in `BuiltInRegistries` | `tools\mc registries` |
| `tags [category]` | Tags | Inspects built-in TagKeys (item, block, enchantment, entity, biome, fluid) | `tools\mc tags enchantment` |
| `usages <symbol>` | References | Fast reference search across the entire Minecraft and NeoForge codebase | `tools\mc usages IClientMobEffectExtensions --limit 5` |
| `tree <class>` | Hierarchy | Displays class inheritance hierarchy (`extends` and `implements`) | `tools\mc tree Zombie` |
| `fields <class> [-q]` | Reflection | Lists declared fields and enum constants | `tools\mc fields InteractionResult` |
| `methods <class> [-q]` | Reflection | Lists declared methods and method signatures | `tools\mc methods ItemStack -q get` |
| `search <query>` | Index | Searches classes by name or regex | `tools\mc search MobEffect` |
| `shell` / `repl` | Interactive | Starts an interactive REPL shell | `tools\mc shell` |
| `--json` | Automation | Outputs results in structured JSON format | `tools\mc effects --json` |

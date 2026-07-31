# Engine Architecture, Isolation Security & Technical Specification

LuaTweaker is a modular, multi-tier Lua 5.1/Luau execution engine for Minecraft 1.21.1 (NeoForge).

---

## 1. LuaTweaker Architecture Style & Design Identity

The core design identity of LuaTweaker is built upon:
> **TƯ DUY LẮP RÁP (MODULE & BUILDER) + NGỮ PHÁP TỰ NHIÊN CỦA LUA**

1. **Content & Recipe Registration:** Chainable Builder DSLs ending with `:Register()`. Zero unanchored global magic.
2. **Runtime Logic & Signals:** Event-driven architecture using `Signal` objects and explicit `require("LuaTweaker.ModuleName")` imports.
3. **Lua-Native Types:** Clean `Vector3`, `Vector2`, `Color3`, tables, and first-class functions optimized for Cobalt VM execution.

```
[ LUA-TWEAKER ARCHITECTURE STYLE ]
   │
   ├──> 1. CONTENT DEFINITION (Static Registration)
   │      └──> Builder Pattern with explicit Namespace imports (Content.NewItem, Recipe.Shaped)
   │
   ├──> 2. RUNTIME SERVICES (Game Logic & AI)
   │      └──> Managed by independent Services & Signals (Events.OnEntityDamaged, Task.Delay)
   │
   └──> 3. LUA-NATIVE TYPES (Performance & Geometry)
          └──> Vector3, Vector2, Color3, and native Lua tables
```

---

## 2. Module Dependency Hierarchy

LuaTweaker enforces strict Single Responsibility Principle (SRP) and Dependency Inversion Principle (DIP) across Java submodules:

```
common-api (Pure Java 21 Interfaces, zero external dependencies)
    ^
core-engine (Cobalt 0.9.9 VM Wrapper, Logger, EmmyLua Stub Generator)
    ^
modules/module-* (Domain-specific bindings: recipes, entities, events, tasks, math, client, network)
    ^
neoforge-platform (NeoForge Bootstrap launcher, concrete PAL implementations)
```

---

## 3. Sandbox Isolation & Metatable Security

To prevent cross-script variable pollution and global environment corruption:

1. **Isolated Table Environments:** Each script file (`.lua`) is executed within its own local environment table. Global variable definitions in one script do not pollute or mutate globals in sibling scripts.
2. **Read-Only System Metatables (`__newindex`):** Core system objects are locked with protected metatables. Mutation attempts trigger security exceptions without crashing the VM.
3. **Headless Dedicated Server Protection (`Dist.DEDICATED_SERVER`):** Client-only visual APIs evaluate runtime environment state via `FMLEnvironment.dist`. On dedicated servers, calls safely log diagnostic info and operate as non-blocking no-ops.

---

## 4. Technical Architecture Roadmap (Future Engine Specifications)

### 4.1 Bytecode Hook Engine (`Runtime.Hook`)
Allow scripts to dynamically register bytecode injection hooks (`:InjectHead`, `:InjectReturn`, `:Overwrite`) into target Minecraft/NeoForge Java methods, gated by the `runtime.bytecode_hook` manifest permission.

### 4.2 Dynamic Native Interface Proxies (`Runtime.Proxy` / `Runtime.Class`)
Enable Lua scripts to resolve classes and instantiate Java interfaces directly from Lua via `java.lang.reflect.Proxy`, gated by the `runtime.reflection` manifest permission.

### 4.3 Multi-Threaded Parallel Actor Model (`Worker.new`)
Support parallel Luau actor execution model for background spatial calculations, pathfinding, and data processing.

### 4.4 Custom GLSL Shader Pipeline (`Shaders`)
Full custom GLSL post-processing render pipeline allowing modpack authors to supply custom shader JSON files and uniform parameters.

# Engine Architecture, Isolation Security & Technical Specification

LuaTweaker is a modular, multi-loader Lua 5.4 (Luava) execution engine for Minecraft 1.21.1 (Architectury: NeoForge & Fabric).

---

## 1. LuaTweaker Architecture Style & Design Identity

The core design identity of LuaTweaker is built upon:
> **TƯ DUY LẮP RÁP (MODULE & BUILDER) + NGỮ PHÁP TỰ NHIÊN CỦA LUA**

1. **Content & Recipe Registration:** Chainable Builder DSLs ending with `:Register()`. Zero unanchored global magic.
2. **Runtime Logic & Signals:** Event-driven architecture using `Signal` objects and explicit `require("LuaTweaker.ModuleName")` imports.
3. **Lua-Native Types:** Clean `Vector3`, `Vector2`, `Color3`, tables, and first-class functions optimized for the Luava VM.

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
core-engine (Luava 0.2.7 VM wrapper, LuaBinder, Logger, EmmyLua Stub Generator)
    ^
modules/module-* (Domain-specific bindings: recipes, entities, events, tasks, math, client, network, ...)
    ^
common (Shared Architectury Minecraft PAL, registrars, commands, mixins)
    ^
neoforge / fabric (Loader entrypoints, native capabilities/transfer, loader events)
```

---

## 3. Sandbox Isolation & Metatable Security

To prevent cross-script variable pollution and global environment corruption:

1. **Isolated Table Environments:** Each script file (`.lua`) is executed within its own local environment table. Global variable definitions in one script do not pollute or mutate globals in sibling scripts.
2. **Read-Only System Metatables (`__newindex`):** Core system objects are locked with protected metatables. Mutation attempts trigger security exceptions without crashing the VM.
3. **Headless Dedicated Server Protection (`Env.SERVER`):** Client-only visual APIs evaluate runtime environment state via `Platform.getClientOrNull()` and Architectury `Platform.getEnvironment()`. On dedicated servers, calls safely log diagnostic info and operate as non-blocking no-ops.
4. **File sandbox:** the VM exposes no `io`/`os` file functions. `mod:Files()` reads only the owning mod's folder or `.zip`; `mod:Exports()` writes only `<gameDir>/luatweaker/exports/<mod_id>/`. Every path is normalized and containment-checked (including symlink escapes) by `LuaModPathSecurity`; reads/writes are size-capped and writes are atomic. Exports are local files and are never synced to clients or servers.

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

### 4.5 Network Security Architecture (implemented in `module-update`)

Outbound network access is a three-layer system:

1. **Declarative update checks (default):** `"update_url": "https://..."` in
   manifest.json is fetched by the engine on the Java side with a dedicated
   daemon thread (never in the Lua sandbox, never on a game thread). Only
   `https://` feeds are accepted. Results are shown in chat and exposed
   read-only to Lua via `mod:GetUpdateStatus()` / `Update` service.
2. **Safe read-only API:** `Net`-adjacent Lua code can never trigger a fetch;
   it only reads the engine-cached result table.
3. **Permission-gated free HTTP:** `Net:HttpGet(url, timeout)` is denied unless
   at least one loaded mod declares `"permissions": ["net.http"]`. The grant
   is **per installation**: the VM cannot attribute runtime calls to a
   specific mod (the `mod` global is spoofable from Lua), so the decision is
   made deterministically from the Java-side mod registry — never from the
   global. All granting mods are listed in red warnings (chat + log) so
   admins can audit their modpacks.

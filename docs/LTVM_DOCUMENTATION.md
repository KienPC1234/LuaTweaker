# LuaTweaker VM (LTVM) - Architectural Design & Extension Guide

**LuaTweaker VM (LTVM)** is an ultra-lightweight, high-performance scripting engine designed to allow fast, dynamic, and non-blocking game modifications. It is built to ensure a zero-clutter, clean multi-module architecture, enabling easy porting to other platforms (Fabric/Forge) and seamless additions of new features.

---

## 🏗️ 1. Core Architectural Layout

LTVM is split into four distinct, decoupled modules to enforce the **Separation of Concerns**:

```text
luatweaker-root/
├── common-api/                 # Pure Java 21: PAL Registry, Abstract Objects, VM Interfaces, LuaDoc Annotations
├── core-engine/                # LTVM core wrapper, Async Logger, Linter, EmmyLua Generator
├── modules/
│   └── module-recipes/         # Feature Module: Abstract Recipe manipulation logic
└── neoforge-platform/          # NeoForge specific implementations of PAL and launch events
```

### 1.1 `common-api` (Platform Abstraction Layer - PAL)
Contains only pure Java 21 interfaces and records. It has **zero dependencies** on Minecraft, NeoForge, or any Lua engine library (Cobalt/LuaJ).
- **Abstract Game Objects**: `IItem`, `IBlock`, `IEntity`, `IRecipe` wrapping raw game classes.
- **PAL Interface**: `IPlatformHelper` to lookup items/tags and query registry files.
- **Decoupled VM**: `ILuaValue`, `ILuaTable`, `ILuaFunction`, `ILuaEngine` interfaces.
- **Annotations**: `@LuaDoc` metadata annotations.

### 1.2 `core-engine` (LTVM Cobalt Wrapper)
Implements the abstract VM interfaces using Cobalt. It manages the Lua execution lifecycle, asynchronous logging, custom compile/runtime error tracebacks, and LSP stubs. It compiles against Cobalt, exposing its APIs transitively.

### 1.3 `modules/module-recipes` (Feature Modules)
Contains business logic for editing recipes. By compiling only against `common-api`, it is **completely engine-agnostic and platform-agnostic**. It registers Lua bindings by interacting with `ILuaTable` and `ILuaFunction` callback interfaces rather than importing Cobalt or Minecraft classes.

### 1.4 `neoforge-platform` (Launcher Entrypoint)
Houses concrete NeoForge code. It implements `IPlatformHelper` and concrete game object wrappers (`NeoForgeItem` -> `IItem`). When Minecraft data reload starts, it initializes the engine, registers bindings, and applies modifications back to Minecraft's `RecipeManager`.

---

## 🛠️ 2. Dynamic EmmyLua Stub Generator

LTVM features a reflection-based LSP Stub Generator, making code autocomplete and IDE support instant and error-free:

1. **`@LuaDoc` Annotation**:
   Placed on interface definitions and service methods:
   ```java
   @LuaDoc(
       description = "Removes all recipes that result in the specified output item.",
       params = {"output: string"},
       returnType = "void"
   )
   void removeByOutput(String output);
   ```
2. **`LtvmStubGenerator`**:
   Iterates through classes using Java reflection, reading `@LuaDoc` annotations and translating signatures into EmmyLua formatting.
3. **`LtvmStubExporter`**:
   Saves the resulting stubs to `lua/.luatweaker/stubs/luatweaker-api.lua` (resolving development workspace roots dynamically).

---

## 🚀 3. How to Extend LTVM (Adding a New Service)

To add a new script service (e.g. custom block manipulation, command executor, event hooks), follow this simple 5-step process:

### Step 1: Define the Service Interface in `common-api`
Create the interface and define the methods, decorating them with `@LuaDoc`:
```java
package com.luatweaker.api.block;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Service for managing world blocks.")
public interface IBlockManagerService {
    @LuaDoc(
        description = "Replaces a block in the world at the specified coordinates.",
        params = {"x: number", "y: number", "z: number", "blockId: string"},
        returnType = "void"
    )
    void setBlock(int x, int y, int z, String blockId);
}
```

### Step 2: Implement the Service
Write the implementation in your features module:
```java
public class BlockManagerService implements IBlockManagerService {
    @Override
    public void setBlock(int x, int y, int z, String blockId) {
        // Business logic or queuing modifications...
    }
}
```

### Step 3: Define the Abstract Lua Binding
In your features module, write a binder class that takes `ILuaTable` and maps the functions:
```java
public class BlocksLuaBinding {
    public static void bind(ILuaTable table, IBlockManagerService service) {
        table.rawset("setBlock", args -> {
            int x = args[1].asInt();
            int y = args[2].asInt();
            int z = args[3].asInt();
            String blockId = args[4].asString();
            service.setBlock(x, y, z, blockId);
            return null;
        });
    }
}
```

### Step 4: Hook and Register in the Platform Launcher
In `neoforge-platform` (or any other launcher platform), instantiate the service and bind it during script initialization:
```java
// Initialize your service
BlockManagerService blockService = new BlockManagerService();

// Create binding table and bind
ILuaTable blocksTable = engine.createTable();
BlocksLuaBinding.bind(blocksTable, blockService);

// Register table as a service
engine.registerService("Blocks", blocksTable);

// Update LSP Generator to include the new stub
stubGen.generateClassStub(IBlockManagerService.class, "Blocks");
```

### Step 5: Start Scripting!
Now, your Lua scripts can retrieve and use the service instantly:
```lua
local blocks = Mod:GetService("Blocks")
blocks:setBlock(100, 64, -200, "minecraft:diamond_block")
```
The EmmyLua Stub Generator will automatically write stubs for the `Blocks` service and the `setBlock` method, and the developer's IDE will instantly provide autocomplete suggestions!

# LuaTweaker VM (LTVM) - Architectural Design & Extension Guide

**LuaTweaker VM (LTVM)** is an ultra-lightweight, high-performance scripting engine designed to allow fast, dynamic, and non-blocking game modifications. It is built to ensure a zero-clutter, clean multi-module architecture, enabling easy porting to other platforms (Fabric/Forge) and seamless additions of new features.

---

## 🏗️ 1. Core Architectural Layout

LTVM is split into decoupled subprojects to enforce the **Separation of Concerns** and satisfy the **SOLID Single Responsibility Principle (SRP)**:

```text
luatweaker-root/
├── common-api/                 # Pure Java 21: PAL Registry, Abstract Objects, VM Interfaces, LuaDoc Annotations
├── core-engine/                # LTVM core wrapper, Async Logger, Linter, EmmyLua Generator, Task Scheduler
├── modules/
│   ├── module-recipes/         # Feature Module: Abstract Recipe manipulation logic
│   ├── module-entities/        # Feature Module: AIGoals and entity state logic
│   ├── module-interaction/     # Feature Module: Workspace block/entity/item OOP wrappers
│   ├── module-events/          # Feature Module: Reactive gameplay event dispatcher
│   ├── module-math/            # Feature Module: Vector3, Vector2, Color3, Signal, Instance classes
│   ├── module-storage/         # Feature Module: World, Player, Session persistent datastores
│   └── module-network/         # Feature Module: Rocket-style RemoteEvent network packet routing
└── neoforge-platform/          # NeoForge specific implementations of PAL and launch events
```

### 1.1 `common-api` (Platform Abstraction Layer - PAL)
Contains only pure Java 21 interfaces and records. It has **zero dependencies** on Minecraft, NeoForge, or any Lua engine library (Cobalt/LuaJ).
- **Abstract Game Objects**: `IItem`, `IBlock`, `IEntity`, `IRecipe` wrapping raw game classes.
- **PAL Interface**: `IPlatformHelper` to lookup items/tags, query registries, read storage paths, and send bidirectional network payloads.
- **Decoupled VM**: `ILuaValue`, `ILuaTable`, `ILuaFunction`, `ILuaEngine` interfaces.
- **Annotations**: `@LuaDoc` metadata annotations.

### 1.2 `core-engine` (LTVM Cobalt Wrapper)
Implements the abstract VM interfaces using Cobalt. It manages the Lua execution lifecycle, asynchronous logging, custom compile/runtime error tracebacks, and LSP stubs. It also executes a pure Lua bootstrap script on startup to define Roblox core classes (`task`, `Signal`, `RemoteEvent`, `Players`).

### 1.3 Feature Modules (`modules/*`)
Business logic features (Recipes, Entities, Interaction, Events, Math, Storage, Network). By compiling only against `common-api` and `core-engine`, these modules are **completely engine-agnostic and platform-agnostic**. They register Lua bindings by interacting with `ILuaTable` and `ILuaFunction` callback interfaces rather than importing Cobalt or Minecraft classes.

### 1.4 `neoforge-platform` (Launcher Entrypoint)
Houses concrete NeoForge code. It implements `IPlatformHelper` and concrete game object wrappers (`NeoForgeItem` -> `IItem`). When Minecraft data reload starts, it initializes the engine, registers bindings, and applies modifications back to Minecraft's `RecipeManager`.

---

## 💾 2. Roblox-Style Storage APIs

LTVM provides persistent and temporary datastores resembling Roblox's DataStoreService. Retrieved via `game:GetService("WorldStorage")` or globally:

*   **`WorldStorage`**: Persists global world state to JSON files (`world_storage.json`) inside the active world save folder.
*   **`PlayerStorage`**: Persists state per player to `player_storage.json` inside the world save folder.
*   **`SessionStorage`**: Keeps data in-memory for the current session (cleared on server shutdown or script reload).

### API Usage
```lua
-- WorldStorage persistent state
local playCount = WorldStorage:GetAsync("play_count") or 0
WorldStorage:SetAsync("play_count", playCount + 1)
print("Updated World persistent play_count to: " .. tostring(WorldStorage:GetAsync("play_count")))

-- PlayerStorage persistent state per player
local pStore = PlayerStorage:GetPlayerStorage("d3b07384-d113-4956-aab3-8e4d28d108d0")
local xp = pStore:GetAsync("experience") or 100
pStore:SetAsync("experience", xp + 50)
```

---

## 📡 3. Rocket-Style Network Packet Routing

LTVM incorporates a Roblox-like bidirectional network messaging framework using `RemoteEvent` instances:

*   **Server Scripts (Server-to-Client / Broadcast)**:
    *   `remoteEvent:FireClient(player, ...)`: Sends a packet payload to a specific player.
    *   `remoteEvent:FireAllClients(...)`: Broadcasts a packet payload to all connected clients.
    *   `remoteEvent.OnServerEvent:Connect(function(player, ...))`: Listens for incoming client packet payloads.
*   **Client Scripts (Client-to-Server)**:
    *   `remoteEvent:FireServer(...)`: Sends a packet payload from the client to the server.
    *   `remoteEvent.OnClientEvent:Connect(function(...))`: Listens for incoming server packet payloads.
*   **`Players.LocalPlayer`**: Exposes client-side player reference on the client-side environment.

### API Usage
```lua
local NetworkService = Mod:GetService("NetworkService")
local scoreEvent = NetworkService:GetOrCreateRemoteEvent("ScoreUpdate")

-- [Server Side] Listen to client events
scoreEvent.OnServerEvent:Connect(function(player, score, reason)
    print("Server received score update from " .. player.Name .. ": " .. tostring(score))
end)

-- [Client Side] Fire events to server
scoreEvent:FireServer(9999, "Completed Quest")
```

---

## 🛠️ 4. Dynamic EmmyLua Stub Generator

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

## 🚀 5. How to Extend LTVM (Adding a New Service)

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

# Addon Developer Guide for LuaTweaker

> **Target:** Minecraft 1.21.1 (NeoForge & Fabric) | **Java:** 21 | **Runtime:** luava 0.3.0 (`org.luava.*`)

This guide is for Java mod developers who want to expose APIs, callbacks and events to Lua scripts.
LuaTweaker keeps a thin SPI: addons bind against `ILuaEngine` and `LuaBinder`. Do not import
`org.luava.*`, `net.minecraft.*`, `net.neoforged.*` or `net.fabricmc.*` outside the platform layer.

---

## 1. Dependency

Compile against `common-api` (the SPI and annotations) and `core-engine` (for `LuaBinder`). The
published group/version live in the project's `gradle.properties` (`com.luatweaker` / `1.0.0`);
adjust the artifact name to however you publish your build:

```groovy
repositories {
    mavenLocal()
}

dependencies {
    compileOnly "com.luatweaker:LuaTweaker:1.0.0"
}
```

---

## 2. Declare the API (single source of truth)

Declare the interface in your addon's API module. `LuaBinder`, the generated EmmyLua stubs and the
docs all read the same annotations:

```java
package com.example.myaddon.api;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.vm.ILuaFunction;

@LuaDoc(description = "Example addon API.")
public interface IMyAddonApi {
    @LuaDoc(description = "Registers a machine.", params = {"name: string", "power: number"})
    void createMachine(String name, @LuaDefault("1.0") double power);

    @LuaDoc(description = "Runs a Lua callback with the machine id.",
            params = {"id: string", "callback: function"})
    void executeCallback(String id, ILuaFunction callback);
}
```

`@LuaDefault` makes a trailing argument optional. Callbacks use the SPI type `ILuaFunction`;
addons must never depend on a concrete Lua runtime class.

---

## 3. Implement and bind

```java
package com.example.myaddon;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaValue;
import com.example.myaddon.api.IMyAddonApi;

public final class MyAddonApiImpl implements IMyAddonApi {
    private final ILuaEngine engine;

    public MyAddonApiImpl(ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public void createMachine(String name, double power) {
        // Add your Java logic here.
    }

    @Override
    public void executeCallback(String id, ILuaFunction callback) {
        if (callback == null) {
            return;
        }
        try {
            callback.invoke(new ILuaValue[] { engine.toLuaValue(id) });
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.RUNTIME_ERROR,
                    "MyAddon callback for " + id + " failed: " + e.getMessage());
        }
    }
}
```

Register it once per engine:

```java
LuaBinder.bind(engine, "MyAddon", new MyAddonApiImpl(engine), IMyAddonApi.class);
```

> **No fork required (plugin runtime hook).** Do NOT patch `LuaServiceBootstrap`,
> `LuaTweakerCommon`, `settings.gradle` or the loader `build.gradle` files. Instead wrap the
> binding above in an `ILuaTweakerPlugin` and expose it once - the engine discovers it on
> every reload (see section 6):

```java
package com.example.myaddon;

import com.luatweaker.api.plugin.ILuaTweakerPlugin;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import com.example.myaddon.api.IMyAddonApi;
import org.jetbrains.annotations.NotNull;
import java.util.Map;

public final class MyAddonPlugin implements ILuaTweakerPlugin {
    @Override public @NotNull String pluginId() { return "myaddon"; }
    @Override public @NotNull String pluginVersion() { return "1.0.0"; }

    @Override
    public void registerServices(@NotNull ILuaEngine engine) {
        // LOW-LEVEL primitives only: one method per bridge call, logic stays in Lua.
        LuaBinder.bind(engine, "MyAddon", new MyAddonApiImpl(engine), IMyAddonApi.class);
    }

    @Override
    public @NotNull Map<String, String[]> requireAliases() {
        return Map.of("LuaTweaker.MyAddon", new String[]{"MyAddon"});
    }

    @Override
    public @NotNull Map<String, Class<?>> stubServices() {
        return Map.of("MyAddon", IMyAddonApi.class);
    }
}
```

`LuaBinder.bind` creates a table with both `camelCase` and `PascalCase` method names, sets the global
`MyAddon` and registers that same table as the service `MyAddon`. Scripts use either form:

```lua
MyAddon:createMachine("super_compressor", 5000.0)
local sameService = Mod:GetService("MyAddon")
```

If you only need a raw Java object (no generated method table), use the dynamic bridge instead:

```java
engine.registerGlobal("myaddon", engine.wrapDynamicJavaObject(apiInstance));
engine.registerService("MyAddonService", apiInstance);
```

---

## 4. Firing custom events

Java side — look up the shared event service and post a payload table:

```java
IEventService events = (IEventService) LuaServiceRegistry.get("Events");
if (events != null) {
    ILuaTable payload = engine.createTable();
    payload.rawset("Status", engine.wrapString("ok"));
    events.post("MyAddon.MachineProcessed", payload);
}
```

Lua side:

```lua
local Events = require("LuaTweaker.Events")
Events:Listen("MyAddon.MachineProcessed", function(payload)
    print(payload.Status)
end)
```

---

## 5. Logging and stubs

Use `LuaTweakerLog.get()` with a `LogStage` value; catch exceptions with context and never discard
them silently. `@LuaDoc` annotations on the interface are read by the engine's stub generator, so
addons get EmmyLua autocomplete without hand-written stub files.

---

## 6. Publishing a plugin (two supported paths, no fork)

The engine calls every discovered plugin at the end of `LuaServiceBootstrap.registerAllServices`
(on every `/lt reload`), after all built-in services. A plugin that throws only fails itself —
the reload continues and the error is logged with the plugin id.

**Path A - separate NeoForge or Fabric mod (recommended for Minecraft API access).**
Your mod depends on LuaTweaker and registers once from its own mod constructor / initializer:

```java
// NeoForge (@Mod)
public MyAddonMod(IEventBus bus, ModContainer container) {
    LuaTweakerPluginRegistry.register(new MyAddonPlugin());
}

// Fabric (ModInitializer)
@Override
public void onInitialize() {
    LuaTweakerPluginRegistry.register(new MyAddonPlugin());
}
```

**Path B - plain jar via ServiceLoader (no loader mod needed).**
Ship a jar containing the plugin class plus:

```
META-INF/services/com.luatweaker.api.plugin.ILuaTweakerPlugin
```

with the single line:

```
com.example.myaddon.MyAddonPlugin
```

Rules:

- `pluginId` must be stable and unique; explicit `register()` wins over ServiceLoader on conflict.
- `loadOrder` (default `1000`) controls order; lower registers first. Built-ins always run before plugins.
- Expose LOW-LEVEL primitives only (`Raycast`, `ApplyDamage`, `SpawnParticle`, ...). Never ship a
  high-level `castMagicSpell()` that hides gameplay logic from Lua (AGENTS.md anti-KubeJS rule).
- Never import `net.minecraft.*` / `net.neoforged.*` outside the platform layer — go through
  `Platform.getX()`. `common-api` plugins compile against `common-api` + `core-engine` only.
- A ready-to-copy Gradle project lives in `templates/java-plugin/`.
  A Lua-only mod template (manifest + `main.lua` + `optional_dependencies` + `ExportAPI` example)
  lives in `templates/luamod/`.

---

## 7. Lua-only mods: manifest fields for safe interop

```json
{
  "id": "my_mod",
  "environment": "universal",
  "dependencies": ["required_base"],
  "optional_dependencies": ["present_loads_first", "missing_is_ignored"],
  "permissions": []
}
```

- `dependencies`: hard requirement — the mod is skipped with a `/lt doctor` error when absent.
- `optional_dependencies`: soft requirement — loads after the listed mod when present,
  warns and continues when absent.
- Cross-mod helpers on the `mod` table: `mod:HasMod("other")`,
  `mod:GetModVersion("other")`, `mod:ExportAPI("ApiName", apiTable)`,
  `mod:ImportAPI("other_mod", "ApiName")`.
- Per-mod file access: `mod:Files()` (read-only own folder/zip) and
  `mod:Exports()` (writable `luatweaker/exports/<mod_id>/`). Paths are relative
  and sandboxed; see `docs/LUA_MOD_SYSTEM.md` section 6.

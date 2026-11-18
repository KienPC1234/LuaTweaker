# Addon Developer Guide for LuaTweaker

> **Target:** Minecraft 1.21.1 (NeoForge) | **Java:** 21 | **Runtime:** luava 0.2.5 (`org.luava.*`)

This guide is for Java mod developers who want to expose APIs, callbacks and events to Lua scripts.
LuaTweaker keeps a thin SPI: addons bind against `ILuaEngine` and `LuaBinder`. Do not import
`org.luava.*`, `net.minecraft.*` or `net.neoforged.*` outside the platform layer.

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

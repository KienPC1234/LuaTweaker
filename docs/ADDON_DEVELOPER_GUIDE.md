# 🔌 Addon Developer Guide for LuaTweaker (`kien.LuaTweaker`)

> **Target Version:** Minecraft 1.21.1 (NeoForge) | **Java:** 21 | **Lua Engine:** LuaJ 3.0.1  
> This guide is for third-party mod developers (e.g., JEI, Mekanism, Create, Botania, Blood Magic) who want to expose custom Java APIs, event hooks, global variables, and type stubs into LuaTweaker.

---

## 🏗️ 1. Gradle Dependency Configuration

Add `LuaTweaker` as a compile-only or implementation dependency in your addon mod's `build.gradle`:

```groovy
repositories {
    mavenLocal()
}

dependencies {
    // Compile against LuaTweaker API
    compileOnly "kien.luatweaker:LuaTweaker:1.0.0"
}
```

---

## ⚡ 2. Creating an Addon Plugin (`@LuaTweakerPlugin`)

LuaTweaker uses automatic runtime annotation scanning. To create an addon plugin:
1. Implement `kien.luatweaker.addon.ILuaTweakerPlugin`.
2. Annotate your class with `@LuaTweakerPlugin`.
3. Provide a public no-arg constructor.

### Example Addon Class
```java
package com.example.myaddon;

import kien.luatweaker.addon.ILuaTweakerPlugin;
import kien.luatweaker.addon.LuaTweakerPlugin;
import kien.luatweaker.core.LuaEngine;
import kien.luatweaker.core.ScriptEnvironment;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

@LuaTweakerPlugin(value = "myaddonmodid") // Optional target mod dependency
public class MyModLuaTweakerPlugin implements ILuaTweakerPlugin {

    private final MyAddonAPI apiInstance = new MyAddonAPI();

    @Override
    public String getPluginId() {
        return "myaddon_luatweaker";
    }

    @Override
    public void onRegisterGlobals(LuaEngine engine) {
        // Expose your mod's Java API under the global variable `myaddon`
        engine.setGlobal("myaddon", CoerceJavaToLua.coerce(apiInstance));
    }

    @Override
    public void onRegisterServices(kien.luatweaker.core.LuaServicesLib services) {
        // Register custom addon service accessible via `game:GetService("MyAddon")`
        services.registerService("MyAddon", CoerceJavaToLua.coerce(apiInstance));
    }

    @Override
    public void onRegisterEvents(kien.luatweaker.api.event.EventBusAPI eventBus) {
        // Post custom addon events to Lua script listeners
        eventBus.post("myaddon.loaded", java.util.Map.of("status", "ok"));
    }

    @Override
    public void onPlatformSetup(kien.luatweaker.platform.ILuaTweakerPlatform platform) {
        // Access active multi-loader platform info (NeoForge, Forge, Fabric)
        System.out.println("Active Platform: " + platform.getPlatformName() + " (MC " + platform.getMinecraftVersion() + ")");
    }

    @Override
    public void onRegisterStubs(StringBuilder stubs) {
        // Inject custom LLS type stubs for VS Code / IntelliJ autocompletion
        stubs.append("""
            ---@class MyAddonAPI
            local MyAddonAPI = {}
            ---@param name string
            ---@param power number
            function MyAddonAPI:createMachine(name, power) end

            ---@type MyAddonAPI
            myaddon = {}
            """);
    }

    @Override
    public void onReload(ScriptEnvironment.Stage stage) {
        // Reset dynamic state when /lt reload is executed
        apiInstance.clear();
    }
}
```

---

## 🌐 3. Multi-Loader Platform Abstraction (`ILuaTweakerPlatform`)

LuaTweaker features a platform-agnostic core architecture (`LuaTweaker-Core`) separated cleanly from platform loaders. Third-party addons can query active platform details seamlessly:

```java
import kien.luatweaker.platform.ILuaTweakerPlatform;
import kien.luatweaker.platform.LuaTweakerPlatformManager;

ILuaTweakerPlatform platform = LuaTweakerPlatformManager.getPlatform();

String loaderName = platform.getPlatformName(); // "NeoForge", "Forge", or "Fabric"
boolean isClient  = platform.isClientEnvironment();
boolean isModLoaded = platform.isModLoaded("mekanism");
```
```

---

## 🎯 3. Exposing Custom Java APIs to Lua

Your API classes can expose standard public Java methods that take primitives, Strings, lists, maps, or `LuaFunction` callbacks:

```java
package com.example.myaddon;

import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import java.util.HashMap;
import java.util.Map;

public class MyAddonAPI {
    private final Map<String, Double> machines = new HashMap<>();

    public void createMachine(String name, double power) {
        machines.put(name, power);
        System.out.println("Registered custom machine: " + name + " with power: " + power);
    }

    public void executeCallback(String id, LuaFunction callback) {
        if (callback != null) {
            callback.call(CoerceJavaToLua.coerce(id));
        }
    }

    public void clear() {
        machines.clear();
    }
}
```

---

## 📜 4. How Modpack Creators Use Your Addon

Once your addon mod is loaded, modpack creators can instantly use your global binding in `.lua` scripts:

```lua
-- server_scripts/my_addon_script.lua
myaddon:createMachine("super_compressor", 5000.0)

myaddon:executeCallback("machine_1", function(id)
    print("Machine callback executed for: " .. id)
end)
```

---

## 🔄 5. Firing Custom Events to Lua

You can dispatch dynamic custom events to Lua listeners using LuaTweaker's `EventBusAPI`:

```java
import kien.luatweaker.neoforge.LuaTweakerMod;

// Fire a custom event to all Lua script listeners
LuaTweakerMod.getEventBus().post("myaddon.machine_processed", eventData);
```

Lua scripts subscribe to your event using:
```lua
events:listen("myaddon.machine_processed", function(data)
    print("Machine processed event received!")
end)
```

# Third-Party Mod Integrations Architecture

This document describes the unified third-party mod integration architecture in LuaTweaker, covering contract isolation, platform lifecycle management, and Lua runtime interaction.

---

## 1. Architectural Layers

LuaTweaker enforces strict separation between integration contracts, platform-side implementations, and core scripting logic:

```
+-------------------------------------------------------------------+
|                   Lua Scripts / LuaMods                           |
|  - manifest.json "integrations" section                           |
|  - mod:HasIntegration(targetModId)                                |
|  - Recipe.RegisterCustom(recipeType, id, data)                    |
|  - block.GetEnergy(), block.InsertEnergy(), block.GetFluid()...   |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|               modules:module-integration-api                      |
|  - Pure Java specifications and service contracts                 |
|  - Zero runtime dependencies on Minecraft, NeoForge, or 3rd mods  |
|  - GeoModelSpec (GeckoLib model/texture/animation definitions)     |
|  - IJeiRecipeService (JEI custom category and recipe contracts)   |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                  neoforge-platform (Integration PAL)              |
|  com.luatweaker.platform.integration:                             |
|  - IPlatformIntegration (contract with modId, availability, hooks)|
|  - IntegrationManager (lifecycle dispatch, status logging)        |
|  - geckolib/ (GeckoLibAnimationBridge, renderers, models)         |
|  - jei/ (CustomJeiRegistry, LuaTweakerJeiPlugin)                  |
|  - GenericPlatformIntegration (extensible soft-mod wrapper)       |
+-------------------------------------------------------------------+
```

---

## 2. Platform Integration SPI (`IPlatformIntegration`)

Every external platform mod integration implements `IPlatformIntegration`:

```java
package com.luatweaker.platform.integration;

import com.luatweaker.api.vm.ILuaEngine;
import net.neoforged.bus.api.IEventBus;
import org.jetbrains.annotations.NotNull;

public interface IPlatformIntegration {
    @NotNull String modId();
    @NotNull String displayName();
    boolean isAvailable();
    default void onModBusSetup(@NotNull IEventBus modBus) {}
    default void onEngineSetup(@NotNull ILuaEngine engine) {}
    default void onServerStopping() {}
}
```

### Lifecycle Dispatch via `IntegrationManager`

All platform integrations register with `IntegrationManager` during mod bootstrap:

```java
IntegrationManager.register(new GeckoLibIntegration());
IntegrationManager.register(new JeiIntegration());
IntegrationManager.initModBus(modEventBus);
IntegrationManager.logStatus();
```

When an active engine is initialized or reloaded, `IntegrationManager.initEngine(engine)` dispatches to all currently available integrations.

---

## 3. Supported Integrations

| Mod ID | Integration Class | Availability | Capabilities |
|---|---|---|---|
| `geckolib` | `GeckoLibIntegration` | Bundled (Jar-in-Jar) | Animated skeletal models, items, entities, blocks |
| `jei` | `JeiIntegration` | Soft dependency (`compileOnly`) | Custom JEI category tabs, custom recipe views |
| Generic | `GenericPlatformIntegration` | Dynamic | Presence checks and mod lifecycle binding |

### Adding a New Platform Integration (e.g. Mekanism, Create, Curios)

To add integration for a new mod:
1. If the integration requires pure Java data contracts, declare them in `modules/module-integration-api`.
2. Implement `IPlatformIntegration` under `neoforge-platform/src/main/java/com/luatweaker/platform/integration/<modid>/`.
3. Guard all third-party mod API calls behind `isAvailable()`.
4. Register the integration in `LuaTweakerMod` constructor or through SPI.

---

## 4. Declaring Integrations in LuaMods

LuaMods declare soft integrations directly inside `manifest.json`:

```json
{
  "id": "my_tech_addon",
  "name": "My Tech Addon",
  "version": "1.0.0",
  "author": "Developer",
  "integrations": {
    "jei": "integrations/jei.lua",
    "mekanism": {
      "entry": "integrations/mekanism.lua",
      "min_version": "10.7.0"
    }
  }
}
```

When the target platform mod is installed and satisfies the version bounds, the engine executes the specified entry script automatically.

### Lua Query Methods

```lua
-- Check if a declared integration was loaded successfully
if mod:HasIntegration("mekanism") then
    print("Mekanism integration is active")
end

-- Inspect integration status
local status = mod:GetIntegrationStatus("mekanism")
print("Status: " .. tostring(status))
```

---

## 5. Universal Capabilities & Custom Recipes

For tech mods exposing standard energy, items, or fluids (Mekanism, Create, AE2, Thermal, EnderIO):

### Low-Level Block Capability Methods
```lua
local block = World:GetBlock(x, y, z)

-- Energy transfer
local energy = block:GetEnergy("UP")
local inserted = block:InsertEnergy(1000, "DOWN")

-- Fluid transfer
local filled = block:FillFluid("minecraft:water", 1000, "NORTH")
local drained = block:DrainFluid("minecraft:lava", 500, "SOUTH")

-- Item slot inspection
local slots = block:GetItemSlots("WEST")
```

### Generic Custom Recipes
```lua
Recipe.RegisterCustom("mekanism:crushing", "mymod:crush_copper", {
    input = { count = 1, item = "minecraft:copper_block" },
    output = { count = 9, item = "minecraft:copper_ingot" }
})
```

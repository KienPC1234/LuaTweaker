# Generic Data-Driven Recipes & Universal Block Capabilities

This document details the standardized service architecture, the data-driven recipe engine (allowing custom recipes for any mod such as Mekanism, Create, Farmer's Delight), and the universal low-level block capability transfer API.

---

## 1. Unified Service SPI & Auto-Binding

In previous versions, registering a new engine service required manual declarations across multiple distinct files (`common-api` interface, binding class, `LuaServiceBootstrap`, `NativeModuleResolver`, `StubCatalog`, and `LuaTweakerMod.generateStubs`).

With the unified Service SPI:
- `LuaBinder.bind(engine, name, impl, apiClass, aliases...)` automatically handles:
  1. Method reflection, argument conversion, and colon/dot call handling.
  2. Registration in engine services (`engine.registerService(...)`).
  3. Exposure to Lua global environment (`_G.<name>` and `_G.<alias>`).
  4. Automatic registration in `StubCatalog` for EmmyLua autocompletion.
  5. Automatic registration in `NativeModuleResolver` for `require("LuaTweaker.<Name>")`, `require("<Name>")`, and all aliases.

Any newly added service or third-party plugin bound via `LuaBinder.bind` is immediately fully wired across the entire VM without touching resolver or stub registration classes.

---

## 2. Generic Data-Driven Recipes (`Recipe.Custom` & `Recipe.RegisterCustom`)

Instead of hardcoding dedicated Java recipe builders for each third-party mod machine (such as Mekanism's 16+ machines or Create's processing recipes), LuaTweaker provides low-level primitive APIs to inject data-driven recipes directly into Minecraft's virtual datapack.

### Direct Registration (`Recipe.RegisterCustom`)

```lua
-- Register a Mekanism Crushing recipe
Recipe.RegisterCustom("mekanism:crushing", "mymod:crush_copper", {
    input = { count = 1, item = "minecraft:copper_ingot" },
    output = { count = 1, item = "mekanism:dust_copper" }
})

-- Register a Create Mixing recipe
Recipe.RegisterCustom("create:mixing", "mymod:mix_potion", {
    ingredients = {
        { item = "minecraft:water_bucket" },
        { item = "minecraft:sweet_berries" }
    },
    results = {
        { item = "mymod:berry_juice" }
    },
    heatRequirement = "heated"
})
```

### Builder Pattern (`Recipe.Custom`)

```lua
Recipe.Custom("mekanism:enriching", "mymod:enrich_iron")
    :Data({
        input = { count = 1, item = "minecraft:iron_ore" },
        output = { count = 2, item = "mekanism:dust_iron" }
    })
    :Register()
```

### Technical Details
- If the `"type"` field is omitted from the table, it is automatically injected from the `recipeType` parameter.
- The recipe is serialized into standard JSON via `JsonServiceImpl.encodeJson` and stored in RAM within `LuaTweakerVirtualPackResources`.
- When datapacks load or when `/lt reload data` runs, Minecraft and the owning mod parse the recipe with native serializers.

---

## 3. Universal Block Capabilities (Energy, Items, Fluids)

Block instances (`IInteractableBlock` obtained via `World:GetBlock(x, y, z)` or the `self` block parameter in machine callbacks) expose direct low-level capability methods communicating with standard NeoForge Capabilities (`IEnergyStorage`, `IItemHandler`, `IFluidHandler`).

### Energy Storage (RF / FE)

```lua
local block = World:GetBlock(x, y, z)

-- Query energy on a specific face (UP, DOWN, NORTH, SOUTH, WEST, EAST) or unsided
local energy = block:GetEnergy("UP")
if energy then
    print("Stored:", energy.Stored, "/", energy.Max)
    print("Can extract:", energy.CanExtract, "Can receive:", energy.CanReceive)
end

-- Insert energy into the block
local accepted = block:InsertEnergy(500, "DOWN")

-- Extract energy from the block
local extracted = block:ExtractEnergy(200, "UP")
```

### Item Handlers

```lua
local block = World:GetBlock(x, y, z)

-- Snapshot occupied slots
local slots = block:GetItemSlots("NORTH")
if slots then
    for _, slot in ipairs(slots) do
        print("Slot " .. slot.Slot .. ": " .. slot.Id .. " x " .. slot.Count)
    end
end

-- Insert items into the first available slot accepting them
local inserted = block:InsertItem("minecraft:coal", 16, "UP")

-- Extract items from a specific slot index
local removed = block:ExtractItem(0, 8, "DOWN")
if removed then
    print("Removed " .. removed.Count .. " of " .. removed.Id)
end
```

### Fluid Handlers

```lua
local block = World:GetBlock(x, y, z)

-- Inspect fluid tank 0
local fluid = block:GetFluid("UP")
if fluid then
    print("Fluid:", fluid.Id, "Amount:", fluid.Amount, "/", fluid.Capacity)
end

-- Fill fluid into the tank
local filled = block:FillFluid("minecraft:water", 1000, "UP")

-- Drain fluid (pass "*" or omit to drain the first non-empty tank)
local drained = block:DrainFluid(500, "DOWN")
```

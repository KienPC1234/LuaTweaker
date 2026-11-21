# 🎨 LuaTweaker Dear ImGui GUI Editor & JEI Recipe Integration Guide

Technical guide for using the in-game **Dear ImGui** visual interface designer (`/luatweaker gui`) and creating custom recipe categories with **JEI (Just Enough Items)** on Minecraft 1.21.1 NeoForge.

---

## 1. Overview & Architecture

LuaTweaker embeds **Dear ImGui** via `imgui-java` directly into the Minecraft LWJGL 3 / OpenGL render pipeline. This provides a desktop-grade, multi-window visual editor allowing developers to visually compose, preview, and export container layouts and JEI recipe category screens in real-time.

| Feature | Container Mode | JEI Recipe Category Mode |
|---|---|---|
| **Primary Command** | `/lt gui [name] [w] [h]` | `/lt gui jei [category_id] [w] [h]` |
| **Default Canvas Size** | 176 x 166 px | 150 x 65 px |
| **Ghost Player Inventory** | Yes (3x9 storage + 9 hotbar slots) | Hidden (recipe tabs only) |
| **Slot Roles** | `INPUT`, `OUTPUT` (auto lock), `FUEL`, `LOCKED` | `INPUT`, `OUTPUT`, `CATALYST` |
| **State Binding** | Machine energy, fluid, and custom progress keys | Crafting duration, XP reward, catalysts |
| **Export Target** | `Content.NewBlock(...)` with `:OnTick` logic | `Recipe.NewJeiCategory(...)` and `Recipe.AddJeiRecipe(...)` |

---

## 2. In-Game Commands

Launch the editor directly from the in-game chat prompt (requires a player entity):

```
-- 1. Standard Container Block GUI
/luatweaker gui [block_name] [width] [height]
-- Examples:
/lt gui crystal_furnace
/lt gui advanced_assembler 194 180

-- 2. JEI Recipe Category GUI
/luatweaker gui jei [category_id] [width] [height]
-- Examples:
/lt gui jei crystal_infusion
/lt gui jei ore_crusher 160 70
```

> **Mode Toggle:** You can switch between **Container Mode** and **JEI Recipe Mode** at any time without closing the screen by selecting the **Mode** dropdown in the top Menu Bar.

---

## 3. Workspace Windows & Controls

The editor provides four dockable panels designed for rapid UI composition:

### A. Toolbox & Hierarchy Panel (Left)
- **Widget Palette:**
  - `Slot`: 18x18 px standard inventory slot.
  - `Progress Arrow`: 24x17 px furnace-style arrow supporting 4-way rotation (`0°`, `90°`, `180°`, `270°`).
  - `Furnace Flame`: 14x14 px burn indicator with live animated flickering fire.
  - `Energy Bar`: 14x50 px Forge Energy / Redstone Flux meter with level ticks.
  - `Fluid Tank`: 16x50 px liquid gauge for water, lava, and custom fluids.
  - `Animated Sprite`: Cycles texture sheets at configurable frame rates.
  - `Button`: Interactive push button firing server-side payload signals.
  - `Label`: Styled static or dynamic text.
- **Multi-Slot Generators:**
  - `3x3 Grid`: Generates a 9-slot crafting matrix.
  - `2x2 Grid`: Generates a 4-slot grid.
  - `1x4 Row` / `Row 1x3`: Generates linear slot arrays.
- **Hierarchy Tree:**
  - Displays all canvas widgets by z-index.
  - Click to select, duplicate (`Duplicate`), rotate (`Rotate R`), or delete (`Delete`).

### B. Canvas Viewport (Center)
- **Pixel-Accurate Vanilla Styling:**
  - Panel body rendered in `#C6C6C6` with beveled 3D frame (`#FFFFFF` top/left, `#373737` bottom/right).
  - Recessed 3D slot cavities (`#8B8B8B` with inner shadow `#2B2B2B`).
- **Interactive Drag & Drop:**
  - Left-click and drag any widget directly on the canvas.
  - Automatic grid alignment: toggle between `1px` (free), `4px`, `9px`, and `18px` (exact 1-slot pitch).
- **Ghost Player Inventory:**
  - Displays faint outlines for the 27 main inventory slots and 9 hotbar slots to prevent widget overlaps.
- **Live Preview Animations:**
  - Crafting arrows animate filling from empty to full.
  - Furnace flames flicker dynamically.
  - Energy and fluid bars render current fill levels.

### C. Properties Inspector (Right)
- **Transform & Geometry:**
  - Direct numeric dragging for X, Y, Width, and Height.
  - 4-way rotation buttons: `0°`, `90°`, `180°`, `270°` (Shortcut key: `R`).
- **Container Slot State-Binding:**
  - **Role:** `INPUT`, `OUTPUT` (automatically sets `:LockSlot`), `FUEL`, `LOCKED`.
  - **Preview Item:** Enter registry IDs (e.g., `minecraft:raw_iron`, `minecraft:iron_ingot`) to render sample ghost items inside slots.
- **Bar State-Binding:**
  - **Source Type:** `progress`, `energy`, `fluid`, or custom keys.
  - **Data Keys:** Configure `Current Key` (e.g. `Progress`, `Energy`) and `Max Key` (e.g. `MaxEnergy`).
- **JEI Metadata (JEI Mode only):**
  - Category Title (e.g. `"Crystal Infusion"`).
  - Icon registry ID (e.g. `"luatweaker:crystal_infuser"`).
  - Catalyst workstation (e.g. `"luatweaker:crystal_infuser"`).
  - Recipe duration (`cookTime` in ticks) and experience reward (`xp`).

### D. Lua Code Exporter (Bottom)
- Real-time syntax generator matching your canvas layout.
- **Copy Lua Code to Clipboard:** Instantly places ready-to-run Lua code onto the OS clipboard.
- **Save to File:** Automatically writes code to `luamods/exported_gui.lua`.

---

## 4. JEI Recipe Integration

All recipes registered via the `Recipe` module automatically integrate into JEI without requiring extra addons.

### A. Vanilla Workstation Categories

```lua
local Recipe = require("LuaTweaker.Recipe")

-- Shaped Crafting (Appears in JEI Crafting Table tab)
Recipe.Shaped("luatweaker:crystal_blade")
    :Pattern(
        " C ",
        " C ",
        " S "
    )
    :Key("C", "minecraft:amethyst_shard")
    :Key("S", "minecraft:stick")
    :Output("luatweaker:crystal_blade", 1)
    :Register()

-- Blasting (Appears in JEI Blast Furnace tab)
Recipe.Blasting("luatweaker:smelt_crystal_ore")
    :Input("luatweaker:crystal_ore")
    :Output("luatweaker:crystal_ingot", 1)
    :Exp(1.5)
    :CookTime(100) -- 5 seconds
    :Register()

-- Anvil Combination (Appears in JEI Anvil tab)
Recipe.Anvil("luatweaker:reinforce_crystal")
    :Left("luatweaker:crystal_blade")
    :Right("minecraft:netherite_ingot")
    :Output("luatweaker:reinforced_crystal_blade", 1)
    :Cost(5)
    :Register()
```

### B. Custom JEI Recipe Categories (New in 1.21.1)

Define independent recipe categories for custom machines with custom slots, progress arrows, and catalyst workstations:

```lua
local Recipe = require("LuaTweaker.Recipe")

-- 1. Register Custom JEI Recipe Category Layout
Recipe.NewJeiCategory("luatweaker:crystal_infusion")
    :Title("Crystal Infusion")
    :Icon("luatweaker:crystal_infuser")
    :Size(150, 65)
    :Catalyst("luatweaker:crystal_infuser")
    :Slot(20, 20, true)   -- Recipe Input Slot
    :Slot(100, 20, false) -- Recipe Output Slot
    :Arrow(55, 20)        -- Animated Progress Arrow
    :Flame(21, 42)        -- Animated Burn Flame (optional)
    :Register()

-- 2. Add Recipes to the Custom Category
Recipe.AddJeiRecipe("luatweaker:crystal_infusion", {
    inputs = { "luatweaker:crystal_ore" },
    outputs = { "luatweaker:crystal_ingot" },
    cookTime = 100, -- 5 seconds
    xp = 1.5
})
```

### C. Automatic Item Information Tabs

Adding `:Tooltip("...")` to any custom item or block registers an **Information** tab in JEI. Players hovering over the item and pressing `U` or `R` will see usage documentation:

```lua
local Content = require("LuaTweaker.Content")

Content.NewItem("crystal_wrench")
    :DisplayName("Crystal Wrench")
    :Tooltip("Rotates machines and configures face input/output sides.")
    :Tooltip("Sneak + Right-Click to dismantle machines instantly.")
    :Register()
```

---

## 5. Complete Machine & GUI Example

Exported code from `/lt gui crystal_furnace` wired into `src/startup/content.lua`:

```lua
local Content = require("LuaTweaker.Content")

Content.NewBlock("crystal_furnace")
    :DisplayName("Crystal Furnace")
    :Container(1, 2, "drop")
    :SlotPosition(0, 56, 35)   -- Input Slot
    :SlotPosition(1, 116, 35)  -- Output Slot
    :LockSlot(1, true)         -- Output Slot is read-only
    :GuiBar("progress", 79, 34, 24, 17, "progress")
    :GuiBar("energy", 18, 18, 14, 50, "energy")
    :EnergyStorage(10000, 500, 0)
    :OnTick(function(data)
        local World = require("LuaTweaker.World")
        local pos = { X = data.X, Y = data.Y, Z = data.Z }
        local inv = World:GetContainer(pos)
        if not inv then return end

        local input = inv:GetSlot(0)
        if input and input.Id == "luatweaker:crystal_ore" and (data.Energy or 0) >= 20 then
            data.Energy = data.Energy - 20
            data.Progress = (data.Progress or 0) + 0.05

            if data.Progress >= 1.0 then
                data.Progress = 0
                inv:ExtractItem(0, 1)
                inv:InsertItem(1, "luatweaker:crystal_ingot", 1)
            end
        else
            data.Progress = 0
        end
    end)
    :Register()
```

# GeckoLib Custom Model & Animation System

LuaTweaker embeds GeckoLib 4 (`software.bernie.geckolib`) directly into the NeoForge runtime via Jar-in-Jar (`jarJar`). This architecture provides low-level, primitive scripting hooks allowing Lua mod developers to register custom 3D animated models and trigger animations dynamically at runtime for **Items**, **Blocks**, and **Entities**.

---

## 1. Asset Directory Structure

All GeckoLib models, textures, and animations reside within standard LuaMod asset paths:

```
luamods/<mod_id>/
  assets/
    <namespace>/
      geo/
        item/<name>.geo.json
        block/<name>.geo.json
        entity/<name>.geo.json
      animations/
        item/<name>.animation.json
        block/<name>.animation.json
        entity/<name>.animation.json
      textures/
        item/<name>.png
        block/<name>.png
        entity/<name>.png
```

### Path Resolution Rules
Paths passed to `:GeoModel(...)` accept either:
1. **Explicit ResourceLocation**: `"luatweaker:geo/item/crystal_scythe.geo.json"`
2. **Shorthand Path**: `"geo/item/crystal_scythe.geo.json"` (resolved against the model's own namespace when unprefixed).

Only the model path is required: omit texture/animation paths (or pass `nil`) to derive
them from the model (`textures/<path>.png`, `animations/<path>.animation.json` in the same namespace).
Geo blocks render animated in the world via their block-entity renderer; their inventory
icon stays a plain cube model (handheld geo rendering for blocks is not implemented).

---

## 2. Blockbench Asset Creation Workflow

1. Open **Blockbench**.
2. Select **GeckoLib Animated Model** (Format: GeckoLib 4.0 / 4.x).
3. Set your Project ID / Model Identifier matching your asset name.
4. Model your geometry and map texture coordinates.
5. Create animations in the **Animate** tab:
   - For looping idle animations, create an animation named `idle` (or similar) and enable the **Loop** mode.
   - For action animations, create animations such as `spin`, `attack`, `work`, `open`, etc.
6. Export:
   - Export Model -> `<name>.geo.json` -> Place in `geo/{item,block,entity}/`.
   - Export Animations -> `<name>.animation.json` -> Place in `animations/{item,block,entity}/`.
   - Export Texture -> `<name>.png` -> Place in `textures/{item,block,entity}/`.

---

## 3. Lua Registration API

### 3.1 Custom Animated Item

Register a GeckoLib 3D item using `:GeoModel(...)` on `Content.NewItem`:

```lua
local Content = require("LuaTweaker.Content")

Content.NewItem("crystal_scythe")
    :MaxStackSize(1)
    :GeoModel(
        "geo/item/crystal_scythe.geo.json",
        "textures/item/crystal_scythe.png",
        "animations/item/crystal_scythe.animation.json",
        "idle" -- Optional default idle animation
    )
    :OnRightClick(function(player, itemStack, hand)
        -- Trigger spin animation on right click
        player:TriggerAnimation("spin", "controller")
        return true
    end)
    :Register()
```

### 3.2 Custom Animated Block

Register a GeckoLib animated block using `:GeoModel(...)` on `Content.NewBlock`:

```lua
local Content = require("LuaTweaker.Content")

Content.NewBlock("crystal_reanimator")
    :Material("metal")
    :Hardness(3.5)
    :GeoModel(
        "geo/block/crystal_reanimator.geo.json",
        "textures/block/crystal_reanimator.png",
        "animations/block/crystal_reanimator.animation.json",
        "idle" -- Looping idle animation
    )
    :OnRightClick(function(world, pos, player, hand)
        -- Trigger reanimate pulse animation on interaction
        world:PlayAnimation(pos, "pulse", "controller")
        return true
    end)
    :Register()
```

Blocks with a `:GeoModel(...)` specification are automatically backed by a `CustomContainerBlockEntity` implementing `GeoBlockEntity` and rendered via `LuaGeoBlockRenderer` using `RenderShape.ENTITYBLOCK_ANIMATED`.

### 3.3 Custom Animated Entity

Register a GeckoLib animated mob using `:GeoModel(...)` on `Content.NewEntity`:

```lua
local Content = require("LuaTweaker.Content")

Content.NewEntity("crystal_golem", function(builder)
    builder:Attributes({
        maxHealth = 100.0,
        movementSpeed = 0.25,
        attackDamage = 8.0
    })
    :GeoModel(
        "geo/entity/crystal_golem.geo.json",
        "textures/entity/crystal_golem.png",
        "animations/entity/crystal_golem.animation.json",
        "idle"
    )
end)
```

---

## 4. Runtime Animation Control Primitives

LuaTweaker provides low-level primitive functions to trigger and manage animations.

### 4.1 Entity Animation Primitives

```lua
-- Trigger animation on an entity instance
entity:TriggerAnimation(animName, controllerName)

-- Example: Trigger attack animation during custom combat logic
entity:TriggerAnimation("attack", "controller")
```

### 4.2 Block Animation Primitives

```lua
-- Directly via IInteractableBlock
block:PlayAnimation(animName, controllerName)
block:TriggerAnimation(animName, controllerName)

-- Or via World spatial service
World:PlayAnimation(blockPos, animName, controllerName)
World:TriggerAnimation(blockPos, animName, controllerName)
```

| Parameter | Type | Required | Description | Default |
|---|---|---|---|---|
| `animName` | `string` | Yes | Name of animation defined in `.animation.json` | None |
| `controllerName` | `string` | No | Target controller identifier | `"controller"` |

---

## 5. Network Synchronization Architecture

GeckoLib requires animations triggered on the server to be synchronized to all tracking clients:

```
[Lua Script (Server)]
       |
       v
`triggerAnimation(animName, controllerName)`
       |
       v
`GeoBlockEntity.triggerAnim(...)` / `GeoEntity.triggerAnim(...)`
       |
       +---> Client: Plays locally on RenderThread
       |
       +---> Server: Broadcasts GeckoLib Network Packet to Tracking Clients
                  |
                  v
             Clients receive packet and execute animation state transition
```

No manual packet handling is required from script authors. Triggering an animation on the server automatically propagates to all observing players.

---

## 6. Fallback and 2D Compatibility

When registering a 3D animated item or block:
- Standard 2D fallback item models (`assets/<namespace>/models/item/<name>.json`) can be provided alongside for GUI inventory renderers if desired.
- For blocks, the blockstate (`blockstates/<name>.json`) should point to a model referencing `minecraft:block/cube_all` or an empty model if full 3D entity-block rendering is used exclusively.

# LuaTweaker Scripting Guide

LuaTweaker enforces a clean architecture combining **Chainable Builder DSLs** for static definitions (Content, Recipes) and a **Reactive Signal & Service Architecture** for runtime logic.

---

## 1. Core Architecture Principles

1. **Explicit Module Imports (`require`):** No unanchored global magic. Access core libraries via explicit module paths.
2. **Static Content Builder DSL:** Dynamic chainable methods (`:DisplayName()`, `:MaxStackSize()`, `:Pattern()`) ending with `:Register()`.
3. **Reactive Signal Engine (`Events`):** Roblox Studio-inspired event signals with `Connect`, `Once`, `Fire`, `Wait`, and `Disconnect`.
4. **Spatial Math (`Vector3`):** Vector calculations (`Vector3.new(x, y, z)`), magnitude, dot product, and vector arithmetic (`posA + posB`).

---

## 2. Static Content Registration (`Content`)

```lua
local Content = require("LuaTweaker.Content")

-- Custom Item Registration
local Excalibur = Content.NewItem("luatweaker:excalibur")
    :DisplayName("§6Thánh Kiếm Excalibur")
    :MaxStackSize(1)
    :AttackDamage(14.5)
    :Durability(2000)
    :OnEquip(function(player)
        player:AddEffect("minecraft:regeneration", 200, 1)
    end)
    :Register()

-- Custom Block Registration
local RubyBlock = Content.NewBlock("luatweaker:ruby_block")
    :DisplayName("§cRuby Block")
    :Hardness(3.0)
    :Resistance(12.0)
    :Register()
```

---

## 3. Recipe Registration (`Recipe`)

```lua
local Recipe  = require("LuaTweaker.Recipe")
local Content = require("LuaTweaker.Content")

Recipe.Shaped("luatweaker:excalibur_craft")
    :Pattern({
        "  D  ",
        "  D  ",
        "  S  "
    })
    :Key("D", Content.Item("minecraft:diamond_block"))
    :Key("S", Content.Item("minecraft:blaze_rod"))
    :Output(Content.Item("luatweaker:excalibur"), 1)
    :Register()
```

---

## 4. Runtime Event Signals (`Events`, `World`, `Task`, `Vector3`)

```lua
local Events  = require("LuaTweaker.Events")
local World   = require("LuaTweaker.World")
local Task    = require("LuaTweaker.Task")
local Vector3 = require("LuaTweaker.Math.Vector3")

-- Event Listener: Entity Damaged Hook
Events.OnEntityDamaged:Connect(function(event)
    local attacker = event.Attacker
    local target   = event.Target

    if not attacker:IsPlayer() then return end
    
    local mainHand = attacker:GetHeldItem()
    if mainHand.Id == "luatweaker:excalibur" then
        Task.Delay(0.5, function()
            if target:IsAlive() then
                World:StrikeLightning(target.Position)
                target:TakeDamage(5.0, "magic")
            end
        end)
    end
end)

-- Event Listener: Block Break Particles
Events.OnBlockBreak:Connect(function(event)
    local block = event.Block
    local pos   = block.Position -- Vector3(X, Y, Z)
    
    if block.Id == "minecraft:diamond_ore" then
        World:SpawnParticle("minecraft:happy_villager", pos + Vector3.new(0.5, 0.5, 0.5))
    end
end)
```

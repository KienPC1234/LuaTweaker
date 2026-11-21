# Custom Mob Effects and Data-Driven Enchantments API

LuaTweaker provides a low-level, expressive builder API for registering custom status effects (MobEffects) and data-driven Enchantments in Minecraft 1.21.1 NeoForge.

---

## 1. Custom Mob Effects (`Content.NewMobEffect`)

Custom effects must be registered during startup scripts.

### Builder Methods

| Method | Parameters | Description |
|---|---|---|
| `:color(rgb)` | `rgb: integer` | Particle and icon tint color (e.g. `0xFF2222`). |
| `:category(cat)` | `cat: string` | `"BENEFICIAL"`, `"HARMFUL"`, or `"NEUTRAL"`. |
| `:beneficial()` | none | Sets category to beneficial. |
| `:harmful()` | none | Sets category to harmful. |
| `:neutral()` | none | Sets category to neutral. |
| `:texture(path)` | `path: string` | Custom 18x18 icon texture (e.g. `"mymod:textures/mob_effect/freeze.png"`). Renders on HUD and inventory. |
| `:displayName(name)` | `name: string` | User-visible name, automatically registered into virtual `en_us.json`. |
| `:instantaneous(bool)` | `bool: boolean` | Whether effect is instant (like Instant Health). |
| `:instant()` | none | Shortcut for `:instantaneous(true)`. |
| `:tickInterval(ticks)` | `ticks: integer` | Ticking frequency in ticks (fires `MobEffectTick`). Default is 0 (no tick event). |
| `:curable(bool)` | `bool: boolean` | Whether milk bucket clears this effect. Default is `true`. |
| `:attribute(id, val, op)` | `id: string, val: number, op: string` | Applies attribute modifier while active. Operations: `"ADD_VALUE"`, `"ADD_MULTIPLIED_BASE"`, `"ADD_MULTIPLIED_TOTAL"`. |
| `:register()` | none | Finalizes registration into NeoForge registry. |

### Example

```lua
-- src/startup/effects.lua
local Content = require("LuaTweaker.Content")

Content.NewMobEffect("luatweaker:frostbite")
    :displayName("Frostbite")
    :harmful()
    :color(0x33CCFF)
    :texture("luatweaker:textures/mob_effect/frostbite.png")
    :tickInterval(20) -- ticks every 1 second
    :curable(false)   -- milk cannot clear frostbite
    :attribute("minecraft:generic.movement_speed", -0.15, "ADD_MULTIPLIED_BASE")
    :register()
```

### Runtime Events

```lua
-- src/server/frostbite_logic.lua
local Events = require("LuaTweaker.Events")

Events:Listen("MobEffectStarted", function(event)
    if event.effect == "luatweaker:frostbite" then
        print("Entity " .. tostring(event.entity) .. " gained frostbite!")
    end
end)

Events:Listen("MobEffectTick", function(event)
    if event.effect == "luatweaker:frostbite" then
        local dmg = 1.0 * (event.amplifier + 1)
        event.entity:applyDamage(dmg, "freeze")
    end
end)
```

---

## 2. Custom Enchantments (`Content.NewEnchantment`)

In Minecraft 1.21.1, enchantments are data-driven JSON components. LuaTweaker provides a Lua table syntax that compiles directly into Minecraft 1.21.1 enchantment JSON definitions and virtual tags.

### Builder Methods

| Method | Parameters | Description |
|---|---|---|
| `:description(text)` | `text: string` | Display name of the enchantment. |
| `:maxLevel(level)` | `level: integer` | Maximum enchantment level (default: 1). |
| `:weight(weight)` | `weight: integer` | Rarity weight (1 = very rare, 10 = common). |
| `:minCost(base, perLvl)` | `base: integer, perLvl: integer` | Minimum enchanting table cost calculation. |
| `:maxCost(base, perLvl)` | `base: integer, perLvl: integer` | Maximum enchanting table cost calculation. |
| `:anvilCost(cost)` | `cost: integer` | Anvil repair/combine level cost. |
| `:supportedItems(tag)` | `tag: string` | Items that can accept this enchantment (e.g. `"#minecraft:enchantable/sword"`). |
| `:primaryItems(tag)` | `tag: string` | Items that receive this in enchanting table. |
| `:slots(slotList)` | `slots: table` | Valid equipment slots (e.g. `{"mainhand"}`). |
| `:exclusiveWith(tag)` | `tag: string` | Mutually exclusive enchantment tag. |
| `:damage(base, [perLvl])` | `base: number, perLvl: number` | Convenience helper for `minecraft:damage` value effect. |
| `:damageProtection(base, [perLvl])` | `base: number, perLvl: number` | Convenience helper for `minecraft:damage_protection`. |
| `:knockback(amount)` | `amount: number` | Convenience helper for `minecraft:knockback`. |
| `:curse([bool])` | `bool: boolean` | Marks enchantment as a curse and adds `#minecraft:curse` tag. |
| `:effects(tableOrJson)` | `table\|string` | Full data-driven effects definition using native Lua tables. |
| `:register()` | none | Finalizes enchantment into virtual datapack and tags. |

### Example

```lua
-- src/startup/enchantments.lua
local Content = require("LuaTweaker.Content")

Content.NewEnchantment("luatweaker:frost_edge")
    :description("Frost Edge")
    :maxLevel(3)
    :weight(5)
    :supportedItems("#minecraft:enchantable/sword")
    :primaryItems("#minecraft:enchantable/sword")
    :slots({"mainhand"})
    :damage(2.0, 1.5)
    :knockback(0.5)
    :effects({
        ["minecraft:post_attack"] = {
            {
                enchanted = "attacker",
                affected = "victim",
                effect = {
                    type = "minecraft:apply_mob_effect",
                    to_apply = "luatweaker:frostbite",
                    min_duration = 5.0,
                    max_duration = 10.0
                }
            }
        }
    })
    :register()
```

# 🌍 Exhaustive WorldGen, Jigsaw & Structure Generation Guide (`worldgen`)

> **Stage:** `startup_scripts/` or `server_scripts/`  
> **Global Variable:** `worldgen` | **Service Lookup:** `local worldgen = game:GetService("WorldGen")`

The `worldgen` API allows modpack creators to inject dynamic ore generation, place custom NBT structure templates from `lua/structures/`, generate multi-piece Jigsaw structures (villages, dungeons), add custom biomes, and remove biome features.

> 💡 **Service Registry Paradigm**: Access worldgen API via `worldgen` or `game:GetService("WorldGen")` / `Mod:GetService("WorldGen")`.

---

## 📁 1. Structure NBT Directory Layout (`lua/structures/`)

Place `.nbt` structure templates directly inside **`lua/structures/`**:

- **Structure File:** `.minecraft/lua/structures/ruby_temple.nbt`

---

## ⛏️ 2. Dynamic Ore Generation (`worldgen:addOre`)

```lua
worldgen:addOre("ruby_ore_gen", "luatweaker:custom_ruby_block", "minecraft:stone", 8, 12, -64, 32)
```

---

## 🏰 3. Advanced NBT Structure Placement (`worldgen:addStructure`)

```lua
worldgen:addStructure("ruby_temple", "luatweaker:structures/ruby_temple.nbt", "#minecraft:is_overworld")
```

---

## 🧩 4. Dynamic Jigsaw Structure Generation (`worldgen:addJigsawStructure`)

Generate multi-piece Jigsaw structures (villages, dungeons, custom ruins) dynamically:

### Signature:
```lua
worldgen:addJigsawStructure(id: string, poolId: string, maxDepth: integer, biomeFilter: string)
```

### ✅ Copyable Example:
```lua
worldgen:addJigsawStructure("ruby_village", "luatweaker:village/ruby_town_center", 6, "#minecraft:is_overworld")
```

---

## 🌿 5. Custom Biome Modifier (`worldgen:addBiome`)

```lua
worldgen:addBiome("ruby_forest", 0.8, 0.4)
```

---

## 🌿 6. Biome Feature Removal (`worldgen:removeFeature`)

```lua
worldgen:removeFeature("minecraft:plains", "minecraft:patch_sugar_cane")
```

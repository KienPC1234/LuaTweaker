# Extension Roadmap: Modular Expansion Plan

Tài liệu này mô tả kế hoạch mở rộng LuaTweaker với các module mới cho các tính năng modding phổ biến: Loot Tables, Worldgen, Structures, và Dimensions.

---

## 1. Tổng Quan & Priority Matrix

### 1.1 Nguyên Tắc Ưu Tiên

| Tiêu Chí | Weight | Mô Tả |
|----------|--------|-------|
| **Popularity** | 40% | Mức độ phổ biến trong modding community |
| **Impact** | 30% | Giá trị mang lại cho modders |
| **Complexity** | 20% | Độ phức tạp kỹ thuật (thấp = tốt) |
| **Dependencies** | 10% | Số lượng dependencies cần trước |

### 1.2 Priority Ranking

| Rank | Module / Feature | Score | Effort | Priority |
|------|------------------|-------|--------|----------|
| 👑 **0** | **Core: Proxy & Remapper** | 10/10 | 1-2 days | **BLOCKER** |
| 🥇 **1** | **Module-Loot** | 9.2/10 | 3-5 days | **CRITICAL** |
| 🥈 **2** | **Module-Worldgen** | 8.5/10 | 5-7 days | **HIGH** |
| 🥉 **3** | **Module-Structures** | 7.0/10 | 7-10 days | **MEDIUM** |
| 4 | **Module-Dimensions** | 6.5/10 | 10-15 days | **LOW** |
| 5 | **Recipes Extensions** | 7.5/10 | 2-3 days | **HIGH** |

---

## 2. Phase 0: Core Engine Finalization (BLOCKER)

Trước khi mở rộng bất kỳ Module Gameplay nào (Loot, Worldgen), lõi **Hybrid Dynamic Bridge** phải được hoàn thiện 100% để đảm bảo tính an toàn và khả năng chạy trên Production.

### 2.1 Mở Rộng Proxy cho Block & Item
- Áp dụng cơ chế Fallback Proxy (giống hệt `EntitiesLuaBinding`) vào `InteractionLuaBinding` cho Block và Item.
- Đảm bảo Modder có thể lấy modded properties của mọi Block/Item.

### 2.2 Security Hardening (Vành Đai An Ninh)
- Thêm cơ chế **Whitelist** (chỉ cho phép các package `net.minecraft`, `net.neoforged`, `com.luatweaker`) vào `DynamicJavaProxy`.
- Chặn đứng các nguy cơ bảo mật từ Reflection.

### 2.3 Runtime Remapper (Vượt rào Obfuscation)
- Tích hợp bộ từ điển ánh xạ (SRG/Mojmap) vào `DynamicJavaProxy`.
- Tự động phiên dịch tên hàm (VD: `getHealth` -> `m_21223_`) khi chạy trên môi trường Modpack thực tế.
- Đây là **điều kiện tiên quyết** để Dynamic Bridge không bị crash trên Production.

---

## 3. Module-Loot (PRIORITY 1)

### 2.1 Mục Tiêu

Cung cấp API toàn diện để manipulate loot tables:
- Modify mob drops
- Add/remove chest loot
- Custom loot conditions
- Dynamic loot tables (runtime modification)

### 2.2 API Design

#### Tầng 1: Sugar Syntax (80% users)

```lua
-- ==== MOB DROPS ====
-- Add/modify mob drops
Loot.AddMobDrop("minecraft:zombie", "minecraft:diamond", {
    chance = 0.1,        -- 10% chance
    minCount = 1,
    maxCount = 3,
    lootingBonus = 1     -- +1 per Looting level
})

Loot.ModifyMobDrop("minecraft:creeper", "minecraft:gunpowder", {
    chance = 1.0,        -- 100% (guaranteed)
    minCount = 2,
    maxCount = 4
})

Loot.RemoveMobDrop("minecraft:skeleton", "minecraft:bone")

-- ==== CHEST LOOT ====
Loot.AddChestLoot("minecraft:chests/simple_dungeon", "mymod:magic_sword", {
    chance = 0.05,       -- 5% chance
    minCount = 1,
    maxCount = 1,
    enchantments = {
        { id = "minecraft:sharpness", level = 5 }
    }
})

Loot.RemoveChestLoot("minecraft:chests/village_blacksmith", "minecraft:iron_ingot")

-- ==== FISHING ====
Loot.AddFishingLoot("mymod:treasure_chest", {
    chance = 0.01,
    biomeFilter = { "minecraft:ocean", "minecraft:deep_ocean" }
})

-- ==== BLOCK DROPS ====
Loot.SetBlockDrop("mymod:ruby_ore", {
    item = "mymod:ruby",
    fortuneBonus = 2,    -- +2 per Fortune level
    silkTouchDrop = "mymod:ruby_ore"
})
```

#### Tầng 2: Dynamic Bridge (20% power users)

```lua
-- Access raw LootTable objects via DynamicJavaProxy
local lootTable = Loot.GetTable("minecraft:chests/simple_dungeon")

-- Call Java methods directly
lootTable:addPool(customPool)
local pools = lootTable:getPools()

-- Modify conditions
local pool = lootTable:getPool(0)
pool:addCondition(LootCondition.randomChance(0.5))
```

### 2.3 Technical Implementation

#### Architecture

```
common-api/
└── api/loot/
    ├── ILootService.java          # Interface
    ├── ILootModifier.java         # Loot modifier interface
    └── LootEntry.java             # DTO

modules/module-loot/
├── src/main/java/com/luatweaker/loot/
│   ├── LootServiceImpl.java       # Implementation
│   ├── LootLuaBinding.java        # LuaBinder integration
│   └── LootTableHelper.java       # Helper methods
└── src/test/java/
    └── LootServiceImplTest.java   # Tests

neoforge-platform/
└── platform/loot/
    └── NeoForgeLootProvider.java  # NeoForge-specific implementation
```

#### Key Interfaces

```java
// common-api/src/main/java/com/luatweaker/api/loot/ILootService.java
@LuaDoc(description = "Service for manipulating loot tables")
public interface ILootService {
    @LuaDoc(description = "Add or modify mob drop")
    void addMobDrop(String mobId, String itemId, ILuaTable options);
    
    @LuaDoc(description = "Remove mob drop")
    void removeMobDrop(String mobId, String itemId);
    
    @LuaDoc(description = "Add chest loot")
    void addChestLoot(String chestId, String itemId, ILuaTable options);
    
    @LuaDoc(description = "Get raw LootTable object (for Dynamic Bridge)")
    Object getTable(String lootTableId);
}
```

#### Implementation Strategy

1. **Hot-path methods** (manual wrapper):
   - `addMobDrop`, `removeMobDrop`, `addChestLoot`
   - Sugar syntax, validated inputs
   - Use Platform.getInstance() for NeoForge API

2. **Dynamic Bridge fallback**:
   - `getTable()` returns raw `LootTable` object
   - Wrap via DynamicJavaProxy
   - Modder calls Java methods directly

### 2.4 Files Cần Tạo

| File | Module | Lines | Effort |
|------|--------|-------|--------|
| `ILootService.java` | common-api | ~50 | 0.5 day |
| `LootEntry.java` | common-api | ~30 | 0.5 day |
| `LootServiceImpl.java` | module-loot | ~200 | 2 days |
| `LootLuaBinding.java` | module-loot | ~80 | 1 day |
| `NeoForgeLootProvider.java` | neoforge-platform | ~150 | 1 day |
| `LootServiceImplTest.java` | module-loot/test | ~150 | 1 day |
| **TOTAL** | | **~660** | **3-5 days** |

### 2.5 Tests Required

- ✅ Unit tests cho `LootServiceImpl`
- ✅ Test mob drop modification
- ✅ Test chest loot addition/removal
- ✅ Test dynamic bridge access
- ✅ Test error paths (invalid IDs, null values)
- ✅ Integration test với NeoForge loot system

---

## 3. Module-Worldgen (PRIORITY 2)

### 3.1 Mục Tiêu

Cung cấp API cho world generation:
- Custom ore generation
- Biome modification
- Feature placement
- Vegetation/structure placement

### 3.2 API Design

#### Tầng 1: Sugar Syntax

```lua
-- ==== ORE GENERATION ====
Worldgen.AddOre("mymod:ruby_ore", {
    dimension = "minecraft:overworld",
    minHeight = -64,
    maxHeight = 32,
    clusterSize = 8,
    frequency = 10,          -- clusters per chunk
    biomeFilter = { "minecraft:plains", "minecraft:forest" }
})

Worldgen.AddOre("mymod:mythril_ore", {
    dimension = "minecraft:overworld",
    minHeight = -128,
    maxHeight = -64,
    clusterSize = 4,
    frequency = 2,
    replaceBlocks = { "minecraft:deepslate" }
})

-- ==== BIOME MODIFICATION ====
Worldgen.ModifyBiome("minecraft:desert", {
    temperature = 2.5,
    downfall = 0.0,
    addFeatures = { "mymod:cactus_forest" }
})

-- ==== FEATURE PLACEMENT ====
Worldgen.AddFeature("mymod:giant_tree", {
    type = "tree",
    decorator = "minecraft:heightmap",
    count = 1,
    biomes = { "minecraft:forest", "minecraft:taiga" }
})

Worldgen.AddVegetation("mymod:crystal_flower", {
    chance = 0.1,
    biomes = { "mymod:crystal_plains" }
})
```

#### Tầng 2: Dynamic Bridge

```lua
-- Access raw Biome/Feature objects
local biome = Worldgen.GetBiome("minecraft:plains")
biome:addFeature(GenerationStep.VEGETAL_DECORATION, customFeature)

local feature = Worldgen.GetFeature("mymod:giant_tree")
feature:setConfig(customConfig)
```

### 3.3 Technical Implementation

#### Architecture

```
common-api/
└── api/worldgen/
    ├── IWorldgenService.java
    ├── OreConfig.java         # DTO
    └── BiomeConfig.java       # DTO

modules/module-worldgen/
├── src/main/java/com/luatweaker/worldgen/
│   ├── WorldgenServiceImpl.java
│   ├── WorldgenLuaBinding.java
│   └── OreGenerator.java
└── src/test/java/
    └── WorldgenServiceImplTest.java

neoforge-platform/
└── platform/worldgen/
    └── NeoForgeWorldgenProvider.java
```

#### Key Interfaces

```java
@LuaDoc(description = "Service for world generation")
public interface IWorldgenService {
    @LuaDoc(description = "Add ore generation")
    void addOre(String blockId, ILuaTable config);
    
    @LuaDoc(description = "Modify biome properties")
    void modifyBiome(String biomeId, ILuaTable modifications);
    
    @LuaDoc(description = "Add custom feature")
    void addFeature(String featureId, ILuaTable config);
    
    @LuaDoc(description = "Get raw Biome object (Dynamic Bridge)")
    Object getBiome(String biomeId);
}
```

### 3.4 Files Cần Tạo

| File | Module | Lines | Effort |
|------|--------|-------|--------|
| `IWorldgenService.java` | common-api | ~60 | 0.5 day |
| `OreConfig.java` | common-api | ~40 | 0.5 day |
| `WorldgenServiceImpl.java` | module-worldgen | ~300 | 3 days |
| `WorldgenLuaBinding.java` | module-worldgen | ~100 | 1 day |
| `NeoForgeWorldgenProvider.java` | neoforge-platform | ~250 | 2 days |
| `WorldgenServiceImplTest.java` | module-worldgen/test | ~200 | 1 day |
| **TOTAL** | | **~950** | **5-7 days** |

---

## 4. Module-Structures (PRIORITY 3)

### 4.1 Mục Tiêu

Custom structure generation:
- Register custom structures
- Structure pieces/jigsaw
- Spawn conditions
- Loot integration

### 4.2 API Design

#### Tầng 1: Sugar Syntax

```lua
-- ==== STRUCTURE REGISTRATION ====
Structures.Register("mymod:ancient_temple", {
    template = "mymod:structures/temple.nbt",
    spawnBiomes = { "minecraft:desert", "minecraft:badlands" },
    spacing = 32,            -- average distance between structures
    separation = 16,         -- minimum distance
    spawnChance = 0.8,
    
    -- Loot integration
    lootTables = {
        chest = "mymod:chests/temple_treasure"
    }
})

-- ==== JIGSAW STRUCTURES ====
Structures.RegisterJigsaw("mymod:village_house", {
    startPiece = "mymod:jigsaw/house_start",
    maxDepth = 3,
    pieces = {
        "mymod:jigsaw/house_wall",
        "mymod:jigsaw/house_corner",
        "mymod:jigsaw/house_roof"
    }
})

-- ==== SPAWN CONDITIONS ====
Structures.SetSpawnCondition("mymod:ancient_temple", function(context)
    local biome = context:getBiome()
    local height = context:getHeight()
    return biome == "minecraft:desert" and height > 60
end)
```

#### Tầng 2: Dynamic Bridge

```lua
local structure = Structures.Get("mymod:ancient_temple")
structure:addPiece(customPiece)
structure:setStartPool(jigsawPool)
```

### 4.3 Technical Implementation

**Dependencies:** Module-Worldgen (structure placement)

#### Architecture

```
common-api/
└── api/structures/
    ├── IStructureService.java
    └── StructureConfig.java

modules/module-structures/
├── src/main/java/com/luatweaker/structures/
│   ├── StructureServiceImpl.java
│   ├── StructureLuaBinding.java
│   └── NbtStructureLoader.java
└── src/test/java/
    └── StructureServiceImplTest.java

neoforge-platform/
└── platform/structures/
    └── NeoForgeStructureProvider.java
```

### 4.4 Files Cần Tạo

| File | Module | Lines | Effort |
|------|--------|-------|--------|
| `IStructureService.java` | common-api | ~70 | 1 day |
| `StructureConfig.java` | common-api | ~50 | 0.5 day |
| `StructureServiceImpl.java` | module-structures | ~400 | 4 days |
| `StructureLuaBinding.java` | module-structures | ~120 | 1.5 days |
| `NbtStructureLoader.java` | module-structures | ~150 | 1 day |
| `NeoForgeStructureProvider.java` | neoforge-platform | ~300 | 2 days |
| `StructureServiceImplTest.java` | module-structures/test | ~250 | 1.5 days |
| **TOTAL** | | **~1340** | **7-10 days** |

---

## 5. Module-Dimensions (PRIORITY 4)

### 5.1 Mục Tiêu

Custom dimension creation:
- Dimension types
- Biome sources
- Custom world generators
- Sky/weather customization

### 5.2 API Design

#### Tầng 1: Sugar Syntax

```lua
-- ==== DIMENSION CREATION ====
Dimensions.Create("mymod:sky_realm", {
    type = "floating_islands",
    
    -- Biomes
    biomes = {
        { id = "mymod:crystal_plains", weight = 5 },
        { id = "mymod:rainbow_forest", weight = 3 }
    },
    
    -- Generator
    generator = "noise",
    generatorConfig = {
        noiseScale = 1.5,
        heightVariation = 32
    },
    
    -- Visual
    skyColor = 0xFF88FF,
    fogColor = 0xAADDFF,
    hasSkyLight = true,
    hasCeiling = false,
    
    -- Gameplay
    fixedTime = 6000,        -- noon
    piglinSafe = false,
    bedWorks = true
})

-- ==== CUSTOM BIOME SOURCE ====
Dimensions.SetBiomeSource("mymod:sky_realm", function(x, z)
    local distance = math.sqrt(x*x + z*z)
    if distance < 1000 then
        return "mymod:crystal_plains"
    else
        return "mymod:rainbow_forest"
    end
end)
```

#### Tầng 2: Dynamic Bridge

```lua
local dimension = Dimensions.Get("mymod:sky_realm")
dimension:setChunkGenerator(customGenerator)
dimension:setBiomeSource(customSource)
```

### 5.3 Technical Implementation

**Dependencies:** Module-Worldgen (biome source, chunk generator)

#### Architecture

```
common-api/
└── api/dimensions/
    ├── IDimensionService.java
    ├── DimensionConfig.java
    └── IBiomeSource.java

modules/module-dimensions/
├── src/main/java/com/luatweaker/dimensions/
│   ├── DimensionServiceImpl.java
│   ├── DimensionLuaBinding.java
│   └── LuaBiomeSource.java
└── src/test/java/
    └── DimensionServiceImplTest.java

neoforge-platform/
└── platform/dimensions/
    └── NeoForgeDimensionProvider.java
```

### 5.4 Files Cần Tạo

| File | Module | Lines | Effort |
|------|--------|-------|--------|
| `IDimensionService.java` | common-api | ~80 | 1 day |
| `DimensionConfig.java` | common-api | ~60 | 0.5 day |
| `IBiomeSource.java` | common-api | ~30 | 0.5 day |
| `DimensionServiceImpl.java` | module-dimensions | ~500 | 6 days |
| `DimensionLuaBinding.java` | module-dimensions | ~150 | 2 days |
| `LuaBiomeSource.java` | module-dimensions | ~200 | 2 days |
| `NeoForgeDimensionProvider.java` | neoforge-platform | ~400 | 3 days |
| `DimensionServiceImplTest.java` | module-dimensions/test | ~300 | 2 days |
| **TOTAL** | | **~1720** | **10-15 days** |

---

## 6. Dependencies Graph

```
Module-Loot (standalone)
    ↓
Module-Worldgen (standalone)
    ↓
Module-Structures (depends on Worldgen)
    ↓
Module-Dimensions (depends on Worldgen)
```

### Dependency Matrix

| Module | Depends On | Blocks |
|--------|-----------|--------|
| Module-Loot | None | None |
| Module-Worldgen | None | Structures, Dimensions |
| Module-Structures | Worldgen | None |
| Module-Dimensions | Worldgen | None |

---

## 7. Timeline & Milestones

### Phase 0: Core Finalization (Tuần 0)

**Week 0:**
- ✅ Day 1: Mở rộng Proxy cho Block/Item.
- ✅ Day 2: Security Whitelist + Performance Benchmark.
- ✅ Day 3-4: Runtime Remapper (Đọc bảng SRG/Mojmap).

### Phase 1: Foundation (Weeks 1-2)

**Week 1:**
- ✅ Day 1-3: Module-Loot (API + implementation)
- ✅ Day 4-5: Module-Loot (tests + documentation)
- ✅ Day 5: Recipes Extensions (quick win)

**Week 2:**
- ✅ Day 1-3: Module-Worldgen (API + ore generation)
- ✅ Day 4-5: Module-Worldgen (biome modification)
- ✅ Day 5: Module-Worldgen (tests + documentation)

### Phase 2: Advanced (Weeks 3-4)

**Week 3:**
- ✅ Day 1-4: Module-Structures (API + NBT loading)
- ✅ Day 5: Module-Structures (jigsaw support)

**Week 4:**
- ✅ Day 1-3: Module-Structures (tests + documentation)
- ✅ Day 4-5: Buffer / bug fixes

### Phase 3: Complex (Weeks 5-7)

**Week 5-6:**
- ✅ Day 1-10: Module-Dimensions (full implementation)

**Week 7:**
- ✅ Day 1-5: Module-Dimensions (tests + documentation)

### Milestone Checklist

- [ ] **M1 (Week 1):** Module-Loot complete, tested, documented
- [ ] **M2 (Week 2):** Module-Worldgen complete, tested, documented
- [ ] **M3 (Week 4):** Module-Structures complete, tested, documented
- [ ] **M4 (Week 7):** Module-Dimensions complete, tested, documented
- [ ] **M5 (Week 8):** Integration tests, cross-module validation

---

## 8. Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| NeoForge API changes | Medium | High | Use PAL, abstract NeoForge-specific code |
| Performance issues (worldgen) | High | Medium | Benchmark early, optimize hot paths |
| Complexity creep (dimensions) | High | High | Start simple, iterate incrementally |
| Test coverage gaps | Medium | Medium | Write tests alongside implementation |
| Documentation debt | Medium | Medium | Document as you go, not at the end |

---

## 9. Success Metrics

### Quantitative

- **Module-Loot:** 100% test coverage, < 100ms per loot modification
- **Module-Worldgen:** Support 100+ custom ores without performance degradation
- **Module-Structures:** Load NBT structures < 50ms
- **Module-Dimensions:** Generate custom dimension at 20 TPS

### Qualitative

- API dễ dùng cho beginners (sugar syntax)
- Power users có full access qua dynamic bridge
- Documentation rõ ràng với examples
- Zero breaking changes giữa versions

---

## 10. Kết Luận

Kế hoạch này tuân thủ nghiêm ngặt ARCHITECTURE_BLUEPRINT.md:
- ✅ 2 tầng API (Sugar Syntax + Dynamic Bridge)
- ✅ Zero-Wrapper Rule (chỉ wrap hot paths)
- ✅ Runtime Remapper (Sống sót trên Production)
- ✅ Platform Abstraction Layer (PAL)
- ✅ SOLID principles
- ✅ Test-driven development

Tổng effort: **28-40 ngày** cho Core + 4 modules lớn.

Bắt buộc hoàn thành **Phase 0 (Core: Proxy & Remapper)** để tạo nền móng bê tông cốt thép trước khi xây dựng **Module-Loot**.

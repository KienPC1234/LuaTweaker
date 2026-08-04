# Custom Dimension System - Chi Tiết Thiết Kế

## Mục Tiêu

Tạo custom dimension cho arcane_rpg mod với:
- Bộ gen địa hình riêng bằng Lua (không dùng vanilla)
- fBm/fractal noise, ridged noise, domain warping, Voronoi
- Custom blocks, fluids, skybox
- Nhiều cách vào dimension (portal, command, item)
- Low-level API cho power users

---

## Kiến Trúc Tổng Thể

```
┌─────────────────────────────────────────────────────┐
│  Tầng Lua (Modder)                                   │
│                                                       │
│  Dimensions.Create("arcane:crystal_realm", {         │
│      terrain = "custom",                             │
│      generator = function(x, z) ... end,             │
│      skyColor = 0xFF88FF,                            │
│      blocks = { surface = "arcane:crystal_grass" }   │
│  })                                                   │
│                                                       │
│  Noise.fBm(x, z, octaves, lacunarity, gain)          │
│  Noise.Ridged(x, z, octaves)                         │
│  Noise.DomainWarp(x, z, warpStrength)                │
│  Noise.Voronoi(x, z, jitter)                         │
└─────────────────┬───────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────┐
│  module-dimensions (core-engine)                      │
│                                                       │
│  IDimensionService - interface                        │
│  DimensionServiceImpl - logic + cache                 │
│  DimensionLuaBinding - LuaBinder                      │
└─────────────────┬───────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────┐
│  module-noise (core-engine)                           │
│                                                       │
│  INoiseService - interface                            │
│  NoiseServiceImpl - Java impl (fast)                  │
│  NoiseLuaBinding - LuaBinder                          │
└─────────────────┬───────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────┐
│  neoforge-platform                                    │
│                                                       │
│  NeoForgeDimensionProvider                            │
│  LuaChunkGenerator - extends ChunkGenerator           │
│  LuaBiomeSource - extends BiomeSource                 │
│  PortalBlock - custom portal                          │
└───────────────────────────────────────────────────────┘
```

---

## Phase A: Module-Noise (3-4 ngày)

### A.1 Interface (common-api)

```java
// common-api/src/main/java/com/luatweaker/api/noise/INoiseService.java
@LuaDoc(description = "Noise functions for terrain generation.")
public interface INoiseService {
    
    @LuaDoc(description = "Fractional Brownian Motion noise.")
    double fBm(double x, double z, int octaves, double lacunarity, double gain);
    
    @LuaDoc(description = "Ridged multifractal noise.")
    double ridged(double x, double z, int octaves, double frequency, double lacunarity);
    
    @LuaDoc(description = "Domain warping - distort input coordinates.")
    double[] domainWarp(double x, double z, double strength, double frequency);
    
    @LuaDoc(description = "Voronoi/cellular noise.")
    double voronoi(double x, double z, double jitter, int returnType);
    
    @LuaDoc(description = "Simplex noise (2D).")
    double simplex(double x, double z, double frequency);
    
    @LuaDoc(description = "Seed the noise generator.")
    void setSeed(long seed);
}
```

### A.2 Implementation (module-noise)

```
modules/module-noise/
├── build.gradle
└── src/main/java/com/luatweaker/noise/
    ├── NoiseServiceImpl.java      // Core noise algorithms
    ├── NoiseLuaBinding.java       // LuaBinder
    └── internal/
        ├── SimplexNoise.java      // OpenSimplex2 implementation
        ├── FBMNoise.java          // fBm with octaves
        ├── RidgedNoise.java       // Ridged multifractal
        ├── VoronoiNoise.java      // Cellular/Voronoi
        └── DomainWarp.java        // Domain warping
```

### A.3 Thuật Toán

**fBm (Fractional Brownian Motion):**
```java
double fBm(double x, double z, int octaves, double lacunarity, double gain) {
    double sum = 0, amplitude = 1, frequency = 1, max = 0;
    for (int i = 0; i < octaves; i++) {
        sum += simplex(x * frequency, z * frequency) * amplitude;
        max += amplitude;
        amplitude *= gain;
        frequency *= lacunarity;
    }
    return sum / max;  // normalize to [-1, 1]
}
```

**Ridged Noise:**
```java
double ridged(double x, double z, int octaves) {
    double sum = 0, weight = 1;
    for (int i = 0; i < octaves; i++) {
        double signal = Math.abs(simplex(x, z));
        signal = 1.0 - signal;
        signal *= signal;
        signal *= weight;
        weight = Math.min(1.0, Math.max(0.0, signal * 2.0));
        sum += signal;
        x *= 2.0; z *= 2.0;
    }
    return sum;
}
```

**Domain Warping:**
```java
double[] domainWarp(double x, double z, double strength) {
    double qx = fBm(x, z, 4, 2.0, 0.5);
    double qz = fBm(x + 5.2, z + 1.3, 4, 2.0, 0.5);
    double rx = fBm(x + strength * qx, z + strength * qz, 4, 2.0, 0.5);
    double rz = fBm(x + strength * qy, z + strength * qx, 4, 2.0, 0.5);
    return new double[]{rx, rz};
}
```

**Voronoi/Cellular:**
```java
double voronoi(double x, double z, double jitter) {
    int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
    double minDist = Double.MAX_VALUE;
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            // hash neighbor to get random point
            double px = xi + dx + hash(xi+dx, zi+dy) * jitter;
            double pz = zi + dy + hash2(xi+dx, zi+dy) * jitter;
            double dist = Math.hypot(x - px, z - pz);
            minDist = Math.min(minDist, dist);
        }
    }
    return minDist;
}
```

### A.4 Tests

- 20+ tests cover: mỗi noise function với edge cases
- Benchmark: 1M evaluations < 2 seconds
- Seed reproducibility test

---

## Phase B: Module-Dimensions (7-10 ngày)

### B.1 Interface (common-api)

```java
@LuaDoc(description = "Service for creating and managing custom dimensions.")
public interface IDimensionService {
    
    @LuaDoc(description = "Register a new dimension.")
    void create(@NotNull String dimensionId, @NotNull ILuaTable config);
    
    @LuaDoc(description = "Set the terrain generator callback (Lua function).")
    void setTerrainGenerator(@NotNull String dimensionId, @NotNull Object luaFunction);
    
    @LuaDoc(description = "Set the biome provider callback.")
    void setBiomeProvider(@NotNull String dimensionId, @NotNull Object luaFunction);
    
    @LuaDoc(description = "Register a portal block for dimension entry.")
    void registerPortal(@NotNull String blockId, @NotNull String targetDimension);
    
    @LuaDoc(description = "Teleport player to dimension.")
    void teleportTo(@NotNull Object player, @NotNull String dimensionId);
    
    @LuaDoc(description = "Get dimension info.")
    @Nullable Object getDimension(@NotNull String dimensionId);
}
```

### B.2 Config Structure (Lua side)

```lua
Dimensions.Create("arcane:crystal_realm", {
    -- Dimension type properties
    hasSkyLight = true,
    hasCeiling = false,
    ultraWarm = false,
    natural = true,
    coordinateScale = 1.0,
    bedWorks = true,
    respawnAnchorWorks = false,
    fixedTime = nil,  -- nil = day/night cycle
    
    -- Visual
    skyColor = 0xFF88FF,
    fogColor = 0xAADDFF,
    ambientLight = 0.1,
    
    -- Terrain
    terrain = "custom",
    seaLevel = 63,
    minHeight = -64,
    maxHeight = 320,
    
    -- Surface blocks
    surfaceBlock = "arcane:crystal_grass",
    subsurfaceBlock = "arcane:crystal_dirt",
    fillerBlock = "minecraft:stone",
    
    -- Biomes
    biomes = {
        { id = "arcane:crystal_plains", weight = 5 },
        { id = "arcane:crystal_forest", weight = 3 },
        { id = "arcane:void_wastes", weight = 1 }
    },
    
    -- Features
    features = {
        { type = "ore", block = "arcane:crystal_ore", size = 8, frequency = 20, minY = -64, maxY = 64 },
        { type = "tree", feature = "arcane:crystal_tree", count = 2 }
    },
    
    -- Spawn
    spawnEntities = {
        { entity = "arcane:crystal_elemental", weight = 10, minGroup = 1, maxGroup = 3 }
    }
})

-- Custom terrain generator (Tier 2 - low-level)
Dimensions.SetTerrainGenerator("arcane:crystal_realm", function(x, z, baseHeight)
    -- Macro terrain: fBm for mountains/valleys
    local continent = Noise.fBm(x * 0.001, z * 0.001, 6, 2.0, 0.5)
    local mountains = Noise.Ridged(x * 0.005, z * 0.005, 4)
    local detail = Noise.fBm(x * 0.02, z * 0.02, 4, 2.0, 0.5)
    
    -- Domain warp for organic shapes
    local warped = { Noise.DomainWarp(x * 0.003, z * 0.003, 50) }
    
    -- Combine layers
    local height = baseHeight 
        + continent * 40        -- large-scale landforms
        + mountains * 30        -- mountain ridges
        + detail * 5            -- surface detail
    
    -- Voronoi for crystal formations
    local crystal = Noise.Voronoi(x * 0.05, z * 0.05, 0.8, 0)
    if crystal < 0.1 then
        height = height + 10  -- crystal spires
    end
    
    -- Return height + block ID
    return height, "arcane:crystal_stone"
end)
```

### B.3 NeoForge Integration

```
neoforge-platform/src/main/java/com/luatweaker/platform/dimension/
├── NeoForgeDimensionProvider.java
├── LuaChunkGenerator.java         // extends ChunkGenerator
├── LuaBiomeSource.java            // extends BiomeSource  
├── LuaDimensionType.java          // DimensionType registration
├── PortalBlock.java               // Custom portal block
├── PortalFrameValidator.java      // Validate portal frame
└── DimensionCommands.java         // /dimension teleport command
```

**LuaChunkGenerator:**
```java
public class LuaChunkGenerator extends ChunkGenerator {
    private final IDimensionService dimensionService;
    private final String dimensionId;
    
    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures,
                             ChunkAccess chunk) {
        // For each column in chunk:
        // 1. Call Lua terrain generator: (x, z) -> (height, blockId)
        // 2. Fill column with blocks
        // 3. Place surface/subsurface blocks
    }
    
    @Override
    public void applyCarvers(WorldGenLevel level, long seed, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk) {
        // Optional: call Lua carver function
    }
}
```

### B.4 Portal System

```lua
-- Register portal block
Content.NewBlock("crystal_portal")
    :Hardness(-1)        -- unbreakable
    :LightLevel(15)
    :OnRightClick(function(player, blockState)
        Dimensions:TeleportTo(player, "arcane:crystal_realm")
    end)
    :Register()

-- Portal frame (auto-detect like nether portal)
Dimensions:RegisterPortal("arcane:crystal_portal", "arcane:crystal_realm")
```

### B.5 Entry Mechanisms

| Method | Implementation |
|--------|---------------|
| Portal Block | Custom block with frame validation |
| Command | `/dimension teleport <player> <dimension>` |
| Item | Custom item right-click triggers teleport |
| Entity | Boss death opens portal |
| Structure | Entering structure teleports |

---

## Phase C: Demo - Crystal Realm Dimension (3-5 ngày)

### C.1 Custom Blocks

```lua
-- Crystal Realm blocks
Content.createBlock("crystal_stone", function(b)
    b:hardness(3.0):resistance(15.0):lightLevel(2)
      :mineableWith("pickaxe"):miningLevel("iron")
end)

Content.createBlock("crystal_grass", function(b)
    b:hardness(1.5):lightLevel(4)
      :drop("arcane:crystal_dirt")
end)

Content.createFluid("liquid_crystal", function(f)
    f:name("Liquid Crystal"):color(0x88CCFF)
      :temperature(300):viscosity(800):lightLevel(10)
end)
```

### C.2 Custom Biomes

```lua
-- Biome definitions via datapack JSON
-- Generated by DimensionProvider
```

### C.3 Terrain Generator

```lua
-- Crystal Realm terrain
Dimensions.SetTerrainGenerator("arcane:crystal_realm", function(x, z, baseHeight)
    -- Layer 1: Continent shapes (fBm)
    local continent = Noise.fBm(x * 0.001, z * 0.001, 6, 2.0, 0.5)
    
    -- Layer 2: Crystal mountain ridges (ridged noise)
    local ridges = Noise.Ridged(x * 0.005, z * 0.005, 5)
    
    -- Layer 3: Domain warp for organic crystal shapes
    local warp = Noise.DomainWarp(x * 0.002, z * 0.002, 80)
    
    -- Layer 4: Fine detail
    local detail = Noise.fBm(x * 0.02, z * 0.02, 3, 2.0, 0.5)
    
    -- Layer 5: Voronoi crystal formations
    local crystal = Noise.Voronoi(x * 0.05, z * 0.05, 0.7, 0)
    
    -- Combine
    local height = baseHeight
        + continent * 50
        + ridges * 35
        + detail * 3
        + (crystal < 0.05 and 20 or 0)  -- crystal spires
    
    -- Block selection based on height
    local block = "arcane:crystal_stone"
    if height < 70 then
        block = "arcane:crystal_dirt"
    elseif height > 120 then
        block = "arcane:crystal_peak"
    end
    
    return height, block
end)
```

### C.4 Entry Portal

```lua
-- Crystal Portal - multi-block structure
Content.createBlock("crystal_portal_frame", function(b)
    b:hardness(5):resistance(20):lightLevel(10)
      :onRightClick(function(player)
        -- Check frame, activate portal
    end)
end)

Events:Listen("PlayerInteractBlock", function(event)
    -- Validate 4x5 frame of crystal_portal_frame
    -- Fill interior with portal blocks
end)
```

---

## Tổng Effort

| Phase | Module | Effort | Dependencies |
|-------|--------|--------|--------------|
| **A** | module-noise | 3-4 ngày | none |
| **B** | module-dimensions | 7-10 ngày | module-noise |
| **C** | Crystal Realm demo | 3-5 ngày | A + B |
| **TOTAL** | | **13-19 ngày** | |

---

## Rủi Ro

| Risk | Mitigation |
|------|------------|
| Performance: Lua callback per-block quá chậm | Cache heightmap, batch calls, LOD (Level of Detail) |
| NeoForge API thay đổi | Abstraction qua PAL, test trên 1.21.1 |
| Obfuscation phá reflection | RuntimeRemapper đã fix (4 rules) |
| Memory: Noise cache quá lớn | LRU cache, configurable size |
| Thread safety: Chunk gen chạy multi-thread | Lua VM synchronized, immutable noise state |

---

## Success Metrics

- Crystal Realm dimension có thể tạo và enter được
- Custom terrain gen hoạt động ở 20 TPS
- Noise functions: 1M evals < 2s
- Portal hoạt động 2 chiều
- Custom blocks/fluids render đúng
- 0 crashes trong 30 phút gameplay

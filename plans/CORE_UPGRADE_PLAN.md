# Core Upgrade Plan - Từ Dễ Đến Khó

> **Nguyên tắc:** Không dead code. Không code trang trí. Mọi function phải chạy được, có test, có documentation.

---

## Task 1: Teardown Hooks (OnScriptUnload)

### Độ khó: ⭐ Dễ nhất
### Thời gian: 1-2 ngày
### Mục tiêu:
Khi `/lt reload`, modder cần dọn dẹp resources (timers, UI, signals) trước khi script mới load.

### Acceptance Criteria:
- [ ] `Events:Listen("OnScriptUnload", callback)` hoạt động
- [ ] Callback được gọi TRƯỚC khi engine cũ bị destroy
- [ ] Multiple listeners supported (mỗi mod 1 listener)
- [ ] Test pass: tạo listener, reload, verify callback chạy

### Files cần tạo/sửa:

#### 1.1 common-api: Thêm event name constant
**File:** `common-api/src/main/java/com/luatweaker/api/event/EventNames.java` (TẠO MỚI)
```java
public final class EventNames {
    public static final String ON_SCRIPT_UNLOAD = "OnScriptUnload";
    private EventNames() {}
}
```

#### 1.2 core-engine: Fire event trước khi destroy
**File:** `core-engine/src/main/java/com/luatweaker/core/mod/LuaModManager.java` (SỬA)
- Tìm method reload/unload
- Thêm `fireEvent("OnScriptUnload", ...)` TRƯỚC khi destroy engine

#### 1.3 Module-events: Đảm bảo event hoạt động
**File:** `modules/module-events/src/main/java/com/luatweaker/events/EventServiceImpl.java` (SỬA)
- Đảm bảo fireEvent hoạt động với raw event name

#### 1.4 Tests
**File:** `modules/module-events/src/test/java/com/luatweaker/events/TeardownHookTest.java` (TẠO MỚI)
```java
@Test
void testOnScriptUnload_CalledBeforeDestroy() {
    // 1. Register listener
    // 2. Trigger reload
    // 3. Verify callback was called
}
```

#### 1.5 Documentation
**File:** `README.md` (SỬA) - Thêm section về Teardown Hooks

### Code Example (modder usage):
```lua
-- main.lua
local signal = Signal.new()

Events:Listen("OnScriptUnload", function()
    signal:Disconnect()  -- Dọn signal
    print("[MyMod] Cleanup done!")
end)

-- Reload script -> "Cleanup done!" in log
```

### Checklist khi hoàn thành:
- [ ] Code compile thành công
- [ ] Tests pass (ít nhất 2 tests)
- [ ] Documentation updated
- [ ] Không có dead code (mọi method đều được gọi)

---

## Task 2: UI Component System

### Độ khó: ⭐⭐⭐ Trung bình
### Thời gian: 5-7 ngày
### Mục tiêu:
WoW-style UI builder - modder tạo UI bằng component DSL, không cần tính pixel.

### Acceptance Criteria:
- [ ] `UI.NewFrame()`, `UI.NewText()`, `UI.NewProgressBar()`, `UI.NewButton()`
- [ ] Component có `:SetPosition()`, `:SetSize()`, `:SetColor()`
- [ ] `:BindTo(object, property)` auto-update khi property thay đổi
- [ ] Component render đúng thứ tự (z-index)
- [ ] Test pass: tạo UI, verify render

### Files cần tạo:

#### 2.1 Module mới: module-ui
```
modules/module-ui/
├── build.gradle
└── src/main/java/com/luatweaker/ui/
    ├── IUIService.java           # Interface
    ├── UIServiceImpl.java         # Core logic
    ├── UILuaBinding.java          # LuaBinder
    └── components/
        ├── UIComponent.java       # Base class
        ├── UIFrame.java           # Container
        ├── UIText.java            # Text label
        ├── UIProgressBar.java     # Progress bar
        └── UIButton.java          # Clickable button
```

#### 2.2 common-api: Interface
**File:** `common-api/src/main/java/com/luatweaker/api/ui/IUIService.java` (TẠO MỚI)
```java
@LuaDoc(description = "UI Component System - WoW-style UI builder")
public interface IUIService {
    @LuaDoc(description = "Create a new frame container")
    Object newFrame();
    
    @LuaDoc(description = "Create a new text label")
    Object newText(String text);
    
    @LuaDoc(description = "Create a progress bar")
    Object newProgressBar(double value, double maxValue);
    
    @LuaDoc(description = "Create a clickable button")
    Object newButton(String label);
}
```

#### 2.3 Component DSL (Lua side):
```lua
local UI = require("LuaTweaker.UI")

-- Tạo mana bar
local manaBar = UI.NewProgressBar(player.Mana, player.MaxMana)
manaBar:SetPosition(10, 50)
manaBar:SetSize(120, 10)
manaBar:SetColor(0x4488FF)
manaBar:BindTo(player, "Mana")  -- Auto-update khi Mana thay đổi

-- Tạo text label
local label = UI.NewText("Hello World")
label:SetPosition(10, 70)
label:SetColor(0xFFFFFF)

-- Tạo button
local btn = UI.NewButton("Cast Spell")
btn:SetPosition(10, 90)
btn:OnClick(function()
    print("Button clicked!")
end)
```

#### 2.4 Render Integration
**File:** `modules/module-client/src/main/java/com/luatweaker/client/ClientLuaBinding.java` (SỬA)
- Hook vào `OnRenderHUD` event
- Render tất cả UI components theo z-index

#### 2.5 Tests
**File:** `modules/module-ui/src/test/java/com/luatweaker/ui/UIServiceTest.java` (TẠO MỚI)
```java
@Test
void testProgressBar_Creation() {
    // 1. Create progress bar
    // 2. Set position/size
    // 3. Verify properties
}

@Test
void testBindTo_AutoUpdates() {
    // 1. Create UI component
    // 2. BindTo property
    // 3. Change property
    // 4. Verify UI updated
}
```

#### 2.6 Documentation
**File:** `docs/UI_COMPONENT_GUIDE.md` (TẠO MỚI)

### Checklist khi hoàn thành:
- [ ] 5+ component types (Frame, Text, ProgressBar, Button, Image)
- [ ] All methods have @LuaDoc
- [ ] Tests pass (ít nhất 5 tests)
- [ ] Example mod demonstrates UI
- [ ] No dead code

---

## Task 3: Custom Dimension System

### Độ khó: ⭐⭐⭐⭐⭐ Khó nhất
### Thời gian: 13-19 ngày
### Mục tiêu:
Custom dimension với Lua terrain generator (fBm, ridged, domain warp, Voronoi).

### Acceptance Criteria:
- [ ] Module-noise: fBm, ridged, domain warp, Voronoi, simplex
- [ ] Module-dimensions: DimensionType registration, ChunkGenerator bridge
- [ ] Lua terrain callback: `function(x, z) return height, blockId end`
- [ ] Portal system: custom portal blocks
- [ ] Test dimension "Crystal Realm" hoạt động
- [ ] No crashes khi generate chunks

### Phase A: Module-Noise (3-4 ngày)

#### A.1 Files cần tạo:
```
modules/module-noise/
├── build.gradle
└── src/main/java/com/luatweaker/noise/
    ├── INoiseService.java
    ├── NoiseServiceImpl.java
    ├── NoiseLuaBinding.java
    └── internal/
        ├── SimplexNoise.java
        ├── FBMNoise.java
        ├── RidgedNoise.java
        ├── VoronoiNoise.java
        └── DomainWarp.java
```

#### A.2 API:
```lua
local Noise = require("LuaTweaker.Noise")

Noise.SetSeed(12345)

local height = Noise.fBm(x, z, 6, 2.0, 0.5)
local ridges = Noise.Ridged(x, z, 4)
local warped = Noise.DomainWarp(x, z, 50)
local crystal = Noise.Voronoi(x, z, 0.7)
```

#### A.3 Tests:
- 10+ tests cover mỗi noise function
- Benchmark: 1M evaluations < 2s
- Seed reproducibility

### Phase B: Module-Dimensions (7-10 ngày)

#### B.1 Files cần tạo:
```
modules/module-dimensions/
├── build.gradle
└── src/main/java/com/luatweaker/dimensions/
    ├── IDimensionService.java
    ├── DimensionServiceImpl.java
    ├── DimensionLuaBinding.java
    └── terrain/
        ├── TerrainGenerator.java
        └── LuaTerrainCallback.java

neoforge-platform/src/main/java/com/luatweaker/platform/dimension/
├── NeoForgeDimensionProvider.java
├── LuaChunkGenerator.java
├── LuaBiomeSource.java
├── PortalBlock.java
└── DimensionCommands.java
```

#### B.2 API:
```lua
local Dimensions = require("LuaTweaker.Dimensions")

Dimensions.Create("arcane:crystal_realm", {
    hasSkyLight = true,
    skyColor = 0xFF88FF,
    fogColor = 0xAADDFF,
    terrain = "custom"
})

Dimensions.SetTerrainGenerator("arcane:crystal_realm", function(x, z, baseHeight)
    local continent = Noise.fBm(x * 0.001, z * 0.001, 6, 2.0, 0.5)
    local mountains = Noise.Ridged(x * 0.005, z * 0.005, 4)
    local height = baseHeight + continent * 40 + mountains * 30
    return height, "arcane:crystal_stone"
end)

-- Portal entry
Dimensions.RegisterPortal("arcane:crystal_portal", "arcane:crystal_realm")
```

#### B.3 NeoForge Integration:
- `LuaChunkGenerator extends ChunkGenerator`
- Call Lua callback per chunk column
- Cache heightmap cho performance

#### B.4 Tests:
- Dimension creation
- Terrain generation (verify height range)
- Portal teleportation
- Chunk generation performance (20 TPS)

### Phase C: Crystal Realm Demo (3-5 ngày)

#### C.1 Custom content:
- 5+ custom blocks (crystal_stone, crystal_grass, etc.)
- 1 custom fluid (liquid_crystal)
- 3 custom biomes
- 1 custom entity (crystal_elemental)
- Portal frame structure

#### C.2 Terrain generator:
```lua
Dimensions.SetTerrainGenerator("arcane:crystal_realm", function(x, z, baseHeight)
    -- Layer 1: Continents
    local continent = Noise.fBm(x * 0.001, z * 0.001, 6, 2.0, 0.5)
    
    -- Layer 2: Mountain ridges
    local ridges = Noise.Ridged(x * 0.005, z * 0.005, 5)
    
    -- Layer 3: Domain warp
    local warp = Noise.DomainWarp(x * 0.002, z * 0.002, 80)
    
    -- Layer 4: Crystal formations
    local crystal = Noise.Voronoi(x * 0.05, z * 0.05, 0.7, 0)
    
    -- Combine
    local height = baseHeight + continent * 50 + ridges * 35
    if crystal < 0.05 then height = height + 20 end
    
    -- Block selection
    local block = "arcane:crystal_stone"
    if height < 70 then block = "arcane:crystal_dirt" end
    
    return height, block
end)
```

### Checklist khi hoàn thành:
- [ ] Noise module: 5 algorithms, benchmark pass
- [ ] Dimensions module: create + enter dimension
- [ ] Crystal Realm: playable dimension
- [ ] Portal: 2-way teleportation
- [ ] Performance: 20 TPS khi generate chunks
- [ ] 0 crashes trong 30 phút gameplay

---

## Tổng Kết

| Task | Độ Khó | Thời Gian | Priority |
|------|--------|-----------|----------|
| **1. Teardown Hooks** | ⭐ | 1-2 ngày | **HIGH** |
| **2. UI Component** | ⭐⭐⭐ | 5-7 ngày | **MEDIUM** |
| **3. Custom Dimension** | ⭐⭐⭐⭐⭐ | 13-19 ngày | **LOW** |
| **TOTAL** | | **19-28 ngày** | |

---

## Nguyên Tắc Implementation

### KHÔNG LÀM:
- ❌ Code trang trí (decorators, unused methods)
- ❌ Dead code (methods không bao giờ được gọi)
- ❌ Fake tests (tests luôn pass)
- ❌ Placeholder values (return 0, null, "")
- ❌ Hardcoded values (magic numbers)

### PHẢI LÀM:
- ✅ Mọi method đều có @LuaDoc
- ✅ Mọi function đều có test
- ✅ Mọi config đều từ `mod:GetConfig()`
- ✅ Mọi error đều log rõ ràng
- ✅ Compile thành công trước khi commit

---

## Tiến Độ

| Task | Status | Notes |
|------|--------|-------|
| 1. Teardown Hooks | ✅ Hoàn thành | 5 tests pass, mod.OnDisable() + Events:Listen("OnScriptUnload") |
| 2. UI Component | ⬜ Chưa bắt đầu | |
| 3. Custom Dimension | ⬜ Chưa bắt đầu | |

**Cập nhật status khi hoàn thành mỗi task.**

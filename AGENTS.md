# LuaTweaker ⚠️ READ THIS FIRST

Minecraft 1.21.1 NeoForge mod — Lua scripting engine with runtime recipe/content patching.

> **🚨 CRITICAL: Every AI agent MUST strictly follow ALL rules below. Violations cause bugs, broken builds, and SOLID violations.**

---

# ⚠️ CRITICAL SECTION 0 — Code Honesty & Delivery Integrity (Strictest — Zero Tolerance)

❌ **No hidden code. No sloppy code. No broken code. No silent stubs. No hallucinations. No fake tests. No leaked secrets. No inaccurate docs. No hardcoded values. Every delivered change MUST be complete, correct, and verified before submission. ANY violation below = instant rejection.**

---

## 0.1 — NO Hidden Code (Giấu Code) — Absolute Ban

| ❌ Forbidden | ✅ Required |
|---|---|
| Silently omitting logic while pretending the task is finished | Deliver the full, real implementation |
| Writing empty or no-op method bodies presented as "working" | Every method contains its actual business logic |
| Empty `catch` blocks that swallow exceptions silently | Catch → log + handle the error; NEVER hide failures |
| Hiding broken/failing code behind `@Deprecated`, `// TODO`, or "unused" flags | Fully implement it, or explicitly report it as incomplete |
| Commenting out logic so the code "compiles" or "passes" | Real logic that compiles and actually works |
| Reusing old results/cache as a fake success while new code is missing | Fresh, real execution path that produces real output |
| Registering a feature that only logs and discards its data (e.g. a config table that is never read) | Wire the registered data to real behavior, or do not register it |

> **Lesson from the codebase**: `registerProjectile` was once a log-only stub — the `damage`/`explosionPower` config was thrown away. It is now a real registry (`ProjectileRegistry`) consumed by the firing layer. Any "registration" API must have a consumer.

## 0.2 — NO Sloppy / Careless Code (Code Cẩu Thả) — Absolute Ban

- **No dead code**: unused imports, unused fields, unused methods, unused local variables, unused parameters.
- **No placeholder values**: returning `0`, `null`, `""`, `false`, or hardcoded constants as if they were real results.
- **No copy-paste duplication**: duplicate logic must be extracted or the change is rejected. (Two parallel projectile builders, two parallel entity wrappers, and repeated 12-line binding glue have all been removed — do not reintroduce them.)
- **No leftover markers**: `// TODO`, `// FIXME`, `// HACK`, `// WIP`, `// XXX` left in delivered code.
- **No fake signals**: every log line, `print()`, and return value must reflect the real behavior.
- **No lazy shortcuts**: no hardcoded paths, no magic string switching instead of real dispatch, no bypassing the module/builder architecture in Section 1.
- **No duplicate API surfaces**: the SAME concept must not exist twice under different names (e.g. two entity wrapper APIs). See Section 5.

## 0.3 — NO Non-Functional / Broken Code — Absolute Ban

- **Every change MUST compile.** Run the build before submitting — never hand over code you did not compile.
- **Every change MUST pass tests.** Add tests for all new behavior; never ship untested logic.
- **Every exposed API must work end-to-end**: Java interface → binding → Lua script → runtime effect.
- **No runtime crashes** in default-reachable paths: no NPEs, no `ClassCastException`, no illegal state reached by normal usage.
- **Never merge code you have not actually run and verified** (build + tests + relevant runtime path).

## 0.4 — NO Silent Stubs — Absolute Ban

> **A "stub" is any code that is NOT the real implementation.** Using a stub is **STRICTLY FORBIDDEN** unless ALL of the following hold:

1. The stub is explicitly disclosed to the user, with the exact file and line.
2. The stub is loudly marked in code (`throw new UnsupportedOperationException("STUB <reason>")`, `assert false : "STUB ..."`, or `log.warn("STUB ...")`).
3. A follow-up task is recorded, and the user is told it remains unfinished.

| ❌ Forbidden | ✅ Required |
|---|---|
| Returning a stub silently as if the feature were done | Disclose it clearly + mark it + log the follow-up |
| `Mock*` classes used in production code paths | Mocks exist only in tests — never in runtime |
| `return null` / `return 0` / blank `{}` as a "complete" feature | Complete, working behavior or explicit disclosure |
| A binding method that only writes a log line instead of doing its job | Real behavior, or remove the method |

## 0.5 — NO Hallucinations / Fabricated Code — Absolute Ban

- **Verify every symbol exists** in the exact library/project version (read the real source — e.g. `javap` the NeoForge jar — before calling a Minecraft API).
- **No invented signatures**, phantom return types, or imagined registry entries.
- **State uncertainty, do not guess**: if you are not 100% sure a symbol/behavior exists, SAY SO.

## 0.6 — NO Fake Tests / Fake Coverage — Absolute Ban

- Tests that assert nothing, always pass, or never run = rejection.
- Cover success path **and** error/failure paths, plus edge cases (nil, empty, min/max, malformed input).
- Every delivered function MUST have at least one unit test.
- Run `./gradlew test` — all relevant tests MUST be green. Report actual pass/fail output.

## 0.7 — NO Hidden Secrets / Credentials — Absolute Ban

Never commit or embed API keys, tokens, passwords, database URLs — even "placeholder" ones. Scan every diff.

## 0.8 — NO Inaccurate Comments / Documentation — Absolute Ban

- Comments must describe what the code ACTUALLY does. Delete stale comments.
- `@LuaDoc`, `@param`, `@return`, JavaDoc must match the real signature and behavior.
- README claims must be TRUE: if a feature does not exist, do not advertise it.

## 0.9 — Complete Codebase Awareness (Mandatory Before Any Change)

- Read the surrounding code, dependencies, imports, and existing implementations before editing.
- Respect existing patterns: module/builder architecture, Roblox-style Lua conventions, LuaBinder auto-binding (Section 5).
- Check environment parity: Java 21, NeoForge 21.1.242, Cobalt 0.9.9, Luau-style Lua.

## 0.10 — NO Silent Errors — Loud, Honest Failure Reporting

- Report every error, warning, and limitation at the exact moment it happens — WHAT failed, WHERE (file:line), WHY, WHAT was done.
- **Unverified ≠ verified.** Label unverifiable steps UNVERIFIED — never quietly upgrade them to "done".
- When uncertain, say "I don't know".

## 0.11 — NO Rule Evasion / Loopholing — Absolute Ban

The SPIRIT of each rule is as binding as its LETTER. No technical bypasses, no partial truths, no scope-shrinking.

## 0.12 — Error Handling in Delivered Code — Loud, Never Silent

- **No empty `catch` blocks. Ever.** Every `catch` logs with context and takes visible corrective action or rethrows.
- Errors must surface through the engine logger; failed Lua/Java calls must be reported.
- The error path must be tested (at least one test asserting the failure path throws/logs).

## 0.13 — Self-Review Checklist — Before Declaring Anything Done

1. Did I compile? What was the exact output?
2. Did I run the tests? How many passed / failed / skipped?
3. Did I loudly report every error, warning, and UNVERIFIED step?
4. Did I follow the letter **and** the spirit of every rule?
5. Did I name every file changed and every behavior added?
6. Am I claiming anything I cannot prove? If yes → label it UNVERIFIED.

## 0.14 — NO Hardcoding — Absolute Ban

- Absolute paths, magic numbers, tunable values (durations, costs, cooldowns, damage, thresholds) → config (`default_config.json` → `luaconfig/<mod_id>.json`) or named `static final` constants.
- Version/platform literals → `gradle.properties` expansion.
- Lua scripts: skill costs, cooldowns, spawn positions, regen rates MUST come from `mod:GetConfig()` with a sane fallback.
- Tests MAY hardcode expected values in assertions — production code never does.

## 0.15 — Verification Gate (Mandatory Before "Done")

1. **Plan → Code → Test → Verify → Report.**
2. **Compile**: `./gradlew build` — MUST pass.
3. **Test**: `./gradlew test` — all relevant tests MUST be green.
4. Self-review your diff: no hidden files, no stubs, no dead code, no fabricated symbols, no secrets.
5. Report honestly: what works, what is untested, known limitations.

---

# ⚠️ CRITICAL SECTION 1 — LuaTweaker Style & Quy Trình Tạo Module Mới

> **🚨 BẢN SẮC THIẾT KẾ: TƯ DUY LẮP RÁP (MODULE & BUILDER) + NGỮ PHÁP TỰ NHIÊN CỦA LUA**

## 1. Bản Sắc Thiết Kế LuaTweaker Style

1. **Content & Recipe Definitions (Đăng ký tĩnh):**
   - Chainable Builder Pattern theo Namespace: `Content.NewItem("id")`, `Content.NewBlock("id")`, `Content.createEntity("id", fn)`, `Recipe.Shaped("id"):Pattern(...):Register()`.
   - **CẤM** hàm global trôi nổi `item()`, `ingredient()` làm API chính (chúng chỉ là helper trong recipes).

2. **Runtime Services & AI Logic:**
   - Service/Signal PascalCase: `Events:Listen(...)`, `Task:delay(...)`, `World:StrikeLightning(...)`, `Vector3.new(...)`.
   - Entity API **thống nhất**: một bảng entity duy nhất hỗ trợ CẢ method style (`entity:setHealth(50)`) lẫn property style (`entity.Health = 50`) — xem Section 5.

3. **Nạp Thư Viện Tường Minh:**
   - Mọi script nạp thư viện bằng `require("LuaTweaker.ModuleName")`.
   - **CẤM** magic globals trôi nổi (trừ `mod`, `Mod`, `Events`/`Content`/... do engine cung cấp).

4. **Cấu trúc 1 LuaMod:**
   - `luamods/<mod_id>/manifest.json` + `main.lua` (entrypoint duy nhất) + `src/startup`, `src/server`, `src/client` + `default_config.json` + `assets/`, `data/`.

---

## 2. Quy Trình 5 Bước Tạo 1 Module Mới Chuẩn OOP

### Bước 1: Khai báo Java Interface trong `common-api` với `@LuaDoc` (+ `@LuaDefault` cho tham số tùy chọn)
```java
package com.luatweaker.api.example;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.annotation.LuaDefault;

@LuaDoc(description = "Dịch vụ quản lý mô-đun ví dụ.")
public interface IExampleModuleService {
    @LuaDoc(
        description = "Thực hiện chức năng ví dụ.",
        params = {"id: string", "power: number"},
        returnType = "void"
    )
    void executeFeature(String id, @LuaDefault("1.0") double power);
}
```
> Interface là **single source of truth**: binding + EmmyLua stubs + docs đều sinh từ nó. KHÔNG viết binding tay.

### Bước 2: Viết Implementation độc lập trong `modules/module-<feature>`
```java
public class ExampleModuleServiceImpl implements IExampleModuleService {
    @Override
    public void executeFeature(String id, double power) {
        // Business logic
    }
}
```

### Bước 3: Đăng ký binding bằng **LuaBinder** — KHÔNG viết glue tay
```java
public class ExampleModuleLuaBinding {
    public static void registerBindings(ILuaEngine engine, IExampleModuleService service) {
        LuaBinder.bind(engine, "ExampleModule", service, IExampleModuleService.class);
    }
}
```
`LuaBinder` (core-engine) tự động: convert tham số (String/int/double/boolean/table/IEntity), bỏ self của colon-call, sinh alias PascalCase, áp dụng `@LuaDefault`, ném lỗi rõ khi thiếu tham số bắt buộc, convert giá trị trả về. **CẤM viết tay `getOffset` + `args[i].asInt()` + delegate** trong binding mới.

### Bước 4: Đăng ký trong `LuaServiceBootstrap` (+ stub trong `LuaTweakerMod.generateStubs`)
```java
ExampleModuleLuaBinding.registerBindings(engine, exampleService);
stubGen.registerService("ExampleModule", IExampleModuleService.class);
```
> Entity/player wrapper classes: `stubGen.registerClassStub(IEntity.class, "Entity")` — sinh class stub có inheritance (`---@class Player: Entity`).

### Bước 5: Sử dụng trong Lua Script chuẩn LuaTweaker Style
```lua
local ExampleModule = require("LuaTweaker.ExampleModule")
ExampleModule:ExecuteFeature("custom_id", 100)
```

---

# ⚠️ CRITICAL SECTION 2 — SOLID Principles (Absolute Compliance Required)

| Principle | Enforcement |
|---|---|
| **S**ingle Responsibility | Mỗi class/module/method có ĐÚNG MỘT lý do thay đổi. `common-api` = abstraction thuần; `core-engine` = VM wrapper + LuaBinder + logger + stubs; `modules/*` = đúng một domain; `neoforge-platform` = bootstrap + PAL impl. |
| **O**pen/Closed | Thêm tính năng = thêm code (binding mới, service mới), KHÔNG sửa code đang chạy. `InterceptionHelper` closed; `LuaServiceRegistry` mở rộng qua `register()`. |
| **L**iskov | Mọi PAL impl thay thế được cho base type. Test với `Mock*` phải xanh CÙNG impl thật. |
| **I**nterface Segregation | Interface nhỏ, đúng nhu cầu từng client. Nếu một impl phải stub method → TÁCH interface ngay. |
| **D**ependency Inversion | Chỉ `neoforge-platform` import NeoForge/Minecraft. `common-api` zero-dependency. Module graph là DAG. |

---

# ⚠️ CRITICAL SECTION 3 — Java Coding Conventions

- **Java 21**: `record` cho DTO bất biến, sealed hierarchy, pattern matching switch, TextBlock, `Optional` CHỈ cho return nullable.
- **@NotNull/@Nullable trên MỌI tham số và return.**
- **Naming**: packages `com.luatweaker.<module>.<feature>` (không plural), class PascalCase, method camelCase (một verb), constants `UPPER_SNAKE_CASE`, boolean `is*/has*/can*`.
- **Encapsulation**: fields `private final`, collection getters trả `List.copyOf()`/unmodifiable, builder cho 3+ params, **KHÔNG mutable statics** (trừ registry có chủ đích như `LuaServiceRegistry`, `EventServiceImpl`, `ProjectileRegistry` — phải có lý do + comment).
- **Exceptions**: catch specific, KHÔNG blanket `catch (Exception e)`; try-with-resources; error path phải test.
- **Braces K&R, 4 spaces, không tab, max 120 chars, imports không wildcard, `@Override` bắt buộc.**

---

# ⚠️ CRITICAL SECTION 4 — Cross-Platform Architecture (PAL)

- `core-engine` + `modules/*` **CẤM** import `net.minecraft.*`, `net.neoforged.*` — REJECTED.
- Mọi platform call → `Platform.getInstance().<method>()` / `Platform.getX()`.
- Capability mới → thêm vào `Platform` interface + impl trong `NeoForgePlatform` — zero change cho consumers.
- Test không cần Minecraft (mock qua interface).

---

# ⚠️ CRITICAL SECTION 5 — Anti-Boilerplate & Runtime Architecture Rules (MỚI — Bắt Buộc)

> Những luật này được rút ra từ các bug thực tế đã sửa. Vi phạm = tái sinh bug cũ.

## 5.1 — Binding bắt buộc qua LuaBinder (CẤM glue tay)

- Mọi service binding MỚI: `LuaBinder.bind(engine, name, impl, apiClass, aliases...)` hoặc `LuaBinder.bindTable(...)` cho wrapper tables.
- Tham số tùy chọn → `@LuaDefault` trên interface — KHÔNG tự viết default logic trong binding.
- **CẤM** copy pattern cũ: `int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;` + `args[off].asInt()` + delegate.
- Ngoại lệ (có lý do hợp lệ): builder DSL (`ContentLuaBinding`), parsing phức tạp (`RecipesLuaBinding`), wrapper có logic riêng (`StorageLuaBinding`, `NetworkLuaBinding`).

## 5.2 — MỘT Entity API duy nhất (CẤM 2 wrapper song song)

- `IEntity` là wrapper duy nhất. `NeoForgeEntityWrapper` + `NeoForgePlayerWrapper` là impl; `NeoForgeInteractableEntity implements IEntity` (không có view thứ 2).
- Lua table entity/player: `EntitiesLuaBinding.createEntityLuaTable` / `createPlayerLuaTable` — method style + property aliases (Health, MaxHealth, Type, CustomName, Velocity, Position) qua metatable.
- **CẤM** tạo API entity thứ 2 (kiểu `wrapEntity` cũ với method khác tên: `ShootProjectile` vs `shootProjectile`).
- Projectile: **MỘT nơi duy nhất** bắn — `EntityInteractionHelper.shootProjectile/shootProjectileAt` + `NeoForgeInteractableEntity` override delegate về helper. **CẤM** 2 bảng switch tạo projectile độc lập.

## 5.3 — Event Bus: shared + replace semantics

- `EventServiceImpl` dùng **một map dùng chung mọi engine**; `Listen` **THAY THẾ** listener cũ (engine mới nhất thắng) — không tích lũy.
- Lý do: item handler closure bị pin vào startup engine — dispatch qua bus shared để tới listener của runtime engine hiện tại.
- **CẤM** đưa listener map về per-instance tích lũy (gây double-fire) hoặc clear sai thời điểm.

## 5.4 — Item/Block handler CHỈ dispatch server-side

- `ItemRegistrar` / `BlockRegistrar`: Lua handler chỉ chạy khi `!level.isClientSide()` — client chỉ consume click (`sidedSuccess`).
- **CẤM** dispatch Lua event ở cả 2 phía — client-side cast sẽ trừ mana/đặt cooldown sai + không thể bắn projectile (`ClientLevel`).

## 5.5 — Thread Safety: VM entry points synchronized

- `CobaltLuaEngine.executeString/executeScript/callFunction` là `synchronized` — VM Cobalt không thread-safe (render thread + server thread).
- Render callbacks (OnRenderHUD) phải dùng `Signal:FireSync` (đồng bộ, đúng render thread với GuiGraphics context) — **CẤM** async Fire cho render.
- Coroutine/deferred: chạy qua `task._run_deferred` (Java, có Cobalt loop) — **CẤM** `coroutine.resume` trực tiếp trong `task._tick`.

## 5.6 — Network: clientbound → network service của ACTIVE engine

- Clientbound packet → `LuaServiceRegistry.get("NetworkServiceImpl")` (engine active, nơi mods load + listener connect) — KHÔNG dùng service của client engine (không bao giờ load mods → remoteEvents rỗng → HUD/effects chết).

## 5.7 — Stub Generator: inheritance + class cho wrapper

- `generateClassStub` phải emit `---@class X: Parent` cho interface kế thừa; transitive scan dùng `classNameByType`.
- Entity/player: `registerClassStub(IEntity.class, "Entity")`, `registerClassStub(IPlayer.class, "Player")`.

## 5.8 — Config-driven mọi tunable

- Skill cost/cooldown/regen, boss health, spawn offsets, GUI geometry → `default_config.json` → `mod:GetConfig()` với fallback hợp lý.
- **CẤM** hardcode trong Lua script (AGENTS.md 0.14).

## 5.9 — Log ASCII thuần

- Log output (log file + console) dùng ASCII: `=== BEGIN RELOAD ===`, `->`, `-`. **CẤM** box-drawing/em-dash/arrow unicode trong log strings.

## 5.10 — Dynamic Bridge Architecture (Hybrid Proxy Pattern)

- **DynamicJavaProxy** (`core-engine/src/main/java/com/luatweaker/core/bind/DynamicJavaProxy.java`) cung cấp automatic property/method access cho Java objects trong Lua.
- **Cơ chế:**
  - `__index`: `obj.Health` → tự động gọi `getHealth()` hoặc `isHealth()`
  - `__newindex`: `obj.Health = 20` → tự động gọi `setHealth(20)`
  - Method call: `obj:teleport(0, 100, 0)` → exact method name match
- **Security model - Smart Package Blacklist (CRITICAL):**
  - **3 Bức Tường Phòng Ngự:**
    1. **Cobalt VM Isolation:** Tước bỏ `os.*`, `io.*`, `package.loadlib` khỏi Lua VM.
    2. **Method Blacklist:** Block `getClass`, `wait`, `notify`, `notifyAll`, `clone`, `finalize`, `hashCode`.
    3. **Smart Package Blacklist:** Block `java.lang.Runtime`, `java.lang.Process`, `java.lang.ProcessBuilder`, `java.lang.System`, `java.lang.Thread`, `java.lang.Class`, `java.lang.ClassLoader`, `java.lang.reflect.*`, `java.lang.invoke.*`, `java.io.*`, `java.nio.*`, `java.net.*`, `javax.net.*`, `sun.*`, `com.sun.*`, `jdk.*`.
  - **Cross-mod interop:** Mọi package KHÔNG nằm trong blacklist đều được phép (GregTech, Create, Mekanism...).
  - Check thứ tự: package blacklist → method blacklist → cache → reflection → remapper.
- **Runtime Remapper (`core-engine/src/main/java/com/luatweaker/core/remap/RuntimeRemapper.java`):**
  - Giải quyết obfuscation trong production (Mojmap → SRG: `getHealth` → `m_21223_`).
  - **3 tầng fallback:**
    1. **Exact match:** Tìm method đúng tên (hoạt động trong dev).
    2. **Heuristic match:** Case-insensitive + contains pattern (tìm methods tương tự).
    3. **Signature match:** Tìm theo param types + return type (fallback cuối cùng).
  - **Cache:** `ConcurrentHashMap` cho cả exact và signature results.
  - **Obfuscation detection:** Tự động detect dev vs production environment.
  - Tích hợp vào `DynamicJavaProxy.findMethodCached()` - gọi khi exact match thất bại.
- **Integration points:**
  - `UniversalEventForwarder`: wrap NeoForge events → Lua (auto proxy)
  - `EntitiesLuaBinding`: fallback proxy cho unknown entity properties
  - `InteractionLuaBinding`: fallback proxy cho unknown block/item properties
- **Khi nào dùng:**
  - ✅ Event objects (raw NeoForge events)
  - ✅ Modded properties không có trong manual wrapper
  - ✅ Arbitrary Java objects từ interop (cross-mod: GregTech, Create, Mekanism)
  - ❌ KHÔNG dùng cho core APIs đã có manual wrapper (Entity/Player/Block/Item hot paths)
- **Performance:** Cache `ConcurrentHashMap<Class<?>, Map<String, Method>>` cho method lookup. Benchmark target: < 2x manual wrapper.

---

# Project Structure (Hiện Tại)

| Module | Role | Depends On |
|---|---|---|
| `common-api/` | Pure Java 21: PAL, VM interfaces, `@LuaDoc`/`@LuaDefault`, object wrappers | *(none)* |
| `core-engine/` | Cobalt wrapper, **LuaBinder**, **DynamicJavaProxy**, async logger (ASCII), linter, stub generator, LuaModManager | `common-api` |
| `modules/module-content/` | Content builders (items/blocks/fluids/entities/tabs/keybinds), ProjectileRegistry | `common-api`, `core-engine` |
| `modules/module-recipes/` | Recipe manipulation + builder DSL | `common-api`, `core-engine` |
| `modules/module-events/` | Shared event bus (replace semantics) | `common-api`, `core-engine` |
| `modules/module-entities/` | Unified entity/player tables, AI goals, LuaBinder bindings | `common-api`, `core-engine` |
| `modules/module-interaction/` | World/block interaction, entity wrapping | `common-api`, `core-engine`, `module-entities` |
| `modules/module-network/` | Remote events/functions | `common-api`, `core-engine` |
| `modules/module-client/` | GuiService, ClientEffects, keybinds, RunService | `common-api`, `core-engine` |
| `modules/module-storage/` | World/player/session data stores | `common-api`, `core-engine` |
| `modules/module-tasks/` | Task scheduler bridge | `common-api`, `core-engine` |
| `modules/module-math/` | Vector3/Vector2/Color3, math/string extensions | `common-api`, `core-engine` |
| `modules/module-interception/` | Anvil/brewing/trade interception | `common-api`, `core-engine` |
| `modules/module-loot/` | Loot table manipulation (mob drops, chest loot, block drops, fishing) | `common-api`, `core-engine` |
| `neoforge-platform/` | **Only runnable module**. NeoForge launcher, registrars, PAL impl, commands | all of the above + NeoForge |

Entrypoint: `com.luatweaker.platform.LuaTweakerMod` (`@Mod("luatweaker")`).

---

# Build & Run

```sh
# full build (CI)
./gradlew build

# dev build + asset sync (fast iteration)
./gradlew :neoforge-platform:classes :neoforge-platform:syncLua

# run client or server
./gradlew :neoforge-platform:runClient
./gradlew :neoforge-platform:runServer

# all tests
./gradlew test

# single test class
./gradlew :modules:module-recipes:test --tests *EngineTest
```

Java 21 (Temurin), Gradle wrapper, NeoForge 21.1.242. CI: `.github/workflows/build.yml`.

---

# LuaMod Directory (Game Run Dir)

- `luamods/<mod_id>/manifest.json` — identity + metadata (id, name, author, version, main, permissions)
- `luamods/<mod_id>/default_config.json` — defaults copied to `luaconfig/<mod_id>.json`
- `luamods/<mod_id>/main.lua` — single autonomous entrypoint (requires `src/startup`, `src/server`, `src/client`)
- `luamods/<mod_id>/assets/` + `data/` — auto-mounted virtual resourcepack & datapack roots
- `.luatweaker/stubs/` — auto-generated EmmyLua stubs (class inheritance included)
- `logs/luatweaker/mods/<mod_id>.log` — dedicated per-mod engine output

Dev workspace: `neoforge-platform/luamods/` → synced to `run/luamods/` by `syncLuaMods`.

---

# Lua Script Style — Roblox (Luau) Convention

- `local` mọi biến; **CẤM** implicit globals (trừ API engine cung cấp).
- Colon method: `object:method()`, chain tối đa ~3.
- Banner `-- ==== SECTION ====`, 4 spaces indent, max 120 chars, break trước operator.
- EmmyLua annotations cho hàm public: `---@param player Player`, `---@return boolean` (dùng class stub `Player`/`Entity`).
- Naming: local `camelCase`, API export `PascalCase`, constants `UPPER_SNAKE_CASE`, boolean `is*/has*/can*`, callback `on*`.
- **CẤM**: `goto`, biến 1 ký tự (trừ `i`), concat chuỗi lớn (dùng `[[ ]]` / `table.concat`).
- Thứ tự script: banner → `require` → config (`mod:GetConfig()`) → constants → private functions → public API → final `print()`.

---

# In-Game Commands (Thực Tế)

- `/lt reload` — hot-reload Lua, regenerate stubs, re-apply recipes
- `/lt doctor` — health diagnostics: mods + per-mod load errors + engine/service status
- `/lt hand` — inspect held item/block
- `/lt syntax <file>` — syntax check
- `/lt list` — list loaded mods/scripts
- `/lt debug [on|off]` — toggle debug logging

---

# Runtime Architecture Notes (Bắt Buộc Nắm)

- **PAL** (`Platform`) — singleton abstraction, set một lần tại mod init.
- **LuaBinder** — auto-binding từ interface; interface là single source of truth (binding + stubs + docs).
- **Event bus** — shared map, replace semantics, cross-engine dispatch.
- **Item/Block handlers** — server-side only dispatch.
- **`CobaltLuaEngine`** — synchronized VM entry points; `getAndClearLastExecutionError()` cho per-mod load error tracking; `task._run_deferred` chạy coroutine đúng Cobalt loop.
- **`Signal:FireSync`** — đồng bộ, dùng cho render; `Signal:Fire` — async qua task.
- **Clientbound network** — dispatch qua network service của active engine.
- **ProjectileRegistry** — custom projectile definitions thật (damage/explosionPower được áp dụng).
- **Lua execution stages**: `startup` (content registration) → `server` (runtime) → `client` (HUD/keybind) — tất cả qua `main.lua` require; reload dựng engine mới + re-load mods.

---

# Test Quirks

- Tests dùng `Mock*` interface — không cần Minecraft runtime.
- `AsyncFileLogger` shutdown trong `@AfterAll`.
- `EngineTest.findScriptFile()` — relative path + fallback `../../` cho IDE vs CLI.
- Mỗi module có `build.gradle` riêng với junit deps; module mới cần thêm test deps nếu chưa có.

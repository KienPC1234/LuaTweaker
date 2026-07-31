# LuaTweaker ⚠️ READ THIS FIRST

Minecraft 1.21.1 NeoForge mod — Lua scripting engine with runtime recipe/content patching.

> **🚨 CRITICAL: Every AI agent MUST strictly follow ALL rules below. Violations cause bugs, broken builds, and SOLID violations.**

---

# ⚠️ CRITICAL SECTION 0 — LuaTweaker Style & Quy Trình Tạo Module Mới

> **🚨 BẢN SẮC THIẾT KẾ: TƯ DUY LẮP RÁP (MODULE & BUILDER) + NGỮ PHÁP TỰ NHIÊN CỦA LUA**

## 1. Bản Sắc Thiết Kế LuaTweaker Style (Bắt Buộc Cum Compliance)

1. **Content & Recipe Definitions (Đăng ký tĩnh):**
   - Sử dụng **Chainable Builder Pattern** phân nhóm rõ ràng theo Namespace (`Content.NewItem("id")`, `Content.NewBlock("id")`, `Recipe.Shaped("id")`), kết thúc bằng `:Register()`.
   - **CẤM** sử dụng các hàm global trôi nổi như `item()`, `ingredient()`, `recipes:addShaped()`.

2. **Runtime Services & AI Logic (Vận hành Game):**
   - Quản lý bởi các Service & Signal chuẩn PascalCase (`Events.OnEntityDamaged:Connect(...)`, `Task.Delay(...)`, `World:StrikeLightning(...)`).
   - Sử dụng kiểu dữ liệu `Vector3.new(x, y, z)` cho tọa độ không gian.

3. **Nạp Thư Viện Tường Minh:**
   - Mọi script phải nạp thư viện tường minh bằng `require("LuaTweaker.ModuleName")`.
   - **CẤM** phụ thuộc vào biến toàn cục ma thuật trôi nổi (magic globals).

---

## 2. Quy Trình 5 Bước Tạo 1 Module Mới Chuẩn OOP

Khi thêm bất kỳ tính năng hoặc mô-đun mới nào vào dự án, **BẮT BUỘC** phải tuân thủ 5 bước chuẩn hóa sau:

### Bước 1: Khai báo Java Interface trong `common-api` với `@LuaDoc`
Tạo interface mô tả dịch vụ trong `common-api/src/main/java/com/luatweaker/api/<feature>/` và đánh dấu `@LuaDoc`:
```java
package com.luatweaker.api.example;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Dịch vụ quản lý mô-đun ví dụ.")
public interface IExampleModuleService {
    @LuaDoc(
        description = "Thực hiện chức năng ví dụ.",
        params = {"id: string", "power: number"},
        returnType = "void"
    )
    void executeFeature(String id, double power);
}
```

### Bước 2: Viết Implementation độc lập trong `modules/module-<feature>`
Triển khai interface trong mô-đun tương ứng (không phụ thuộc platform NeoForge/Minecraft):
```java
public class ExampleModuleServiceImpl implements IExampleModuleService {
    @Override
    public void executeFeature(String id, double power) {
        // Business logic
    }
}
```

### Bước 3: Tạo Lua Binding Binder
Viết binder ánh xạ phương thức Java sang `ILuaTable` trong `modules/module-<feature>`:
```java
public class ExampleLuaBinding {
    public static void registerBindings(ILuaEngine engine, IExampleModuleService service) {
        ILuaTable tbl = engine.createTable();
        tbl.set("ExecuteFeature", (state, args) -> {
            String id = args.arg(1).toString();
            double p = args.arg(2).toDouble();
            service.executeFeature(id, p);
            return engine.nilValue();
        });
        engine.getGlobalEnvironment().set("ExampleModule", tbl);
    }
}
```

### Bước 4: Đăng ký trong `LuaServiceBootstrap` & `LtvmStubGenerator`
Trong `neoforge-platform/src/main/java/.../LuaServiceBootstrap.java` và `LuaTweakerMod.java`:
```java
// Native Java Binding & OOP Reflection Stub Registration:
ExampleLuaBinding.registerBindings(engine, exampleService);
stubGen.registerService("ExampleModule", IExampleModuleService.class);
```
> 💡 `LtvmStubGenerator` sẽ **tự động dùng Reflection** sinh ra toàn bộ stubs EmmyLua (`---@class ExampleModule`, `---@overload fun(modName: 'LuaTweaker.ExampleModule'): ExampleModule`) cho VSCode & IntelliJ mà không cần sửa code engine!

### Bước 5: Sử dụng trong Lua Script chuẩn LuaTweaker Style
```lua
local ExampleModule = require("LuaTweaker.ExampleModule")
ExampleModule:ExecuteFeature("custom_id", 100)
```

---

# ⚠️ CRITICAL SECTION 1 — SOLID Principles (Absolute Compliance Required)

❌ **Every violation below = rejection. These are non-negotiable.**

---

## S — Single Responsibility Principle

> **Every class, module, and method must have *exactly one* reason to change.**

| Module | Single Responsibility | Forbidden |
|---|---|---|
| `common-api/` | Define pure abstractions (interfaces, annotations). Zero dependencies. | NO logic, NO implementations, NO platform imports |
| `core-engine/` | Wrap Cobalt Lua engine, logger, linter, stub gen. | NO Minecraft/NeoForge imports. NO recipe logic. |
| `modules/*/` | Each module owns **exactly one** domain (recipes, events, etc.). | NO cross-domain coupling. NO platform code. |
| `neoforge-platform/` | NeoForge bootstrap + concrete PAL implementation. | NO engine logic. NO business logic. Bootstrap only. |

**Break-down rule**: if a class does more than its name implies → **refactor immediately**. If you need "and" in the class description, it fails S.

---

## O — Open/Closed Principle

> **Modules open for extension, closed for modification.**

| Rule | Enforcement |
|---|---|
| **PAL** (`Platform` interface) | New platform feature = extend interface. Never modify consumers. |
| **Service Registry** | New Lua service = call `registerService()`. Never touch registry core. |
| **Recipe interception** | New recipe type = extend visitor. `InterceptionHelper` is closed. |
| **No `instanceof`** | Never check platform types in common/engine code. Use PAL only. |

**Golden rule**: You add new code, you NEVER change existing working code.

---

## L — Liskov Substitution Principle

> **Subtypes must be replaceable for their base types without altering correctness.**

- Every PAL impl must be **fully substitutable**. No platform assumptions in contracts.
- `MockRecipeService` proves test-side substitutability.
- Overrides must honor **preconditions, postconditions, invariants**.
- **NO** `UnsupportedOperationException` in PAL impls unless the interface documents it.
- If tests pass with `Mock*` but fail with real impl → L violation.

---

## I — Interface Segregation Principle

> **No client depends on methods it does not use.**

- `common-api` has **separate** interfaces: `Platform`, `ILuaEngine`, `ILuaService`, `ILogger`, etc.
- NOT one monolithic "API" class.
- If an implementing class is forced to stub a method → **split the interface NOW**.
- Service interfaces in modules consumed only by their relevant clients.

---

## D — Dependency Inversion Principle

> **Depend on abstractions, not concretions. High-level modules never depend on low-level modules.**

```
common-api (zero deps)       ← ALL abstractions live here
    ↑
core-engine + modules/*      ← depend ONLY on common-api + Cobalt libs
    ↑
neoforge-platform            ← ONLY module that imports NeoForge
```

| Rule | If violated |
|---|---|
| `common-api` has **zero dependencies** | Build fails with classpath leak |
| `core-engine` imports `net.minecraft.*` | **REJECTED** — platform leak |
| `modules/*` imports `net.neoforged.*` | **REJECTED** — platform leak |
| PAL set once at init: `Platform.setInstance()` | No service locator allowed |
| Module dependency graph must be a **DAG** | Circular deps = build error |

---

# ⚠️ CRITICAL SECTION 2 — Java Coding Conventions (Every Line Matters)

❌ **Code style violations cause CI failures. Review each line.**

---

## Language Level — Java 21 (Mandatory Feature Usage)

| Feature | ✅ Correct Usage | ❌ Wrong Usage |
|---|---|---|
| `record` | Immutable DTOs, configs, results | Mutable state, complex logic |
| `sealed class/interface` | Restricted type hierarchies | Open hierarchies that should be sealed |
| `switch` + pattern matching | Exhaustive type-based dispatch (`if-else` replacement) | Non-exhaustive, missing default |
| `TextBlock` | Multi-line Lua scripts, JSON, SQL | Single-line strings |
| `Optional` | Return type for **nullable** results **only** | Fields, parameters, collections |
| `Stream`/`Collectors` | Inline collection transforms | Hot paths, complex nested pipelines |
| `var` | Local vars where type is obvious (`var list = new ArrayList<String>()`) | API return types, method params, ambiguous code |
| `@NotNull/@Nullable` | **Every single parameter and return type** | Missing annotations |
| `Instant`/`Duration` | All time handling | `long` millis, `Date`, `Calendar` |

---

## Naming & Structure — Strict Rules

| Element | Rule |
|---|---|
| **Packages** | `com.luatweaker.<module>.<feature>`. **No plurals** (`api` not `apis`). |
| **Classes** | PascalCase, noun/noun phrase. Interfaces: `Runnable`-style or `I` prefix (consistent per module). |
| **Methods** | camelCase, verb/verb phrase. **One verb per method.** |
| **Constants** | `UPPER_SNAKE_CASE`. `static final`. Utility classes: `final class` + private constructor. |
| **Fields** | `private final` default. Expose via getters (immutable) or builder. |
| **Booleans** | `is*`, `has*`, `can*` prefix. Never `get*` for booleans. |

---

## Encapsulation & Immutability — Zero Exceptions

| Rule | Enforcement |
|---|---|
| All fields `private` | `public` only for constants and API |
| Prefer `final` + constructor injection | No setters |
| Collection getters | Return `Collections.unmodifiable*()` or `List.copyOf()`. Never expose raw mutable collections. |
| Builder pattern | For objects with 3+ construction params |
| No mutable statics | **Absolutely forbidden** |

---

## Exception Handling — Never Break These

| ✅ Do | ❌ Never Do |
|---|---|
| Catch specific checked exceptions | `catch (Exception e)` — **blanket catch forbidden** |
| `IllegalArgument/StateException` for programming errors | Swallowed exceptions |
| Custom `RuntimeException` + `@throws` JavaDoc | Checked exceptions for recoverable errors (use `Optional` or result types) |
| try-with-resources | Manual `close()` in `finally` |

---

## Package / Module Boundaries — Strict

| Module | Visibility |
|---|---|
| `common-api` | `exports com.luatweaker.api.*`. No `requires` beyond `java.base`. |
| `core-engine` | Qualified export to `neoforge-platform` only. |
| `modules/*` | Sealed packages — only entrypoint is `public`. |
| **Cross-module** | **No coupling outside declared API packages.** |

---

## Immutable Data Pattern

```java
// ✅ GOOD — record + factory method
public record RecipeId(String namespace, String path) {
    public RecipeId {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(path);
    }
    public static RecipeId of(String namespace, String path) {
        return new RecipeId(namespace, path);
    }
}

// ❌ BAD — mutable struct with public fields
public class RecipeId {
    public String namespace;  // ❌ public mutable field
    public String path;       // ❌ public mutable field
}
```

---

## Code Style — Automated Enforcement Expected

- **Braces**: K&R (opening brace on same line). **Always use braces**, even for single-line blocks.
- **Indentation**: 4 spaces. **No tabs.**
- **Line length**: 120 chars max. Break **before** operators.
- **Imports**: No wildcard `*`. Order: static → Java → third-party → project (blank line between groups).
- `@Override` **mandatory** on every overriding method.
- `@Deprecated` must include `@deprecated` JavaDoc with migration path and removal plan.

---

# ⚠️ CRITICAL SECTION 3 — Cross-Platform Architecture (PAL)

❌ **Any direct platform dependency outside `neoforge-platform` = instant rejection.**

---

## Dependency Hierarchy (Memorize This)

```
common-api (pure interfaces)
    ↑
core-engine (Cobalt wrapper, logger, stubs — ENGINE AGNOSTIC)
    ↑
module-recipes (recipe binding — ENGINE AGNOSTIC)
    ↑
neoforge-platform (NeoForge launcher, PAL impl — ONLY NEOFORGE MODULE)
```

---

## PAL Rules — Absolute

| # | Rule | Consequence |
|---|---|---|
| 1 | `core-engine` + `modules/*` **NEVER** import `net.minecraft.*`, `net.neoforged.*` | **REJECTED** |
| 2 | All platform calls → `Platform.getInstance().<method>()` | **No direct platform access** |
| 3 | New platform capability → define in `Platform` interface, impl in `NeoForgePlatform` | **Zero changes to consumers** |
| 4 | New loader (Fabric, Forge) = new module implementing PAL | **Zero changes to core/modules** |

---

## Service Registration Pattern (Must Follow)

```java
// ✅ CORRECT — cross-platform
ILuaEngine engine = Platform.getInstance().getEngine();
engine.registerService("Recipes", new RecipeService());

// ❌ WRONG — platform leak
// engine.registerService("Recipes", new NeoForgeRecipeService());
// The line above would be REJECTED
```

---

## Testing Without Minecraft — Mandatory

- All engine tests run against `MockRecipeService`. **No Minecraft on classpath**.
- PAL is mockable via interface. Verify against interface contract, NOT concrete impl.
- `AsyncFileLogger` must be shut down in `@AfterAll` (documented thread-safety requirement).
- Every test must be runnable **without Minecraft installed**.

---

## Other Information (Build, Structure, etc.)

### Project Structure

| Module | Role | Depends On |
|---|---|---|
| `common-api/` | Pure Java 21 interfaces (PAL, VM, LuaDoc annotations). Zero deps. | *(none)* |
| `core-engine/` | Cobalt Lua engine wrapper, async logger, linter, EmmyLua stub gen. Fat-jars Cobalt 0.9.9. | `common-api` |
| `modules/module-recipes/` | Engine-agnostic recipe binding + Lua scripts. | `common-api`, `core-engine` |
| `neoforge-platform/` | **Only runnable module**. NeoForge launcher, concrete PAL impl. Jar-in-Jar embeds all submodules. | all of the above + NeoForge |

Entrypoint: `com.luatweaker.platform.LuaTweakerMod` (`@Mod("luatweaker")`).

---

### Build & Run

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

Java 21 (temurin), Gradle via wrapper. CI runs `./gradlew build` on push/PR (`.github/workflows/build.yml`).

---

### Lua Directory (`lua/`)

- `startup/` — item/block/fluid registration (mod loading phase)
- `server/` — recipes, events, worldgen, commands (server init)
- `client/` — GUI, keybinds, shaders
- `luamods/` — autonomous Lua mods (folders or `.zip`)
- `assets/` + `data/` — virtual resource/datapack roots
- `.luatweaker/stubs/` — auto-generated EmmyLua stubs (regenerated on reload)
- `logs/luatweaker.log` — engine output

Game directory: `run/`. The `syncLua` Gradle task copies `neoforge-platform/lua/` → `run/lua/` before each run.

---

### Lua Script Style — Roblox (Luau) Convention (Maximize Fidelity)

Lua scripts MUST replicate Roblox Studio's official Lua/Luau style as closely as possible. The auto-generated EmmyLua stubs (`luatweaker-api.lua`) mirror a `--!strict` Roblox API, so every script should read like a Roblox ModuleScript. Copy the look and feel of the existing `neoforge-platform/lua/` scripts.

#### Naming

| Element | Roblox Rule | ✅ Correct | ❌ Wrong |
|---|---|---|---|
| Local vars & local functions | `camelCase` | `local maxStack = 64`, `local function registerItems()` | `local MaxStack`, `local max_stack` |
| Global API / module exports | `PascalCase` | `Mod`, `startup`, `Recipes` | `mod`, `recipesService` |
| New script file names | `PascalCase` | `RubyRecipes.lua` | `ruby_recipes.lua` |
| Constants | `UPPER_SNAKE_CASE` | `local MAX_STACK = 64` | `local maxStack = 64` |
| Booleans | `is*` / `has*` / `can*` prefix | `isServer`, `canAttack` | `getEnabled`, `server` |
| Events / callbacks | `on*` prefix | `onRightClick`, `onConsume` | `rightClick` |

#### Formatting

- **Indentation**: 4 spaces. **No tabs** (keep consistent with the Java modules).
- **One statement per line**. Never pack multiple statements into one line.
- Blank line between logical blocks; blank line **before** each `-- ====` section banner.
- **Line length**: 120 chars max. Break **before** binary operators (`..`, `and`, `or`).
- **Strings**: use `[[ ... ]]` long brackets for multi-line data (JSON DataPack patches, long messages). Roblox-style banner:

```lua
-- ===================================================================
-- SECTION TITLE
-- ===================================================================
```

#### Module Structure (Roblox ModuleScript Pattern)

```lua
local recipes = Mod:GetService("Recipes")

-- PRIVATE FUNCTIONS
local function buildRubyBlockPattern()
    return { "RRR", "RRR", "RRR" }
end

-- PUBLIC API
recipes:addShaped("ruby_block_craft", item("luatweaker:ruby_block", 1), buildRubyBlockPattern(), {
    R = ingredient("luatweaker:custom_ruby")
})
```

- All variables declared `local`. **NEVER** create implicit globals.
- Use colon method syntax: `object:method()`, chained as `obj:a():b():c()` (same as `Player:sendMessage(...)`).
- Keep callbacks small — extract helpers into `local function` above the call site.
- Order inside a script: banner header → `Mod:GetService(...)` handles → constants → private functions → public API → final `print()`.

#### EmmyLua Type Annotations (`--!strict` Equivalent)

Annotate every public script function with `---@param` / `---@return`, matching the generated `.luatweaker/stubs/` types:

```lua
---@param player any
---@param itemStack any
local function onRightClick(player, itemStack)
    player:sendMessage("§6Shine!")
end
```

#### Forbidden

- No `goto`.
- No single-letter variable names (except loop counters `i`).
- No string concatenation for large/multiline text — use `[[ ]]` blocks or `table.concat`.
- No nested method chains deeper than ~3 calls — split into named locals.
- No redundant `return nil` / `return true` at the end of void functions.

---

### Build Gotchas

- **`syncLua`** must run before client/server launch (auto-wired in `neoforge-platform/build.gradle`).
- **`copyDevelopmentMods`** runs before `processResources` (copies JEI JAR to `run/mods`).
- **`generateModMetadata`** expands template properties from `gradle.properties` into `build/generated/sources/modMetadata`.
- **IDE sync** (`neoForgeIdeSync`) depends on all subproject `jar` tasks (root `build.gradle` `projectsEvaluated` block). After a clean build, run `./gradlew build` before opening IDE to avoid classpath errors.
- **`core-engine`** fat-jars Cobalt (`cc.tweaked:cobalt:0.9.9`) into its own output via `jar { from { configurations.runtimeClasspath.filter { ... } } }`.
- **`evaluationDependsOn`** in `neoforge-platform/build.gradle` ensures submodules evaluate first. Do not remove.

---

### Test Quirks

- Tests use `MockRecipeService` — no Minecraft runtime required.
- `AsyncFileLogger` must be shut down in `@AfterAll` to avoid dangling threads.
- The `EngineTest.findScriptFile()` helper checks relative paths then falls back to `../../` for IDE vs CLI execution.
- `testSyntaxErrorTraceback` relies on `Thread.sleep(1000)` for async logger flush — inherently timing-sensitive.

---

### Architecture Notes

- **PAL** (`com.luatweaker.api.pal.Platform`) — singleton abstraction over NeoForge. Set once at mod init.
- **Service Registry** — Lua-side `Mod:GetService("Recipes")` maps to `ILuaEngine.registerService()`. Maintained in `core-engine/src/.../LuaServiceRegistry.java`.
- **`InterceptionHelper`** — accumulates pending recipe modifications, flushed on `ServerAboutToStartEvent` / `AddReloadListenerEvent` / `/lt reload`. Cleared each cycle.
- **`@LuaDoc`** annotation on API interfaces drives auto-generated stub files (`luatweaker-api.lua`). Generated whenever stubs are enabled (`AUTO_GENERATE_STUBS` config, default true).
- **Lua execution stages**: `startup` files run during mod construction; `server` files run on server start and `/lt reload`; `client` files run on client init.

---

### In-Game Commands

- `/lt reload` — hot-reloads all Lua scripts, regenerates stubs, re-applies recipes
- `/lt doctor` — health diagnostics on loaded scripts
- `/lt hand` — inspect held item/block
- `/lt dump` — dump registries to logs

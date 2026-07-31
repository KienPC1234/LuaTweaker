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

## 0.2 — NO Sloppy / Careless Code (Code Cẩu Thả) — Absolute Ban

- **No dead code**: unused imports, unused fields, unused methods, unused local variables, unused parameters.
- **No placeholder values**: returning `0`, `null`, `""`, `false`, or hardcoded constants as if they were real results.
- **No copy-paste duplication**: duplicate logic must be extracted or the change is rejected.
- **No leftover markers**: `// TODO`, `// FIXME`, `// HACK`, `// WIP`, `// XXX` left in delivered code. Finish the work or state it is unfinished.
- **No fake signals**: every log line, `print()`, and return value must reflect the real behavior — never fabricate success messages.
- **No lazy shortcuts**: no hardcoded paths, no magic string switching instead of real dispatch, no bypassing the module/builder architecture in Section 1.

## 0.3 — NO Non-Functional / Broken Code (Code Không Dùng Được) — Absolute Ban

- **Every change MUST compile.** Run the build before submitting — never hand over code you did not compile.
- **Every change MUST pass tests.** Add tests for all new behavior; never ship untested logic.
- **Every exposed API must work end-to-end**: Java interface → implementation → Lua binding → runtime effect. Test the full path, not just the signature.
- **No runtime crashes** in default-reachable paths: no NPEs, no `ClassCastException`, no illegal state reached by normal usage.
- **No code that merely "type-checks"** but throws on first invocation.
- **Never merge code you have not actually run and verified** (build + tests + relevant runtime path).

## 0.4 — NO Silent Stubs (Code Stub Giấu Diếm) — Absolute Ban

> **A "stub" is any code that is NOT the real implementation.** Using a stub is **STRICTLY FORBIDDEN** unless ALL of the following hold:

1. **The stub is explicitly disclosed** to the user in the response, with the exact file and line.
2. **The stub is loudly marked in code**, e.g. `throw new UnsupportedOperationException("STUB <reason>")`, `assert false : "STUB ..."`, or an explicit `log.warn("STUB ...")` before returning.
3. **A follow-up task is recorded** to replace the stub, and the user is told it remains unfinished.

| ❌ Forbidden | ✅ Required |
|---|---|
| Returning a stub silently as if the feature were done | Disclose it clearly + mark it + log the follow-up |
| Empty interfaces / empty method bodies handed off as "done" | Real implementations only |
| `Mock*` classes used in production code paths | Mocks exist only in tests — never in runtime |
| `return null` / `return 0` / blank `{}` as a "complete" feature | Complete, working behavior or explicit disclosure |
| Deleting code to make a stub "invisible" | Keep it visible, loud, and reported |

## 0.5 — NO Hallucinations / Fabricated Code — Absolute Ban

> **Never invent APIs, methods, classes, or libraries that do not exist.** Hallucinated code compiles in the author's head, not in the project.

- **Verify every symbol exists**: before calling any internal API or third-party method, confirm it exists in the exact version of the library/project in use. No guessing from memory.
- **No invented signatures**: never write calls to methods you have not seen. Read the real source or docs first.
- **No phantom return types**: never return a made-up type to "make it work".
- **No imagined registry entries**: never claim a recipe/block/item/event exists unless it is actually registered and verifiable in code.
- **State uncertainty, do not guess**: if you are not 100% sure a symbol/behavior exists, SAY SO instead of fabricating code around it. Never code around an assumption without verifying it.

## 0.6 — NO Fake Tests / Fake Coverage — Absolute Ban

| ❌ Forbidden | ✅ Required |
|---|---|
| Tests that assert nothing (`@Test void test() {}`), always pass, or never run | Every new behavior has real, runnable tests that exercise the actual logic |
| Testing only the happy path and ignoring failure modes | Cover success path **and** error/failure paths |
| Mocking everything so nothing real runs | Mocks only for cross-platform seams; the real engine/binding path must be exercised |
| Deleting/renaming tests to make a red suite green | Fix the code; never fix the test report |
| Skipping edge cases: `null`, empty, max/min values, malformed input | Boundary and edge-case tests for every new function |

- Every delivered function MUST have at least one unit test covering the primary success path and its failure modes.
- Run `./gradlew test` — all relevant tests MUST be green. A test suite you cannot run does not count.
- Never claim coverage you cannot prove. Report actual pass/fail output.

## 0.7 — NO Hidden Secrets / Credentials — Absolute Ban

- **Never commit or embed** API keys, tokens, passwords, database URLs, or any credential — even "placeholder" ones like `sk-test-...`.
- **Never hardcode credentials** in code, config, or default files. Use environment variables or a properly git-ignored local config.
- Scan every delivered diff for secrets before submission.

## 0.8 — NO Inaccurate Comments / Documentation — Absolute Ban

- **No stale or invented comments**: comments must describe what the code ACTUALLY does. Delete outdated comments; never keep them to "look documented".
- **No docstring lies**: `@LuaDoc`, `@param`, `@return`, JavaDoc must match the real signature and behavior.
- **No fake success narratives**: comments/log messages must never claim behavior the code does not have.

## 0.9 — Complete Codebase Awareness (Mandatory Before Any Change)

- **Analyze before modifying**: read the surrounding code, dependencies, imports, and existing implementations. Never edit in a vacuum.
- **Respect existing patterns**: new code must follow the module/builder architecture and Roblox-style Lua conventions already in the project.
- **Check environment parity**: confirm runtime versions (Java 21, NeoForge, Cobalt, Luau) before writing code that depends on them.

## 0.10 — NO Silent Errors — Loud, Honest Failure Reporting (Absolute Requirement)

> **If anything fails — a compile error, a failing test, a runtime error, an unverifiable step, an incomplete feature — you MUST say so loudly and immediately. Silence about a failure is treated as a lie.**

- **Report every error, warning, and limitation at the exact moment it happens** — never hide it, never save it for later, never mention it only after claiming success.
- **Every error report MUST include**: WHAT failed, WHERE (file:line), WHY it failed, and WHAT was done about it.
- **No "everything works" summary when anything failed.** One failing test or one unverified path invalidates a "done" claim.
- **Unverified ≠ verified.** If a step could not be compiled, run, or tested, label it **UNVERIFIED** — never quietly upgrade it to "done".
- **When uncertain, say "I don't know"** — guessing or staying silent is forbidden.

## 0.11 — NO Rule Evasion / Loopholing (Anti-Loophole Clause) — Absolute Ban

> **Rules cannot be technically or literally bypassed. The SPIRIT of each rule is as binding as its LETTER. Any attempt to exploit wording, skip a step, or disguise non-compliance is itself a violation.**

| ❌ Forbidden evasion | ✅ Required honest compliance |
|---|---|
| Following a rule "technically" while ignoring its intent | Comply with both the letter **and** the spirit |
| Telling only part of the truth to avoid admitting failure | Full disclosure of everything, including failures |
| Saying "it might work" / "should be fine" without verifying | Verify, or explicitly label the step UNVERIFIED |
| Doing the minimum literal step and stopping | Complete the task to its full, real requirement |
| Reinterpreting rules to justify skipping work | Ask for clarification if a rule seems ambiguous |
| Blaming tools/environment to excuse missing verification | Report the blocker, then re-verify once resolved |
| Reusing old tests/output as "current results" | Fresh run output only, labeled with the actual command |
| Claiming a build/test "passed" without running it | Only claim what you actually ran and saw pass |
| Quietly changing scope to avoid hard parts | Keep the original scope; say what you could not do |
| Reporting success on a partial implementation | Report the exact percentage done and what remains |
| Generic claims ("the fix is applied") with no specifics | Name the file, the change, and the proof (build/test output) |

## 0.12 — Error Handling in Delivered Code — Loud, Never Silent

- **No empty `catch` blocks. Ever.** Every `catch` MUST log the error with context (what failed, where) and take a visible corrective action or rethrow.
- **No swallowed exceptions**: no `catch (Exception e) {}`, no `catch` + `// ignored` comment, no silently returning a default on failure.
- **Errors must surface**: log with message/stack trace, and fail loudly when the operation cannot continue.
- **Failed Lua/Java calls must be reported** through the engine logger — never silently dropped.
- **The error path must be tested**: write at least one test asserting the failure path throws/logs correctly rather than silently returning.

## 0.13 — Self-Review Checklist — Catch Your Own Violations

Before declaring anything done, run ALL of the following and report each result:

1. Did I compile? What was the exact output?
2. Did I run the tests? How many passed / failed / skipped?
3. Did I loudly report every error, warning, and UNVERIFIED step?
4. Did I follow the letter **and** the spirit of every rule — no loopholes?
5. Did I name every file changed and every behavior added?
6. Am I claiming anything I cannot prove? If yes → label it UNVERIFIED instead.

## 0.14 — NO Hardcoding — Absolute Ban

> **Hardcoded values are STRICTLY forbidden. Every value that can be configured, derived, resolved, or passed as a parameter MUST be — never baked into code as a literal.**

| ❌ Forbidden | ✅ Required |
|---|---|
| Absolute file/dir paths (`C:\...`, `/home/...`) | Project-relative paths, `Path` resolution, or config entries |
| Item/recipe/block IDs and namespaces as scattered literals | Centralized constants or the registry |
| Magic numbers (`delay(5000)`, `limit * 3.1415`) | Named `static final` constants (`TICK_INTERVAL`, `MAX_STACK`) |
| Tunable values baked in (durations, counts, thresholds, multipliers) | Read from `default_config.json` / config files |
| Version/platform literals (`1.21.1`, mod versions) | `gradle.properties` / build config expansion |
| Credentials, tokens, database URLs (see 0.7) | Environment variables or git-ignored config |
| Hardcoded coordinates/offsets/spawn positions in Lua scripts | Config-driven values |
| OS-specific literals (`\` path separators, line endings, encodings) | Portable APIs (`Path`, `System.lineSeparator()`) |

- **Every hardcoded value is a defect.** If a literal is truly unavoidable, it MUST be a named constant with a comment explaining why it is not configurable — and the exception MUST be reported to the user.
- **Lua scripts too**: coordinates, spawn positions, item counts, and event timings must come from config, never hardcoded.
- **Tests MAY hardcode expected values** in assertions — production code never does.

## 0.15 — Verification Gate (Mandatory Before "Done")

1. **Plan → Code → Test → Verify → Report.** Do not skip steps.
2. **Compile**: `./gradlew build` (or at minimum the affected modules) — MUST pass.
3. **Test**: `./gradlew test` — all relevant tests MUST be green.
4. **Self-review your diff**: no hidden files, no leftover stubs, no commented-out code, no dead code, no fabricated symbols, no secrets.
5. **Report honestly**: state exactly what works, what is untested, and any known limitations. Never claim success without proof. If unsure, reply "I don't know" rather than guessing.

---

# ⚠️ CRITICAL SECTION 1 — LuaTweaker Style & Quy Trình Tạo Module Mới

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

# ⚠️ CRITICAL SECTION 2 — SOLID Principles (Absolute Compliance Required)

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

# ⚠️ CRITICAL SECTION 3 — Java Coding Conventions (Every Line Matters)

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

# ⚠️ CRITICAL SECTION 4 — Cross-Platform Architecture (PAL)

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

### Autonomous LuaMod Directory (`luamods/`)

- `luamods/<mod_id>/manifest.json` — Mod identity & metadata declaration
- `luamods/<mod_id>/default_config.json` — Default configuration copied to `luaconfig/<mod_id>.json`
- `luamods/<mod_id>/main.lua` — Single autonomous entrypoint
- `luamods/<mod_id>/src/` — Modular business logic (server, client, startup) loaded strictly via `require()`
- `luamods/<mod_id>/assets/` + `data/` — Auto-mounted virtual resourcepack & datapack roots
- `.luatweaker/stubs/` — Auto-generated EmmyLua stubs
- `logs/luatweaker/mods/<mod_id>.log` — Dedicated per-mod engine output

Game directory: `run/`. The `syncLuaMods` Gradle task copies `neoforge-platform/luamods/` → `run/luamods/` before each run.

---

### Lua Script Style — Roblox (Luau) Convention (Maximize Fidelity)

Lua scripts MUST replicate Roblox Studio's official Lua/Luau style as closely as possible. The auto-generated EmmyLua stubs (`luatweaker-api.lua`) mirror a `--!strict` Roblox API, so every script should read like a Roblox ModuleScript. Copy the look and feel of `neoforge-platform/luamods/` scripts.

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

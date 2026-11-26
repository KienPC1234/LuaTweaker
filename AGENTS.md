# LuaTweaker READ THIS FIRST (SUPREME DIRECTIVE - DO NOT DO THE WORK FOR THE USER)

Minecraft 1.21.1 Multi-Loader (Architectury: NeoForge & Fabric) mod — Lua scripting engine with runtime recipe/content patching.

> **LEVEL 0 RED ALERT: LOW-LEVEL API PHILOSOPHY (ANTI-LOOPHOLING)**
> **Your mission is to PROVIDE TOOLS, NOT TO DO THE WORK FOR THEM.**
> - **STRICTLY FORBIDDEN:** Building high-level wrapper classes in Java encapsulating all logic or creating massive DSLs hiding complexity (KubeJS mindset).
> - **MANDATORY STRUCTURE:** Must export **NUMEROUS LOW-LEVEL, PRIMITIVE FUNCTIONS** (e.g. `Raycast()`, `ApplyDamage()`, `SpawnParticle()`, `GetLookVec()`, etc.) to the Lua environment.
> - **PURPOSE:** To empower users (Lua modders) to freely write scripts, assemble, and customize their own gameplay logic (GMod / Roblox / WoW style).
> - **LOOPHOLING VIOLATION:** Grouping logic into a monolithic high-level Java method (e.g. creating a Java `castMagicSpell()` method instead of separate low-level primitives like `getCustomData()` / `setCustomData()` + `fireProjectile()` for Lua) is classified as **ARCHITECTURAL SABOTAGE**.

> **CRITICAL: Every AI agent MUST strictly follow ALL rules below. Violations cause bugs, broken builds, and SOLID violations.**

---

# CRITICAL SECTION 0 — Code Honesty & Delivery Integrity (Strictest — Zero Tolerance)

No hidden code. No sloppy code. No broken code. No silent stubs. No hallucinations. No fake tests. No leaked secrets. No inaccurate docs. No hardcoded values. Every delivered change MUST be complete, correct, and verified before submission. ANY violation below = instant rejection.

---

## 0.1 — NO Hidden Code — Absolute Ban

| Forbidden | Required |
|---|---|
| Silently omitting logic while pretending the task is finished | Deliver the full, real implementation |
| Writing empty or no-op method bodies presented as "working" | Every method contains its actual business logic |
| Empty `catch` blocks that swallow exceptions silently | Catch → log + handle the error; NEVER hide failures |
| Hiding broken/failing code behind `@Deprecated`, `// TODO`, or "unused" flags | Fully implement it, or explicitly report it as incomplete |
| Commenting out logic so the code "compiles" or "passes" | Real logic that compiles and actually works |
| Reusing old results/cache as a fake success while new code is missing | Fresh, real execution path that produces real output |
| Registering a feature that only logs and discards its data (e.g. a config table that is never read) | Wire the registered data to real behavior, or do not register it |

> **Lesson from the codebase**: `registerProjectile` was once a log-only stub — the `damage`/`explosionPower` config was thrown away. It is now a real registry (`ProjectileRegistry`) consumed by the firing layer. Any "registration" API must have a consumer.

## 0.2 — NO Sloppy / Careless Code — Absolute Ban

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

| Forbidden | Required |
|---|---|
| Returning a stub silently as if the feature were done | Disclose it clearly + mark it + log the follow-up |
| `Mock*` classes used in production code paths | Mocks exist only in tests — never in runtime |
| `return null` / `return 0` / blank `{}` as a "complete" feature | Complete, working behavior or explicit disclosure |
| A binding method that only writes a log line instead of doing its job | Real behavior, or remove the method |

## 0.5 — NO Hallucinations / Fabricated Code — Absolute Ban

- **Verify every symbol exists** in the exact library/project version. MANDATORY to use repo inspection tool `tools/mc_inspect.py` (or wrapper `tools/mc` / `tools/mc.bat`) to directly inspect Mojang-mapped sources from `sources.jar` or run `javap` on `merged.jar` before invoking any Minecraft/NeoForge API (details in Section 0.17).
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
- Check environment parity: Java 21, Architectury 13.0.8 (NeoForge 21.1.242 & Fabric 0.16.10), luava 0.2.7 (Lua 5.4), Luau-style Lua.

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
3. Did PMD report ZERO violations (`pmdMain` + `pmdTest`)? What were the exact counts?
4. Did I loudly report every error, warning, and UNVERIFIED step?
5. Did I follow the letter **and** the spirit of every rule?
6. Did I name every file changed and every behavior added?
7. Am I claiming anything I cannot prove? If yes → label it UNVERIFIED.

## 0.14 — NO Hardcoding — Absolute Ban

- Absolute paths, magic numbers, tunable values (durations, costs, cooldowns, damage, thresholds) → config (`default_config.json` → `luaconfig/<mod_id>.json`) or named `static final` constants.
- Version/platform literals → `gradle.properties` expansion.
- Lua scripts: skill costs, cooldowns, spawn positions, regen rates MUST come from `mod:GetConfig()` with a sane fallback.
- Tests MAY hardcode expected values in assertions — production code never does.

## 0.15 — Verification Gate (Mandatory Before "Done")

1. **Plan → Code → Test → Verify → Report.**
2. **Compile**: `./gradlew build` — MUST pass.
3. **Test**: `./gradlew test` — all relevant tests MUST be green.
4. **PMD**: `./gradlew pmdMain pmdTest` — MUST report ZERO violations (see 0.16).
5. Self-review your diff: no hidden files, no stubs, no dead code, no fabricated symbols, no secrets.
6. Report honestly: what works, what is untested, known limitations.

## 0.16 — PMD Quality Gate — NO Hiding Violations (Strictest — Zero Tolerance)

PMD is the machine enforcer of Sections 0.1–0.3. It is wired into the build, not optional advice.

- **Integration (single source of truth):** root `build.gradle` applies the `pmd` plugin to every subproject; ruleset = `config/pmd/ruleset.xml`; tool version = `pmd_version` in `gradle.properties`. `pmdMain`/`pmdTest` run as part of `check` with `ignoreFailures = false`, so any finding fails the build. Reports: `<module>/build/reports/pmd/`.
- **Analysis self-test (`:pmdCanary` + `:pmdCanaryVerify`):** a broken ruleset makes PMD report ZERO violations and exit successfully (verified). `pmdCanary` therefore runs the real ruleset over `config/pmd/probe/Probe.java`, which intentionally trips exactly one `SystemPrintln`; `pmdCanaryVerify` always runs and asserts that the report exists and contains exactly that finding. It also fails when the probe file is missing, because Gradle would then skip the Pmd task as NO-SOURCE and the canary would prove nothing. Every `pmdMain`/`pmdTest` depends on the verifier, so a silent gate is impossible.
- **Commands:** `./gradlew pmdMain pmdTest` (full scan) or `./gradlew :<module>:pmdMain` (touched module only — MANDATORY after every Java change).
- **Zero-tolerance:** no change is "done" while PMD reports violations in touched code. New code MUST NOT introduce new violations.
- **Hiding a violation = instant rejection** (same severity as a silent stub, Section 0.4). ABSOLUTELY BANNED:
  - `@SuppressWarnings("PMD...")`, `// NOPMD`, or any suppression comment;
  - adding `<exclude>` to the ruleset to dodge a finding;
  - adding a type name to the audited carve-out list in `config/pmd/ruleset.xml` to silence a finding that is not an interface-shape/API-contract issue;
  - deleting, neutering, or splitting code/tests just to silence a rule;
  - fake assertion messages in tests (`"check"`, `"ok"`, `"test"` - messages must describe WHAT is verified);
  - shrinking rule scope (e.g. excluding a file) without explicit user approval;
  - **Telescoping Inheritance to cheat PMD metrics** (e.g. `Base1 -> Base2 -> ... -> BaseN` chains for `TooManyMethods`, `ExcessivePublicCount`, `GodClass`, `NcssCount`): slicing a large class into a ladder of artificial base classes purely to stay under per-file thresholds is strictly forbidden. If a wide class is an immutable Lua facade or SPI implementation (e.g. `ImGuiServiceImpl`), keep it in ONE cohesive class and request an audited carve-out in `config/pmd/ruleset.xml`. If it has multiple domain responsibilities, refactor via **Composition** (independent collaborators/handlers), never inheritance chaining.
- **Audited architectural carve-outs:** the only carve-outs are the `AUDITED ARCHITECTURAL CARVE-OUTS` block in `config/pmd/ruleset.xml`. Each entry suppresses ONE rule on a named type whose method names/arities/member count are the Lua-facing contract (LuaBinder, stub generator, platform registration) or a 25-100 method PAL facade it implements. The same rule stays active for every other type; a new 12-method class still fails, verified. Remove a name when the type is split; adding one is an architecture change that needs user approval, exactly like a public signature change.
- **Fix ladder (in order, stop at the first that applies):**
  1. Fix the code properly (braces, constants, specific catches, try-with-resources, real asserts).
  2. If the finding reflects INTENTIONAL design (e.g. a broad `catch` as a Lua-callback firewall), centralize it into ONE documented helper with a comment stating WHY broad — do not scatter.
  3. If genuinely unfixable without behavior damage, report exact `file:line` + reason to the user and WAIT. Never hide.
- **Ruleset changes** require explicit user approval AND an inline XML comment explaining why the rule is a false positive on this codebase. The bar is "rule is wrong for us", never "code is too dirty to fix".

## 0.17 — Mandatory Tool: `tools/mc_inspect.py` (Multi-Platform Mojmap & Bytecode Inspector)

To strictly adhere to Section 0.5 (zero hallucinated symbols or APIs), the repository provides the `tools/mc_inspect.py` CLI toolkit (with wrappers `tools/mc` and `tools/mc.bat`). The tool automatically discovers classpaths across platforms (NeoForge, Fabric, Forge, Vanilla), dynamically adapts across Minecraft upgrades (1.21.1, 1.21.2+, 1.22+), and discovers libraries in Gradle caches (GeckoLib, JEI, etc.) without manual configuration. Supports `--platform <name>`, `--sources <path>`, `--jar <path>` flags and fuzzy command suggestions.

**All AI agents interacting with Minecraft/NeoForge APIs are MANDATED to use this tool:**

| Command | Description | Practical Example |
|---|---|---|
| `status` / `platform` | View active platform, Minecraft version, sources JAR, and merged JAR | `tools\mc status` |
| `search <query>` | Search classes by name or regex | `python tools/mc_inspect.py search MobEffect` |
| `source <class> [-m fn]` | View Mojmap Java source from `sources.jar` (supports short class names) | `python tools/mc_inspect.py source MobEffect -m applyEffectTick` |
| `disasm <class> [-c] [-m]` | Disassemble bytecode via javap with auto-resolved classpath | `python tools/mc_inspect.py disasm 'Util$OS' -m openFile -c` |
| `effects [query]` | Inspect MobEffects (speed, slowness, category, color) | `python tools/mc_inspect.py effects speed` |
| `potions [query]` | Inspect vanilla Potions (healing, swiftness, invisibility...) | `python tools/mc_inspect.py potions healing` |
| `sounds [query]` | Inspect 1,580+ vanilla SoundEvents | `python tools/mc_inspect.py sounds anvil` |
| `particles [query]` | Inspect 100+ vanilla ParticleTypes (flame, smoke, crit...) | `python tools/mc_inspect.py particles flame` |
| `entities [query]` | Inspect 130+ vanilla EntityTypes with Java class and category | `python tools/mc_inspect.py entities zombie` |
| `capabilities [query]` | Inspect multi-platform Capabilities (NeoForge/Fabric Transfer API) | `python tools/mc_inspect.py capabilities item` |
| `components [query]` | Inspect 54+ DataComponents 1.21.1+ (NBT replacement) | `python tools/mc_inspect.py components food` |
| `attributes [query]` | Inspect 29+ Attributes 1.21.1+ (default, min, max) | `python tools/mc_inspect.py attributes health` |
| `properties [query]` | Inspect 112+ BlockStateProperties | `python tools/mc_inspect.py properties facing` |
| `packets [query]` | Inspect 158+ network packets (Clientbound/Serverbound) | `python tools/mc_inspect.py packets block` |
| `events [query]` | Inspect multi-platform Events (with @Cancelable flags, getters) | `python tools/mc_inspect.py events damage` |
| `extensions [query]` | Inspect NeoForge Extension Interfaces (IItemExtension...) | `python tools/mc_inspect.py extensions item` |
| `registries [query]` | Inspect Minecraft BuiltInRegistries | `python tools/mc_inspect.py registries` |
| `tags [category]` | Inspect built-in TagKeys (item, block, enchantment, entity) | `python tools/mc_inspect.py tags enchantment` |
| `usages <symbol>` | Search symbol references across all Minecraft sources | `python tools/mc_inspect.py usages IClientMobEffectExtensions --limit 5` |
| `tree <class>` | Display inheritance tree (superclasses and interfaces) | `python tools/mc_inspect.py tree Zombie` |
| `fields <class>` | List fields and enum constants | `python tools/mc_inspect.py fields InteractionResult` |
| `methods <class> [-q]` | List declared methods and signatures | `python tools/mc_inspect.py methods ItemStack -q get` |

---

# CRITICAL SECTION 1 — Core Design Philosophy & LuaTweaker Identity

> **CORE PHILOSOPHY: PROVIDE TOOLS (GMOD / ROBLOX / WOW STYLE) — DO NOT DO THE WORK FOR THEM (ANTI-KUBEJS)**
> **Long-Term Vision:** Never write low-quality/hacky workarounds just to pass tests or complete tasks quickly. Build solid, extensible foundational APIs so users have maximum creative freedom.

## 1.1 API Philosophy (MANDATORY READ)

1. **Provide "Raw Ingredients" (Low-Level), DO NOT Provide "Finished Dishes" (High-Level):**
   - **GMod / Roblox / WoW Standard:** Provide raw, primitive, versatile APIs (e.g. `Raycast`, `ApplyImpulse`, `PlaySound`, `Timer`, `Events`). Empower users to script and assemble their own complex mechanics (skills, guns, magic, minigames).
   - **Anti-KubeJS Pattern (BANNED):** DO NOT build massive abstractions or wrappers doing everything for the user (e.g. strictly forbidden to build a `MagicSpellBuilder` that auto-deducts mana, calculates cooldowns, and spawns particles). Doing too much makes the system rigid, hard to maintain, stifles creativity, and breaks easily at scale.

2. **Code Quality & Long-Term Vision First:**
   - Zero low-quality code or hacky workarounds. Always think long-term.
   - The Java layer serves ONLY as a fast, safe, and optimized BRIDGE down to NeoForge/Minecraft. The game logic brain must remain entirely in Lua.

## 1.2 LuaTweaker Design Identity (Patterns & Conventions)

1. **Content & Recipe Definitions (Static Registration):**
   - Chainable Builder Pattern by Namespace: `Content.NewItem("id")`, `Content.NewBlock("id")`, `Content.createEntity("id", fn)`, `Recipe.Shaped("id"):Pattern(...):Register()`.
   - Builders serve ONLY to declare basic data. Floating global functions `item()`, `ingredient()` are BANNED from being primary APIs (they are only helpers in recipes).

2. **Runtime Services & AI Logic:**
   - Service/Signal PascalCase: `Events:Listen(...)`, `Task:delay(...)`, `World:StrikeLightning(...)`, `Vector3.new(...)`.
   - Unified Entity API: A single entity table supporting BOTH method style (`entity:setHealth(50)`) and property style (`entity.Health = 50`) — see Section 5.

3. **Explicit Library Loading:**
   - All scripts load libraries via `require("LuaTweaker.ModuleName")`.
   - Floating magic globals are BANNED (except `mod`, `Mod`, `Events`/`Content`/... injected by the engine).

4. **LuaMod Package Structure:**
   - `luamods/<mod_id>/manifest.json` + `main.lua` (single entrypoint) + `src/startup`, `src/server`, `src/client` + `default_config.json` + `assets/`, `data/`.

---

## 1.3 5-Step OOP Procedure to Create a New Module

### Step 1: Declare Java Interface in `common-api` with `@LuaDoc` (+ `@LuaDefault` for optional parameters)
```java
package com.luatweaker.api.example;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.annotation.LuaDefault;

@LuaDoc(description = "Example module management service.")
public interface IExampleModuleService {
    @LuaDoc(
        description = "Executes an example feature.",
        params = {"id: string", "power: number"},
        returnType = "void"
    )
    void executeFeature(String id, @LuaDefault("1.0") double power);
}
```
> The interface is the **single source of truth**: bindings, EmmyLua stubs, and documentation are all generated from it. DO NOT write manual bindings.

### Step 2: Implement Independently in `modules/module-<feature>`
```java
public class ExampleModuleServiceImpl implements IExampleModuleService {
    @Override
    public void executeFeature(String id, double power) {
        // Business logic
    }
}
```

### Step 3: Register Binding via **LuaBinder** — DO NOT Write Manual Glue
```java
public class ExampleModuleLuaBinding {
    public static void registerBindings(ILuaEngine engine, IExampleModuleService service) {
        LuaBinder.bind(engine, "ExampleModule", service, IExampleModuleService.class);
    }
}
```
`LuaBinder` (core-engine) automatically: converts parameters (String/int/double/boolean/table/IEntity), strips `self` from colon calls, generates PascalCase aliases, applies `@LuaDefault`, throws clear errors when required arguments are missing, and converts return values. Writing manual glue (`getOffset` + `args[i].asInt()` + delegate) in new bindings is STRICTLY BANNED.

### Step 4: Register in `LuaServiceBootstrap` (+ stub in `LuaStubSynchronizer`)
```java
ExampleModuleLuaBinding.registerBindings(engine, exampleService);
stubGen.registerService("ExampleModule", IExampleModuleService.class);
```
> Entity/player wrapper classes: `stubGen.registerClassStub(IEntity.class, "Entity")` — generates class stubs with inheritance (`---@class Player: Entity`).

### Step 5: Use in Lua Scripts with Standard LuaTweaker Style
```lua
local ExampleModule = require("LuaTweaker.ExampleModule")
ExampleModule:ExecuteFeature("custom_id", 100)
```

---

# CRITICAL SECTION 2 — SOLID Principles (Absolute Compliance Required)

| Principle | Enforcement |
|---|---|
| **S**ingle Responsibility | Every class/module/method has EXACTLY ONE reason to change. `common-api` = pure abstraction; `core-engine` = VM wrapper + LuaBinder + logger + stubs; `modules/*` = exactly one domain; `common` = shared Architectury Minecraft PAL + registrars; `neoforge` / `fabric` = loader entrypoints + native capabilities. |
| **O**pen/Closed | Adding features = adding code (new bindings, new services), NOT modifying running code. `InterceptionHelper` is closed; `LuaServiceRegistry` is extended via `register()`. |
| **L**iskov Substitution | Every PAL implementation is substitutable for its base type. Tests with `Mock*` MUST pass alongside the real implementation. |
| **I**nterface Segregation | Small, focused interfaces tailored to client needs. If an implementation has to stub a method → SPLIT the interface immediately. |
| **D**ependency Inversion | Only `common`, `neoforge`, and `fabric` import Minecraft/loader APIs. `common-api` has zero dependencies. The module graph is a strict DAG. |

---

# CRITICAL SECTION 3 — Java Coding Conventions

- **Java 21**: `record` for immutable DTOs, sealed hierarchies, pattern matching switch, TextBlocks, `Optional` ONLY for nullable returns.
- **@NotNull/@Nullable on EVERY parameter and return value.**
- **Naming**: packages `com.luatweaker.<module>.<feature>` (singular), class PascalCase, method camelCase (one verb), constants `UPPER_SNAKE_CASE`, boolean `is*/has*/can*`.
- **Encapsulation**: fields `private final`, collection getters return `List.copyOf()` or unmodifiable views, builder pattern for 3+ parameters, **NO mutable statics** (except intentional registries like `LuaServiceRegistry`, `EventServiceImpl`, `ProjectileRegistry` — must have explicit documentation and comment).
- **Exceptions**: catch specific exceptions, NEVER blanket `catch (Exception e)`; use try-with-resources; all error paths must have unit tests.
- **Formatting**: K&R braces, 4 spaces, no tabs, max 120 chars, no wildcard imports, `@Override` mandatory.

---

# CRITICAL SECTION 4 — Cross-Platform Architecture (PAL)

- `core-engine` + `modules/*` **MUST NOT** import `net.minecraft.*`, `net.neoforged.*`, or `net.fabricmc.*` — violations are REJECTED.
- All platform calls route through `Platform.getInstance().<method>()` or `Platform.getX()`.
- Adding a capability → add to `Platform` interface + implement shared logic in `common` (`Minecraft*Platform`) and loader-specific hooks in `neoforge` / `fabric` — zero changes for consumers.
- Tests do not require a Minecraft runtime (mock through interfaces).

---

# CRITICAL SECTION 5 — Anti-Boilerplate & Runtime Architecture Rules (Mandatory)

> These rules are derived from real fixed bugs. Violations resurrect old defects.

## 5.1 — Bindings Mandatory via LuaBinder (Manual Glue BANNED)

- Every NEW service binding: `LuaBinder.bind(engine, name, impl, apiClass, aliases...)` or `LuaBinder.bindTable(...)` for wrapper tables.
- Optional parameters → `@LuaDefault` on interface — DO NOT write manual fallback logic in bindings.
- Copying old patterns (`int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;` + `args[off].asInt()` + delegate) is STRICTLY BANNED.
- Exceptions (with valid rationale): builder DSL (`ContentLuaBinding`), complex parsing (`RecipesLuaBinding`), wrappers with custom lifecycle (`StorageLuaBinding`, `NetworkLuaBinding`).

## 5.2 — ONE Unified Entity API (Dual Parallel Wrappers BANNED)

- `IEntity` is the sole wrapper. `MinecraftEntityWrapper` + `MinecraftPlayerWrapper` are implementations; `MinecraftInteractableEntity implements IEntity` (no secondary view).
- Lua entity/player tables: `EntitiesLuaBinding.createEntityLuaTable` / `createPlayerLuaTable` — method style + property aliases (Health, MaxHealth, Type, CustomName, Velocity, Position) via metatable.
- Creating a second entity API (like the old `wrapEntity` with mismatched method names: `ShootProjectile` vs `shootProjectile`) is STRICTLY BANNED.
- Projectiles: **EXACTLY ONE place** to fire — `EntityInteractionHelper.shootProjectile/shootProjectileAt` + `MinecraftInteractableEntity` overrides delegating to the helper. Dual independent switch tables are BANNED.

## 5.3 — Event Bus: Shared + Replace Semantics

- `EventServiceImpl` uses **a single map shared across all engines**; `Listen` **REPLACES** old listeners (newest engine wins) — no listener accumulation.
- Rationale: item handler closures are pinned to the startup engine — dispatching through the shared bus forwards to the active runtime engine.
- Making listener maps per-instance (causing double-firing) or clearing listeners at the wrong lifecycle stage is BANNED.

## 5.4 — Item/Block Handlers Dispatch ONLY Server-Side

- `ItemRegistrar` / `BlockRegistrar`: Lua handlers execute ONLY when `!level.isClientSide()` — the client only consumes clicks (`sidedSuccess`).
- Dispatching Lua events on both sides is BANNED — client-side casting erroneously deducts mana, sets invalid cooldowns, and fails to fire projectiles (`ClientLevel`).

## 5.5 — Thread Safety: Synchronized VM Entry Points

- `LuavaEngine` uses an **execution gate** (`Semaphore`) instead of `synchronized`: `executeString/executeScript/callFunctionMulti/registerGlobal` acquire the gate when called from external threads (server/render/reload).
- Coroutine threads (created by `task._run_deferred`) **do not acquire the gate** — they belong to the operation that resumed them, and the resuming thread is parked waiting for them. This prevents deadlocks when Java callbacks call back into the engine (e.g. `task.getTimeClock` → `engine.wrapNumber`).
- Adding gate/synchronization to `wrapString/wrapNumber/wrapBoolean/nilValue/createTable` is BANNED: these are pure factories called from coroutine threads; adding locks will cause deadlocks.
- Render callbacks (`OnRenderHUD`) MUST use `Signal:FireSync` (synchronous, on the render thread with GuiGraphics context) — async Fire for render is BANNED.
- Coroutines/deferred tasks: execute via `task._run_deferred` (Java virtual thread, resumes synchronously until yield/termination) — calling `coroutine.resume` directly inside `task._tick` is BANNED.
- `ILuaEngine.shutdown()` cancels pending coroutines before replacing the engine during reloads.

## 5.6 — Network: Clientbound → Network Service of ACTIVE Engine

- Clientbound packets route to `LuaServiceRegistry.get("NetworkServiceImpl")` (the active engine where mods are loaded and listeners connected) — NEVER use the client engine service (which never loads mods, causing empty remoteEvents and broken HUD/effects).

## 5.7 — Stub Generator: Inheritance + Class Stubs for Wrappers

- `generateClassStub` MUST emit `---@class X: Parent` for inherited interfaces; transitive scans use `classNameByType`.
- Entity/player wrappers: `registerClassStub(IEntity.class, "Entity")`, `registerClassStub(IPlayer.class, "Player")`.

## 5.8 — Config-Driven Tunables

- Skill costs, cooldowns, regen rates, boss health, spawn offsets, GUI geometries → `default_config.json` → `mod:GetConfig()` with sensible fallbacks.
- Hardcoding values in Lua scripts is BANNED (Section 0.14).

## 5.9 — Pure ASCII Logs

- Log output (log files and console) MUST use ASCII: `=== BEGIN RELOAD ===`, `->`, `-`. Box-drawing, em-dashes, and unicode arrow characters in log strings are BANNED.

## 5.10 — Dynamic Bridge Architecture (Hybrid Proxy Pattern)

- **DynamicJavaProxy** (`core-engine/src/main/java/com/luatweaker/core/bind/DynamicJavaProxy.java`) provides automatic property and method access for Java objects in Lua.
- **Mechanism:**
  - `__index`: `obj.Health` → automatically calls `getHealth()` or `isHealth()`
  - `__newindex`: `obj.Health = 20` → automatically calls `setHealth(20)`
  - Method call: `obj:teleport(0, 100, 0)` → exact method name match
- **Security Model - Smart Package Blacklist (CRITICAL):**
  - **3 Defense Walls:**
    1. **Luava VM Isolation:** Strip `os.*`, `io.*`, `package.loadlib` from the Lua VM.
    2. **Method Blacklist:** Block `getClass`, `wait`, `notify`, `notifyAll`, `clone`, `finalize`, `hashCode`.
    3. **Smart Package Blacklist:** Block `java.lang.Runtime`, `java.lang.Process`, `java.lang.ProcessBuilder`, `java.lang.System`, `java.lang.Thread`, `java.lang.Class`, `java.lang.ClassLoader`, `java.lang.reflect.*`, `java.lang.invoke.*`, `java.io.*`, `java.nio.*`, `java.net.*`, `javax.net.*`, `sun.*`, `com.sun.*`, `jdk.*`.
  - **Cross-Mod Interop:** All packages NOT in the blacklist are permitted (GregTech, Create, Mekanism, etc.).
  - Check order: package blacklist → method blacklist → cache → reflection → remapper.
- **Runtime Remapper (`core-engine/src/main/java/com/luatweaker/core/remap/RuntimeRemapper.java`):**
  - Solves production obfuscation (Mojmap → SRG: `getHealth` → `m_21223_`).
  - **3 Fallback Tiers:**
    1. **Exact match:** Find method by exact name (development environment).
    2. **Heuristic match:** Case-insensitive + substring search (similar methods).
    3. **Signature match:** Match by parameter types + return type (last resort).
  - **Cache:** `ConcurrentHashMap` for both exact and signature results.
  - **Obfuscation Detection:** Automatically detects dev vs production environment.
  - Integrated into `DynamicJavaProxy.findMethodCached()` called when exact match fails.
- **Integration Points:**
  - `UniversalEventForwarder`: wrap NeoForge events → Lua (auto proxy)
  - `EntitiesLuaBinding`: fallback proxy for unknown entity properties
  - `InteractionLuaBinding`: fallback proxy for unknown block/item properties
- **When to Use:**
  - Raw NeoForge event objects
  - Modded properties not covered by manual wrappers
  - Arbitrary Java objects from cross-mod interop (GregTech, Create, Mekanism)
  - NOT for core APIs with manual wrappers (Entity/Player/Block/Item hot paths)
- **Performance:** Cached via `ConcurrentHashMap<Class<?>, Map<String, Method>>`. Target benchmark: < 2x manual wrapper overhead.

---

# Project Structure (Current)

| Module | Role | Depends On |
|---|---|---|
| `common-api/` | Pure Java 21: PAL, VM interfaces, `@LuaDoc`/`@LuaDefault`, object wrappers | `modules/module-integration-api` (pure types only) |
| `modules/module-integration-api/` | Integration contracts (Geo/JEI specs + service interfaces). Zero deps; every layer may depend on it | *(none)* |
| `core-engine/` | Luava VM wrapper, **LuaBinder**, **DynamicJavaProxy**, async logger (ASCII), linter, stub generator, LuaModManager | `common-api` |
| `modules/module-content/` | Content builders (items/blocks/fluids/entities/tabs/keybinds), ProjectileRegistry | `common-api`, `core-engine` |
| `modules/module-recipes/` | Recipe manipulation + builder DSL | `common-api`, `core-engine` |
| `modules/module-events/` | Shared event bus (replace semantics) | `common-api`, `core-engine` |
| `modules/module-entities/` | Unified entity/player tables, AI goals, LuaBinder bindings | `common-api`, `core-engine` |
| `modules/module-interaction/` | World/block interaction, entity wrapping | `common-api`, `core-engine`, `module-entities` |
| `modules/module-network/` | Remote events/functions | `common-api`, `core-engine` |
| `modules/module-client/` | GuiService, ClientEffects, keybinds, RunService | `common-api`, `core-engine` |
| `modules/module-storage/` | World/player/session data stores | `common-api`, `core-engine` |
| `modules/module-tasks/` | Task scheduler bridge | `common-api`, `core-engine` |
| `modules/module-math/` | Vector3/Vector2/Color3, math/string extensions, `Curve` path samplers | `common-api`, `core-engine` |
| `modules/module-text/` | Minecraft text component builder (literal/translate/keybind/score, styles, JSON/legacy) | `common-api`, `core-engine` |
| `modules/module-interception/` | Anvil/brewing/trade interception | `common-api`, `core-engine` |
| `modules/module-command/` | Custom Brigadier top-level Lua command service | `common-api`, `core-engine` |
| `modules/module-loot/` | Loot table manipulation (mob drops, chest loot, block drops, fishing) | `common-api`, `core-engine` |
| `modules/module-worldgen/` | Ore, vegetation, mixed vein, and biome modifier planning | `common-api`, `core-engine` |
| `modules/module-noise/` | Deterministic Simplex, fBm, ridged, Voronoi, domain-warp noise | `common-api`, `core-engine` |
| `modules/module-dimensions/` | Custom dimensions + custom biomes (`Biomes` + `Dimensions`) | `common-api`, `core-engine` |
| `modules/module-spawnrules/` | Programmable Lua entity spawn rules per dimension | `common-api`, `core-engine` |
| `modules/module-structures/` | Single-template NBT structure registration and runtime placement | `common-api`, `core-engine` |
| `modules/module-update/` | Declarative update checker (https-only feeds) + permission-gated `Net:HttpGet` (`net.http`) | `common-api`, `core-engine` |
| `common/` | Shared Architectury Minecraft PAL implementation, registrars, mixins, commands | `common-api`, `core-engine`, `modules/*` |
| `neoforge/` | Runnable NeoForge 21.1.242 loader module (capabilities, fluids, JEI, events) | `common`, `modules/*`, NeoForge |
| `fabric/` | Runnable Fabric 1.21.1 loader module (transfer storage, rendering, loot/events) | `common`, `modules/*`, Fabric API |

Entrypoints: `com.luatweaker.common.LuaTweakerCommon`, `com.luatweaker.neoforge.LuaTweakerNeoForge` (`@Mod("luatweaker")`), `com.luatweaker.fabric.LuaTweakerFabric`.

---

# Build & Run

```sh
# full build (CI)
./gradlew build

# dev build + asset sync (fast iteration)
./gradlew :neoforge:classes :neoforge:syncLuaMods
./gradlew :fabric:classes :fabric:syncLuaMods

# run client or server
./gradlew :neoforge:runClient
./gradlew :neoforge:runServer
./gradlew :fabric:runClient
./gradlew :fabric:runServer

# all tests
./gradlew test

# single test class
./gradlew :modules:module-recipes:test --tests *EngineTest
```

Java 21 (Temurin), Gradle wrapper, Architectury Loom (NeoForge 21.1.242 + Fabric Loader 0.16.10). CI: `.github/workflows/build.yml`.

---

# LuaMod Directory (Game Run Dir)

- `luamods/<mod_id>/manifest.json` — identity + metadata (id, name, author, version, main, permissions, update_url)
- `luamods/<mod_id>/default_config.json` — defaults copied to `luaconfig/<mod_id>.json`
- `luamods/<mod_id>/main.lua` — single autonomous entrypoint (requires `src/startup`, `src/server`, `src/client`)
- `luamods/<mod_id>/assets/` + `data/` — auto-mounted virtual resourcepack & datapack roots
- `.luatweaker/stubs/` — auto-generated EmmyLua stubs (class inheritance included)
- `logs/luatweaker/mods/<mod_id>.log` — dedicated per-mod engine output

> **Network security (module-update):** update checks are declarative only — `"update_url": "https://..."` in manifest.json is fetched by the engine on the Java side (https-only) and exposed read-only via `mod:GetUpdateStatus()` / `Update` service. Free HTTP (`Net:HttpGet`) is denied by default; it requires the `"net.http"` manifest permission and triggers red warnings in chat/logs.

Dev workspace: `luamods/` -> synced to `neoforge/run/luamods/` and `fabric/run/luamods/` by `syncLuaMods`.

---

# Lua Script Style — Roblox (Luau) Convention

- `local` on all variables; implicit globals are STRICTLY BANNED (except engine-injected APIs).
- Colon method call: `object:method()`, chain up to ~3 calls max.
- Banner `-- ==== SECTION ====`, 4 spaces indent, max 120 chars, line break before operators.
- EmmyLua annotations for public functions: `---@param player Player`, `---@return boolean` (use class stubs `Player`/`Entity`).
- Naming: local `camelCase`, API export `PascalCase`, constants `UPPER_SNAKE_CASE`, boolean `is*/has*/can*`, callback `on*`.
- BANNED: `goto`, single-letter variables (except `i`), massive string concatenation (use `[[ ]]` / `table.concat`).
- Script order: banner → `require` → config (`mod:GetConfig()`) → constants → private functions → public API → final `print()`.

---

# In-Game Commands

- `/lt reload` — hot-reload Lua, regenerate stubs, re-apply recipes
- `/lt doctor` — health diagnostics: mods + per-mod load errors + engine/service status
- `/lt hand` — inspect held item/block
- `/lt syntax <file>` — syntax check
- `/lt list` — list loaded mods/scripts
- `/lt debug [on|off]` — toggle debug logging

---

# Runtime Architecture Notes

- **PAL** (`Platform`) — singleton abstraction, set once at mod initialization.
- **LuaBinder** — auto-binding from interfaces; interface is the single source of truth (bindings + stubs + docs).
- **Event bus** — shared map, replace semantics, cross-engine dispatch.
- **Item/Block handlers** — server-side only dispatch.
- **`LuavaEngine`** — luava 0.2.7 (Lua 5.4) implementation of `ILuaEngine`; synchronized VM entry points; `getAndClearLastExecutionError()` for per-mod load error tracking; `task._run_deferred` creates `LuaCoroutine` on virtual threads and waits bounded (2s) to avoid tick stalling.
- **`Signal:FireSync`** — synchronous, for render; `Signal:Fire` — asynchronous via task scheduler.
- **Clientbound network** — dispatched through the network service of the active engine.
- **ProjectileRegistry** — real custom projectile definitions (damage/explosionPower applied).
- **Lua execution stages**: `startup` (content registration) → `server` (runtime) → `client` (HUD/keybind) — all required via `main.lua`; reload instantiates a fresh engine + re-loads mods.

---

# Test Quirks

- Tests use `Mock*` interfaces — no Minecraft runtime required.
- `AsyncFileLogger` shuts down in `@AfterAll`.
- `RecipeScriptFixture.findScriptFile()` — resolves test Lua fixtures from `modules/module-recipes/src/test/resources/`.
- Every module has its own `build.gradle` with JUnit dependencies; new modules must add test dependencies if needed.

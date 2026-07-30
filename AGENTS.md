# LuaTweaker

Minecraft 1.21.1 NeoForge mod — Lua scripting engine with runtime recipe/content patching.

## Build & Run

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

## Project Structure

| module | role |
|---|---|
| `common-api/` | Pure Java 21 interfaces (PAL, VM, LuaDoc annotations). Zero deps. |
| `core-engine/` | Cobalt Lua engine wrapper, async logger, linter, EmmyLua stub gen. Fat-jars Cobalt 0.9.9. |
| `modules/module-recipes/` | Engine-agnostic recipe binding + Lua scripts. |
| `neoforge-platform/` | **Only runnable module**. NeoForge launcher, concrete PAL impl. Jar-in-Jar embeds all submodules. |

Entrypoint: `com.luatweaker.platform.LuaTweakerMod` (`@Mod("luatweaker")`).

## Lua Directory (`lua/`)

- `startup/` — item/block/fluid registration (mod loading phase)
- `server/` — recipes, events, worldgen, commands (server init)
- `client/` — GUI, keybinds, shaders
- `luamods/` — autonomous Lua mods (folders or `.zip`)
- `assets/` + `data/` — virtual resource/datapack roots
- `.luatweaker/stubs/` — auto-generated EmmyLua stubs (regenerated on reload)
- `logs/luatweaker.log` — engine output

Game directory: `run/`. The `syncLua` Gradle task copies `neoforge-platform/lua/` → `run/lua/` before each run.

## Build Gotchas

- **`syncLua`** must run before client/server launch (auto-wired in `neoforge-platform/build.gradle`).
- **`copyDevelopmentMods`** runs before `processResources` (copies JEI JAR to `run/mods`).
- **`generateModMetadata`** expands template properties from `gradle.properties` into `build/generated/sources/modMetadata`.
- **IDE sync** (`neoForgeIdeSync`) depends on all subproject `jar` tasks (root `build.gradle` `projectsEvaluated` block). After a clean build, run `./gradlew build` before opening IDE to avoid classpath errors.
- **`core-engine`** fat-jars Cobalt (`cc.tweaked:cobalt:0.9.9`) into its own output via `jar { from { configurations.runtimeClasspath.filter { ... } } }`.
- **`evaluationDependsOn`** in `neoforge-platform/build.gradle` ensures submodules evaluate first. Do not remove.

## Test Quirks

- Tests use `MockRecipeService` — no Minecraft runtime required.
- `AsyncFileLogger` must be shut down in `@AfterAll` to avoid dangling threads.
- The `EngineTest.findScriptFile()` helper checks relative paths then falls back to `../../` for IDE vs CLI execution.
- `testSyntaxErrorTraceback` relies on `Thread.sleep(1000)` for async logger flush — inherently timing-sensitive.

## Architecture Notes

- **PAL** (`com.luatweaker.api.pal.Platform`) — singleton abstraction over NeoForge. Set once at mod init.
- **Service Registry** — Lua-side `Mod:GetService("Recipes")` maps to `ILuaEngine.registerService()`. Maintained in `core-engine/src/.../LuaServiceRegistry.java`.
- **`InterceptionHelper`** — accumulates pending recipe modifications, flushed on `ServerAboutToStartEvent` / `AddReloadListenerEvent` / `/lt reload`. Cleared each cycle.
- **`@LuaDoc`** annotation on API interfaces drives auto-generated stub files (`luatweaker-api.lua`). Generated whenever stubs are enabled (`AUTO_GENERATE_STUBS` config, default true).
- **Lua execution stages**: `startup` files run during mod construction; `server` files run on server start and `/lt reload`; `client` files run on client init.

## In-Game Commands

- `/lt reload` — hot-reloads all Lua scripts, regenerates stubs, re-applies recipes
- `/lt doctor` — health diagnostics on loaded scripts
- `/lt hand` — inspect held item/block
- `/lt dump` — dump registries to logs

# LuaTweaker Java plugin template

Copy this folder out of the LuaTweaker repo and rename the package. No fork needed.

## Layout

- `src/main/java/com/example/myaddon/IMyAddonApi.java` - `@LuaDoc` interface (single source of truth).
- `src/main/java/com/example/myaddon/MyAddonApiImpl.java` - implementation (LOW-LEVEL primitives only).
- `src/main/java/com/example/myaddon/MyAddonPlugin.java` - `ILuaTweakerPlugin` entrypoint.
- `src/main/resources/META-INF/services/...` - ServiceLoader declaration (Path B).
- `build.gradle.example` - rename to `build.gradle` and adjust the GAV.

## Publish (pick one)

**Path A - separate NeoForge or Fabric mod (recommended when touching Minecraft APIs):**

```java
// NeoForge (@Mod)
public MyAddonMod(IEventBus bus, ModContainer container) {
    LuaTweakerPluginRegistry.register(new MyAddonPlugin());
}

// Fabric (ModInitializer)
@Override
public void onInitialize() {
    LuaTweakerPluginRegistry.register(new MyAddonPlugin());
}
```

**Path B - plain jar (no loader mod needed):** build the jar, drop it on the classpath
(`mods/` folder of an already-installed LuaTweaker). The engine finds it via ServiceLoader
on every `/lt reload`.

## Rules

- One method = one bridge call (`putMarker`, `getMarker`). No `castMagicSpell()` god-methods.
- Bind with `LuaBinder.bind(engine, name, impl, apiClass)` so `camelCase` + `PascalCase` both exist.
- `@LuaDefault` for optional trailing args; missing required args must throw (never silent no-op).
- Never import `net.minecraft.*` / `net.neoforged.*` / `net.fabricmc.*` outside a platform module - go through `Platform.getX()`.
- Every failure path must log via `LuaTweakerLog` with context; a throwing plugin only fails itself.
- Lua usage:

```lua
local MyAddon = require("LuaTweaker.MyAddon")
MyAddon:PutMarker("spawn", "village")
assert(MyAddon:GetMarker("spawn") == "village")
```

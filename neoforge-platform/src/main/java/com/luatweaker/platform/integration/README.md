# Platform Integrations (third-party mod compat)

One subpackage per **external** mod goes here — never in `common-api`,
`core-engine`, or `modules/*` (those layers must stay free of
`net.minecraft.*`, `net.neoforged.*`, and third-party mod APIs by
architecture law; see `AGENTS.md` Section 4):

```
platform/integration/
  geckolib/   bundled via Jar-in-Jar, always present (animated models/items/mobs)
  jei/        optional soft dep, only loads when JEI is installed (recipe views)
  mekanism/   (future) conditional compat, guarded by ModList presence checks
  curios/     (future)
  create/     (future)
```

Rules for a new integration subpackage:

1. **No imports from core backwards**: core `modules/*` may only know the
   integration through data already declared in `common-api`
   (e.g. `GeoModelSpec`) or through the `Platform` PAL. The integration
   reads core state; core never imports the integration.
2. **Soft by default**: an absent host mod must never crash the game.
   Guard with `ModList.get().isLoaded("<modid>")` (see
   `NeoForgeContentPlatform.isModLoaded`) and log loudly instead of failing.
   Hard-bundling (`jarJar`) is allowed only when the feature cannot degrade
   (GeckoLib rendering today).
3. **Declare the Gradle dependency next to the code**: `compileOnly` for
   optional hosts (plus `developmentMods` for the dev client), `implementation`
   + `jarJar` only for bundled ones. Keep `neoforge.mods.toml` soft deps in
   sync (`[[dependencies.<modid>]]` with `optional = true`).
4. **Mirror the Lua surface**: if Lua scripts touch the integration
   (`Jei` recipe views, `:GeoModel(...)`), the Lua-facing knobs stay in the
   owning core module (`module-recipes`, `module-content`); only the
   platform-side application lives here.

# Shaders (`Shaders`)

Screen-space visual effects. Loaded in every engine as the `Shaders` global
(`require("LuaTweaker.Shaders")`).

Two tiers, one stable Lua surface across render backends:

- **Tier 1 — named effects** (`setEffect`): camera/overlay presets plus
  shader-backed presets (grade, grain, blur, glitch, …). Backend-agnostic.
- **Tier 2 — custom passes**: `registerGlslPass` (GLSL body subset) and
  `registerSpirvPass` (raw SPIR-V translated on-CPU via SPIRV-Cross) publish
  vanilla post-chain files through the virtual pack; the `GameRenderer` post
  pipeline runs them. Dynamic values go through `setPassUniform` (float) and
  `setPassUniformVec` (vec2/3/4); `Time`/`ScreenSize` update every frame by the
  pipeline itself.

Java owns the pipeline (validation, file generation, activation); Lua owns the
choreography (when to kick, fade, or swap passes).

> Remaining follow-up: production bundling of the SPIRV-Cross natives (dev and
> tests translate today; without natives that one path fails loudly while GLSL
> stays fully working).

---

## 1. Setup and backend query

```lua
local Shaders = require("LuaTweaker.Shaders")

print(Shaders:getBackend())           -- "opengl" on 1.21.1, "vulkan" later, "unknown" headless
print(Shaders:supportsCustomPasses()) -- false on dedicated servers (no client)
```

On a dedicated server every call degrades to a debug-logged no-op (same rule as
`ClientEffects`); single-player and clients get the real behavior.

## 2. Tier 1: named effects

### 2a. Camera and overlays (no shaders involved)

```lua
Shaders:setEffect("kick", { strength = 3.0, direction = 0.0, seconds = 0.3 })
Shaders:setEffect("roll", { degrees = 10.0, seconds = 0.6 })
Shaders:setEffect("sway", { amplitude = 1.5, seconds = 3.0 })
Shaders:setEffect("fovPunch", { amount = 12.0, seconds = 0.4 })  -- legacy shake lives in Camera:shake

Shaders:setEffect("fadeTo", { color = "#000000", seconds = 0.5 }) -- ramps in, then holds
Shaders:setEffect("letterbox", { height = 0.12 })
Shaders:setEffect("vignette", { strength = 0.6, color = "#000000" })
Shaders:setEffect("tint", { strength = 0.3, color = "#FF0000" })
Shaders:setEffect("lowHealthPulse", { strength = 0.7 })  -- beats only under 35% HP
Shaders:setEffect("frost", { strength = 0.5 })
Shaders:setEffect("bloodSplatter", { strength = 0.6 })

Shaders:clearEffect("letterbox")
Shaders:clearAll()  -- every effect + the custom pass
```

Camera offsets compose additively with the legacy `Camera:shake`. Overlays draw
above the Lua HUD at the lowest render priority.

### 2b. Shader-backed presets (one post slot, last activation wins)

| Effect | Params (defaults) | Look |
| :--- | :--- | :--- |
| `grade` | `brightness` 0, `contrast` 1, `saturation` 1 | color correction |
| `sepia` | `strength` 0.8 | vintage brown |
| `noir` | `strength` 0.9 | high-contrast black & white |
| `temperature` | `amount` 0.5 (-1 cold … 1 warm) | cold blue / warm orange |
| `nightVision` | `strength` 1.0 | green phosphor + scanlines |
| `grain` | `strength` 0.4 | animated film grain |
| `blur` | `radius` 3.0 (px) | soft focus |
| `radialBlur` | `strength` 0.6 | speed-zoom blur from the center |
| `chromaticAberration` | `offset` 2.0 (px) | RGB fringe |
| `pixelate` | `cellSize` 8.0 (px) | retro mosaic |
| `glitch` | `intensity` 0.5 | slice shift + channel split |
| `scanlines` | `strength` 0.6 | CRT lines |
| `wobble` | `amplitude` 4.0, `frequency` 3.0 | heat-haze warp |

```lua
Shaders:setEffect("grain", { strength = 0.4 })
Shaders:setEffect("nightVision", { strength = 1.0 })
Shaders:clearEffect("grain")  -- deactivates if it is the live pass
```

Rules for all named effects:

- Unknown names fail loudly with the supported list (no silent typos).
- Omitted params fall back to defaults; out-of-range numbers clamp with a
  warning; `NaN`/infinite/type errors are loud.
- Colors are `'#RRGGBB'` / `'#AARRGGBB'` (also `0x…`, also plain integers).
- Activating anything (preset or custom pass) replaces the live chain: one
  post slot, `getActivePass()` reports what holds it.
- Tunables belong in `mod:GetConfig()` — never hardcode them next to logic.

## 3. Tier 2: custom passes

### 3a. GLSL body subset

Write the fragment **body only**. The engine injects `#version 150`,
`uniform sampler2D DiffuseSampler`, `Time`, `InSize`, `OutSize`, `ScreenSize`,
`in vec2 texCoord`, `out vec4 fragColor` — declaring any of those yourself is a
loud error, because the driver would report a duplicate-declaration failure.

```lua
Shaders:registerGlslPass("warp", [[
void main() {
    vec2 uv = texCoord;
    float w = sin(uv.y * 20.0 + Time * 4.0) * 0.01;
    vec4 base = texture(DiffuseSampler, uv + vec2(w, 0.0));
    fragColor = vec4(base.rgb, base.a);
}
]])
Shaders:activatePass("warp")   -- replaces any active pass
Shaders:setPassUniform("Glow", 0.75)
Shaders:setPassUniformVec("Tint", { 1.0, 0.5, 0.0 })  -- vec2/3/4, 2-4 numbers
print(Shaders:getActivePass()) -- "warp"
Shaders:deactivatePass()
```

### 3b. Raw SPIR-V

```lua
Shaders:registerSpirvPass("aura", "<base64 SPIR-V fragment module>")
Shaders:activatePass("aura")
```

Validated (magic, word count, 256KB cap), translated to desktop GLSL 150 by
SPIRV-Cross on the CPU, then published exactly like a GLSL pass. Contracts:

- Name your input sampler **`DiffuseSampler`** (the only bound name).
- The output is a complete shader with its own header — it bypasses the
  body-subset rules, but `void main` is still required.
- Your uniform names are yours: animate them per tick with
  `setPassUniform`/`setPassUniformVec`. The pipeline auto-sets only its own
  `Time`/`InSize`/`OutSize`/`ScreenSize`.
- Needs the SPIRV-Cross natives (present in dev and tests); without them this
  fails loudly and GLSL stays fully working.

### 3c. What the engine does with a pass id

1. Validates the source (body rules or SPIR-V rules).
2. Generates three virtual files (same shape as the vanilla `invert` chain,
   verified against 1.21.1 assets): `shaders/post/lt_<id>.json` (main → swap →
   blit), `shaders/program/lt_<id>.json` (reuses the vanilla `blit` vertex
   program), `shaders/program/lt_<id>.fsh` (your fragment).
3. Serves them from RAM via the virtual pack — no physical files.
4. Writes a SHA-256 marker under `luatweaker/shader-cache/`; unchanged sources
   skip regeneration on the next launch.
5. `activatePass` loads the chain through `GameRenderer.loadEffect`
   (vanilla closes the previous chain first, so a missing-file failure degrades
   to normal rendering + a log warning).

Rules:

- ids match `[a-z0-9_]{1,48}`; max 16 passes.
- Re-registering an id replaces it (reload-safe; re-published every time
  because a reload wipes the virtual-files map).
- `setPassUniform*` fail loudly with no active pass, blank names, non-finite
  values, or wrong vec arity. Vec uniforms must be declared at the matching
  size in your fragment.
- After an F3+T resource reload, re-call `activatePass` (register from
  `src/startup`, activate from `src/server` init — the standard split).
- `supportsCustomPasses()` gates all of this; check it before building
  pass-dependent flows.

## 4. Debugging shaders (two layers, both name names)

**Layer 1 — register time (instant, with line numbers).** `registerGlslPass`
lints the body before publishing: redeclared engine names, GLSL 1.00 idioms
(`texture2D(`, `gl_FragColor`, `attribute`, `varying`), and unbalanced
delimiters all fail with `pass id + line N + fix`:

```text
Shaders:registerGlslPass('warp') line 4: 'texture2D(' is GLSL 1.00; use 'texture(' in this 150 pipeline.
Shaders:registerGlslPass('warp') line 9: unbalanced '}' — nothing to close here.
```

Comments and `"strings"` are masked before checking, so examples in comments
never trip the lint.

**Layer 2 — activation time (driver verdict).** A body can pass the lint and
still be rejected by the driver (type errors, undeclared identifiers). The
driver reports those as `IOException("Couldn't compile …: <info log>")` with
its own line numbers — vanilla swallows it into a warn. The platform
re-checks after every activation: success lands a debug line naming the
chain, failure a loud warn naming the pass plus where to look:

```text
[Shaders] pass 'luatweaker:shaders/post/lt_warp.json' failed to activate — the driver
rejected the chain. Look above for "Couldn't compile" with the driver's line numbers,
then fix the fragment and re-register.
```

If nothing changes on screen after `activatePass`, that warn (or its absence
meaning success) is the first thing to check. After an F3+T resource reload,
re-call `activatePass` — the chain is re-read from the virtual pack then.

## 5. Worked example: ore radar (complex pass + live uniforms)

Right-click the Crystal Detector and the screen becomes a radar: range rings,
a rotating sweep with trail, and orange blips toward real scanned ores.
`luamods/luatweaker_test/src/client/radar_demo.lua`
(listen on the existing `HighlightOres` event, no item changes needed):

```lua
Shaders:registerGlslPass("radar_sweep", [[
uniform vec3 BlipA; uniform vec3 BlipB; uniform vec3 BlipC;
uniform float Count; uniform float Strength;
-- rings, sweep beam, trail, blips from BlipA/B/C (xz dir, z = closeness)
void main() { ... }
]])
-- per scan: nearest 3 ores -> setPassUniformVec("BlipA", {dx, dz, close})
Shaders:activatePass("radar_sweep")  -- auto-cleared after radar_demo.duration_seconds
```

Lua owns data (scan → direction/distance math, cheapest-first ordering) and
choreography (activate, per-tick uniforms, timed deactivate); GLSL owns
pixels. Copy the pattern for boss telegraphs, sonar pulses, or aura vision.

## 6. Cost rules

1. One chain at a time; `activatePass` closes the previous one.
2. Overlays are fullscreen rects: combine, don't stack five tints.
3. Custom passes cost a fullscreen blit per frame — prefer named effects for
   always-on looks, passes for moments.
4. Uniform writes are fire-and-forget to the client thread; batch them per tick.

## 7. Forward path (Vulkan)

Tier 1 needs no changes on any backend. Tier 2 bodies stay inside the
header-injected subset precisely so a future backend can compile the same
source to SPIR-V (via shaderc/glslang, added then) — Lua does not change.
SPIR-V authored today already *is* the portable IR; only its native bundling
for production remains open.

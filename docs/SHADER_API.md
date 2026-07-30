# 🎬 Shader & Post-Processing API (`ShaderAPI`)

> **Access:** `game:GetService("Shaders")`, or globals `shaders` / `render`  
> **Source:** [ShaderAPI.java](../src/main/java/kien/luatweaker/api/render/ShaderAPI.java)

LuaTweaker's Shader API gives Lua modpack creators Hollywood-grade visual effects that KubeJS simply cannot match. Every effect is controlled in pure Lua — no shader file editing required.

---

## 📋 Quick Reference Table

| Method | Description | Range |
|--------|-------------|-------|
| `shaders:shakeCamera(intensity, seconds)` | Earthquake screen shake | intensity ≥ 0, seconds ≥ 0 |
| `shaders:setCameraShake(intensity)` | Set persistent shake (no timer) | 0.0 – ∞ |
| `shaders:stopCameraShake()` | Instantly stop all shaking | — |
| `shaders:setFilmGrain(intensity)` | Film grain / noise overlay | 0.0 – 1.0 |
| `shaders:setChromaticAberration(offset)` | RGB color fringe distortion | 0.0 – 1.0 |
| `shaders:setVignette(intensity, color)` | Dark or tinted screen border | 0.0 – 1.0, Color3/hex |
| `shaders:setBlur(radius)` | Gaussian blur | 0.0 – ∞ |
| `shaders:setColorCorrection(b, c, s)` | Brightness/Contrast/Saturation | ≥ 0.0 each |
| `shaders:setFilter(name)` | Built-in filter preset | `"sepia"`, `"noir"`, `"invert"`, `"none"` |
| `shaders:createShader(id, frag [, vert])` | Register custom GLSL shader | GLSL strings |
| `shaders:loadPostShader(path)` | Activate a post-processing shader | Resource path |
| `shaders:disablePostShader()` | Deactivate custom shader | — |
| `shaders:setUniform(name, ...)` | Pass float uniforms to shader | float values |
| `shaders:clearAllEffects()` | Reset everything to defaults | — |

---

## 🎯 Usage Examples

### 1. Camera Shake on Boss Encounter

```lua
local shaders = game:GetService("Shaders")

-- Shake for 3 seconds at intensity 1.5
shaders:shakeCamera(1.5, 3.0)
```

### 2. Horror Atmosphere: Film Grain + Vignette

```lua
local shaders = game:GetService("Shaders")

shaders:setFilmGrain(0.4)
shaders:setVignette(0.7, Color3.fromRGB(80, 0, 0))
shaders:setChromaticAberration(0.03)
```

### 3. Underwater Vision: Blur + Color Correction

```lua
local shaders = game:GetService("Shaders")

shaders:setBlur(2.5)
shaders:setColorCorrection(0.8, 0.9, 0.6)
shaders:setVignette(0.3, Color3.fromHex("#003355"))
```

### 4. Sepia Vintage Filter

```lua
local shaders = game:GetService("Shaders")

shaders:setFilter("sepia")
shaders:setFilmGrain(0.15)
```

### 5. Custom GLSL Shader (Advanced)

```lua
local shaders = game:GetService("Shaders")

-- Register a custom fragment shader written in GLSL
shaders:createShader("boss_aura", [[
    #version 150
    uniform sampler2D DiffuseSampler;
    uniform float Time;
    in vec2 texCoord;
    out vec4 fragColor;

    void main() {
        vec4 color = texture(DiffuseSampler, texCoord);
        float pulse = sin(Time * 3.0) * 0.5 + 0.5;
        color.r += pulse * 0.2;
        fragColor = color;
    }
]])

-- Activate the shader
shaders:loadPostShader("luatweaker:shaders/post/boss_aura")

-- Pass dynamic uniform values
shaders:setUniform("Time", os.clock())
```

### 6. Reset All Effects

```lua
shaders:clearAllEffects()
```

---

## ⚙️ How It Works Internally

1. **Built-in effects** (`filmGrain`, `blur`, `vignette`, etc.) are Java state fields applied during Minecraft's `RenderLevelStageEvent`.
2. **Camera Shake** applies random offset to the camera matrix each tick for `durationSeconds × 20` ticks, then auto-stops.
3. **Custom GLSL shaders** are injected at runtime into LuaTweaker's **Virtual ResourcePack** so Minecraft loads them as if they were regular Core Shader files (no physical files needed).

---

## 🆚 KubeJS Comparison

| Feature | LuaTweaker | KubeJS |
|---------|-----------|--------|
| Camera Shake | ✅ `shaders:shakeCamera()` | ❌ Not available |
| Film Grain | ✅ Built-in | ❌ Not available |
| Chromatic Aberration | ✅ Built-in | ❌ Not available |
| Custom GLSL Shader | ✅ `shaders:createShader()` | ❌ Not available |
| Color Correction | ✅ Built-in | ❌ Not available |
| Performance | ✅ Zero-copy, no wrappers | ❌ Rhino JS wrappers |

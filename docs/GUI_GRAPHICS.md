# 🎨 GUI Graphics & Screen Rendering API (`GuiGraphicsAPI`)

> **Access:** `game:GetService("GuiGraphics")`, or globals `gui` / `graphics`  
> **Source:** [GuiGraphicsAPI.java](../src/main/java/kien/luatweaker/api/gui/GuiGraphicsAPI.java)

LuaTweaker's GUI Graphics API provides low-level control over Minecraft's `GuiGraphics` and `PoseStack` rendering pipeline. Draw rectangles, text, textures, items, tooltips, and apply matrix transformations — all from Lua.

---

## 📋 Quick Reference Table

| Method | Description |
|--------|-------------|
| `gui:fill(x1, y1, x2, y2, color)` | Fill a solid rectangle |
| `gui:fillGradient(x1, y1, x2, y2, colorTop, colorBottom)` | Fill a vertical gradient rectangle |
| `gui:drawString(text, x, y, color, shadow)` | Draw text with optional drop shadow |
| `gui:drawCenteredString(text, x, y, color)` | Draw center-aligned text |
| `gui:drawTexture(path, x, y, u, v, w, h, texW, texH)` | Blit a texture from a resource path |
| `gui:drawItem(itemId, x, y)` | Render a Minecraft item icon |
| `gui:drawTooltip(text, x, y)` | Show a floating tooltip |
| `gui:pushPose()` | Push matrix onto the PoseStack |
| `gui:popPose()` | Pop matrix from the PoseStack |
| `gui:scale(x, y, z)` | Scale the current PoseStack matrix |
| `gui:translate(x, y, z)` | Translate the current PoseStack matrix |

---

## 🎯 Usage Examples

### 1. Draw a HUD Overlay

```lua
local gui = game:GetService("GuiGraphics")

-- Dark background panel
gui:fill(10, 10, 200, 60, Color3.fromRGB(20, 20, 20))

-- Title text with shadow
gui:drawString("⚔ Boss Health", 15, 15, "#FFD700", true)

-- Health bar using gradient (green → red)
gui:fillGradient(15, 30, 190, 50, Color3.fromHex("#00FF00"), Color3.fromHex("#FF0000"))
```

### 2. Render Items and Tooltips

```lua
local gui = game:GetService("GuiGraphics")

-- Draw a diamond sword icon
gui:drawItem("minecraft:diamond_sword", 20, 80)

-- Show tooltip near it
gui:drawTooltip("§b✨ Excalibur (Lv.99)", 40, 80)
```

### 3. Draw Texture from ResourcePack

```lua
local gui = game:GetService("GuiGraphics")

-- Blit a 128x32 banner from a mod texture atlas (256x256)
gui:drawTexture("luatweaker:textures/gui/boss_banner.png", 10, 100, 0, 0, 128, 32, 256, 256)
```

### 4. PoseStack Matrix Transformations

```lua
local gui = game:GetService("GuiGraphics")

-- Scale up 1.5x and translate right
gui:pushPose()
gui:scale(1.5, 1.5, 1.0)
gui:translate(20, 0, 0)

gui:drawCenteredString("BOSS ENCOUNTER", 100, 50, Color3.new(1, 0.2, 0.2))

gui:popPose() -- Restore original matrix
```

### 5. Combining with Shader API for Boss Fight HUD

```lua
local gui = game:GetService("GuiGraphics")
local shaders = game:GetService("Shaders")

-- Shake camera
shaders:shakeCamera(1.0, 2.0)
shaders:setVignette(0.5, Color3.fromRGB(200, 0, 0))

-- Draw boss health overlay
gui:fill(50, 10, 350, 50, Color3.fromRGB(30, 0, 0))
gui:fillGradient(55, 15, 345, 45, Color3.fromHex("#FF0000"), Color3.fromHex("#880000"))
gui:drawCenteredString("§c§lENDER DRAGON", 200, 20, "#FFFFFF")
gui:drawCenteredString("████████████████", 200, 32, "#FF4444")
```

---

## ⚙️ Color Argument Formats

The `color` parameter accepts multiple formats:

| Format | Example | Description |
|--------|---------|-------------|
| `Color3.new(r, g, b)` | `Color3.new(1, 0.5, 0)` | Float RGB (0.0–1.0) |
| `Color3.fromRGB(r, g, b)` | `Color3.fromRGB(255, 128, 0)` | Integer RGB (0–255) |
| `Color3.fromHex(hex)` | `Color3.fromHex("#FF8800")` | Hex string |
| `"#RRGGBB"` | `"#FF8800"` | Plain hex string |
| Integer | `0xFF8800` | Raw integer RGB value |

---

## 🆚 KubeJS Comparison

| Feature | LuaTweaker | KubeJS |
|---------|-----------|--------|
| Fill Rectangle | ✅ `gui:fill()` | ❌ Limited |
| Gradient Fill | ✅ `gui:fillGradient()` | ❌ Not available |
| Draw String | ✅ With shadow control | ⚠️ Basic only |
| Draw Item Icon | ✅ `gui:drawItem()` | ⚠️ Complex setup |
| PoseStack Control | ✅ push/pop/scale/translate | ❌ Not exposed |
| Tooltip Rendering | ✅ `gui:drawTooltip()` | ⚠️ Event-only |

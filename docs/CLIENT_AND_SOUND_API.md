# Client & Sound API (actual surface)

Everything below is implemented and bound today. Every entry is a primitive: the engine
plays/stops/draws exactly what you ask, and Lua owns the gameplay logic.

> **Where it does nothing:** on a dedicated server there is no client. `ClientEffects`
> calls become no-ops that log at debug level, `GuiService` is bound to a headless
> implementation (same method names; draw calls are reported once and ignored, and
> `OnRenderHUD` does not exist — use its absence to detect a non-client process), and
> `UserInputService:IsKeyDown` stays `false`. `World:*` sound calls do work there because
> they broadcast to players.

---

## 1. Client sounds (`ClientEffects`)

| Call | Defaults | Returns |
|---|---|---|
| `ClientEffects:PlaySound(id [, volume [, pitch [, source [, loop]]]])` | volume 1, pitch 1, source `MASTER`, loop false | handle for `loop=true`, else `0` |
| `ClientEffects:PlaySoundAt(id, x, y, z [, volume [, pitch [, source [, loop]]]])` | volume 1, pitch 1, source `NEUTRAL`, loop false | handle for `loop=true`, else `0` |
| `ClientEffects:StopSound(handle)` | — | `true` if the handle was still playing |
| `ClientEffects:StopSounds(id [, source])` | source `MASTER` | — |
| `ClientEffects:StopAllSounds()` | — | number of Lua handles dropped |
| `ClientEffects:GetActiveSoundCount()` | — | tracked looping sounds |
| `ClientEffects:SpawnParticle(id, x, y, z [, vx, vy, vz])` | velocity 0 | — |
| `ClientEffects:FlashScreen("#AARRGGBB" [, seconds])` | seconds 0.3 | — |

- **Category** is a Minecraft `SoundSource`: `MASTER`, `MUSIC`, `RECORDS`, `WEATHER`,
  `BLOCKS`, `HOSTILE`, `NEUTRAL`, `PLAYERS`, `AMBIENT`, `VOICE`. It selects which volume
  slider the sound follows.
- `PlaySound` is **non-positional** (UI feedback, no panning). `PlaySoundAt` is positional
  with linear distance falloff.
- Only **looping** sounds get a handle (that is what `StopSound` needs). One-shot sounds
  return `0`; use `StopSounds(id, source)` to cut all instances of an id.
- At most 256 looping handles are tracked; starting more stops the oldest and logs a
  warning. Call `StopSound` when a loop ends.
- `volume <= 0` mutes, `pitch <= 0` is raised to `0.01` (the audio channel rejects 0).
- Client ids resolve through `sounds.json`, so resource-pack/custom ids work here. On a
  dedicated server use registered sound events (`World:PlaySound`).

## 2. World sounds (`World`) — server side, broadcast

| Call | Notes |
|---|---|
| `World:PlaySound(id, pos [, volume [, pitch [, source]]])` | `pos` is `Vector3` or `x, y, z` |
| `World:PlaySound(id, x, y, z [, volume [, pitch [, source]]])` | source defaults to `PLAYERS` |
| `World:PlaySound(dimension, id, ...)` | optional leading dimension overrides the default `minecraft:overworld` |
| `World:StopSound(id [, source])` | sends a stop packet to every player in the dimension |
| `World:StopSound(dimension, id [, source])` | — |

- A missing position is **rejected** (previously it silently played at 0,0,0).
- Unknown/unregistered sound events are reported and skipped; the server cannot send
  resource-pack-only ids because the packet carries a registry holder.
- `StopSound("id", "PLAYERS")` is unambiguous: the second string is detected as a
  category, so the first string stays the sound id.

## 3. Entity and player sounds

```lua
mob:PlaySound("minecraft:entity.zombie.ambient")                -- NEUTRAL at the mob
mob:PlaySoundFrom("minecraft:entity.zombie.ambient", 1.0, 1.0, "HOSTILE")
player:PlaySound("minecraft:entity.player.levelup")             -- PLAYERS at the player
player:PlaySoundFrom("minecraft:entity.player.levelup", 0.5, 1.2, "MASTER")
```

Both are server-side broadcasts at the entity's position and require a **registered**
sound event id.

## 4. HUD drawing (`GuiService`) — inside `GuiService.OnRenderHUD`

Text/shape primitives: `DrawRect`, `DrawOutline`, `DrawGradient`, `DrawText`,
`DrawTextCentered`, `DrawProgressBar`, `DrawLine`, `MeasureText`, `GetScreenSize`.

Textures and items: `DrawTexture` (full texture path), `DrawSprite` (vanilla GUI sprite
atlas id such as `minecraft:hud/experience_bar_background`), `DrawItem(itemId, x, y
[, count])`, `DrawTooltip(text, x, y)`.

Clipping and transforms:

```lua
GuiService:PushClip(x, y, w, h)   -- nestable
GuiService:PushPose()
GuiService:Translate(dx, dy)      -- z optional (default 0)
GuiService:Scale(sx, sy)          -- z optional (default 1)
GuiService:DrawSprite("minecraft:hud/hotbar", 0, 0, 182, 22)
GuiService:PopPose()
GuiService:PopClip()
```

Unclosed clips and poses are reset at the end of the frame, so a broken script cannot
distort the rest of the frame.

Screens:

```lua
local screen = GuiService:CreateScreen("spell_book", "Spell Book")
screen.OnRender = function(self, mouseX, mouseY, partialTick) ... end
screen.OnMouseClick = function(self, mouseX, mouseY, button) return true end
screen.OnMouseRelease = function(self, mouseX, mouseY, button) return true end
screen.OnMouseMove = function(self, mouseX, mouseY, button, dragX, dragY) end  -- button = -1 while hovering
screen.OnScroll = function(self, mouseX, mouseY, scrollX, scrollY) return true end
screen.OnKeyDown = function(self, keyCode, scanCode, modifiers) return true end
screen.OnKeyUp = function(self, keyCode, scanCode, modifiers) return true end
screen.OnCharTyped = function(self, codePoint, modifiers) return true end
screen.OnClose = function(self) end
screen:Open()
```

Returning `true` consumes the event. `GuiService:OpenScreen(id)`,
`GuiService:CloseScreen()` and `GuiService:IsScreenOpen()` operate on the active screen.

## 5. Raw input (`UserInputService`)

```lua
UserInputService.InputBegan:Connect(function(inputType, code, action, modifiers, gameProcessed)
    if gameProcessed then return end       -- chat / inventory / pause screen has focus
    if inputType == "key" and code == 87 then ... end   -- GLFW key ids
end)
UserInputService.InputEnded:Connect(function(inputType, code, action, modifiers, gameProcessed) ... end)

if UserInputService:IsKeyDown(87) then ... end
if UserInputService:IsMouseButtonDown(0) then ... end
```

`inputType` is `"key"` or `"mouse"`, `code` is the GLFW key/button id, `action` is GLFW
1=press / 0=release, `modifiers` is the GLFW modifier bitmask. Key and mouse state are
tracked in separate sets. Dispatch is synchronous.

## 6. Camera

```lua
Camera:Shake(1.5, 0.5)   -- intensity, seconds
Camera:Stop()
```

`Shake` with non-positive values cancels the current shake. Result is added to the
vanilla yaw/pitch/roll during the frame render.

## 7. TweenService

`TweenInfo.new(time, easingStyle, easingDirection, repeatCount, reverses, delayTime)`.

All six fields are honored: `DelayTime` postpones the first write, `RepeatCount = -1`
loops forever, `Reverses = true` plays every second cycle backwards (ping-pong).

Easing styles: `Linear`, `Sine`, `Quad`, `Cubic`, `Quart`, `Quint`, `Expo`, `Circ`,
`Back`, `Elastic`, `Bounce`. Directions: `In`, `Out`, `InOut`.

```lua
local tween = TweenService:Create(bar, TweenInfo.new(0.4, "Quad", "Out"), { Percent = 0.0 })
tween:Play()
tween:Pause()
tween:Cancel()
tween.Completed:Connect(function() print("done") end)
```

Values are written as numbers into the target table; property names are raw table keys
(the tween does not call metatable setters such as entity property aliases).

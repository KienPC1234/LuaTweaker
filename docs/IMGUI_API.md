# ImGui API (advanced client UI)

`require("LuaTweaker.ImGui")` — Dear ImGui 1.88 for config screens, machine
panels, debug overlays and inspectors. Method style: `ImGui:Begin(...)`.

For simple HUD (health bars, text, progress) prefer `GuiService.OnRenderHUD`:
cheaper, no frame rules. ImGui pays one full overlay frame per tick — use it
where interaction (clicks, drags, text input, trees) earns the cost.

## 1. The one rule: widgets live inside OnFrame

The engine opens an ImGui frame once per render tick, fires
`ImGui.OnFrame` **synchronously on the render thread**, then closes the
frame. Every widget call outside that signal is a Lua error naming the
signal:

```text
[ImGui] Button must be called inside ImGui.OnFrame (connect with ImGui.OnFrame:Connect(function() ... end)).
```

```lua
local ImGui = require("LuaTweaker.ImGui")
if ImGui:GetVersion() == "unavailable" then
    return true -- dedicated server: no client, skip like every client demo
end

local show = true
local flow = 50.0
ImGui.OnFrame:Connect(function()
    show = ImGui:Begin("Arcane Panel", show, "NoResize")
    if not show then ImGui:End() return end
    flow = ImGui:SliderFloat("Mana flow", flow, 0.0, 100.0)
    if ImGui:Button("Pulse!") then
        print("pulsed at " .. flow)
    end
    ImGui:End() -- every Begin needs its End, even when Begin returns false
end)
```

Widgets **return their new value** — change detection stays in Lua by
comparison (`flow = ImGui:SliderFloat(...)`). Text fields return the edited
string; store it back or the next frame resets the field.

## 2. Windows and layout

| Lua | Notes |
|---|---|
| `Begin(title, open?, flags?) -> open` | `open` defaults true; returns false after [X]. `flags` CSV: `NoTitleBar,NoResize,NoMove,NoScrollbar,NoCollapse,AlwaysAutoResize,NoBackground,NoMouseInputs,MenuBar,HorizontalScrollbar,NoFocusOnAppearing,NoBringToFrontOnFocus,AlwaysVerticalScrollbar,AlwaysHorizontalScrollbar,NoNavInputs,NoDecoration`. Unknown names fail fast with the valid list. |
| `End()` | Pair 1:1 with Begin. |
| `BeginChild(id, w, h) -> open` / `EndChild()` | Scrollable sub-region. |
| `SetNextWindowSize(w, h)` / `SetNextWindowPos(x, y)` | Call **before** Begin. |
| `SameLine()` `NewLine()` `Spacing()` `Separator()` `Indent()` `Unindent()` `SetCursorPosX(x)` | Layout primitives. |
| `Text(msg)` `BulletText(msg)` | Labels. |

## 3. Widgets (all return the new state)

| Lua | Returns |
|---|---|
| `Button(label)` / `SmallButton(label)` | `true` on click |
| `Checkbox(label, checked)` | new checked |
| `RadioButton(label, active)` | `true` on click |
| `SliderFloat(label, v, min, max)` / `SliderInt(...)` | new number |
| `DragFloat(label, v, speed, min, max)` / `DragInt(...)` | new number |
| `InputText(label, current)` / `InputTextMultiline(label, current)` | new string |
| `InputInt(label, v)` / `InputFloat(label, v)` | new number |
| `Combo(label, current, items)` | new **1-based** selection (Lua convention); empty list echoes without touching the platform; out-of-range input is clamped |
| `ColorEdit3(label, r, g, b)` / `ColorEdit4(label, r, g, b, a)` | `{r, g, b[, a]}` 0-1 |
| `ProgressBar(fraction, label?)` | — (fraction clamped 0-1) |
| `CollapsingHeader(label)` | `true` when open — draw the body only then |
| `TreeNode(label)` / `TreePop()` | `true` when open; pair 1:1 |
| `Selectable(label, selected)` | `true` on click |
| `PushStyleColor(name, argb)` / `PopStyleColor(count?)` | theme override (ARGB int, same convention as GuiService). Names: `Text,TextDisabled,WindowBg,Button,ButtonHovered,ButtonActive,FrameBg,FrameBgHovered,FrameBgActive,Header,HeaderHovered,HeaderActive,CheckMark,SliderGrab,SliderGrabActive,TitleBg,TitleBgActive,Separator,Border`. Unknown names fail fast. |

## 4. Item slots (preview only — read this)

```lua
if ImGui:ItemIcon("luatweaker_test:crystal_sword", 32, 1) then
    picked = "luatweaker_test:crystal_sword"
end
```

`ItemIcon(itemId, sizePx?, count?) -> clicked` draws the item with its count
overlay at the cursor and reports clicks. It is **not** a container slot:
no shift-click, no stack merging, no server sync. Wire real inventory
yourself (Network/Storage, like every other game rule) and keep containers
in `GuiService` screens. Unknown ids draw nothing (one debug line per id).

## 5. Input ownership

While a window is open the panel owns the mouse: clicks and scroll over it
never reach the game (`WantCaptureMouse()`). Typing in a field works, but
know the platform limit: `InputEvent.Key` is **not cancelable by NeoForge
design**, so game key bindings (WASD…) still see key presses while you type
with no vanilla screen open. Prefer short edits while standing still.
`WantCaptureKeyboard()` reports field focus for your own gating.

## 6. Debugging (every failure names names)

- **Out-of-frame call** — Lua error naming `ImGui.OnFrame` (section 1).
- **Unbalanced Begin/End** — loud warn with the leaked count; the frame
  auto-closes leftovers so one bad panel cannot wedge the overlay:
  `[ImGui] unbalanced Begin/End detected (2 window(s), 0 child region(s) left open)...`
- **Unknown flag/color** — error naming the bad name plus the valid list.
- **Nothing renders** — check the demo wiring order: `Begin` → widgets →
  `End`, all inside one `OnFrame:Connect`. After F3+T resource reload the
  signal persists (engine re-binds the table, same contract as GuiService).

## 7. Tables, tabs, menus, lists, images

```lua
if ImGui:BeginTabBar("Main") then
    if ImGui:BeginTabItem("Kit") then
        if ImGui:BeginTable("KitTable", 2, "RowBg,Borders") then
            ImGui:TableSetupColumn("Icon")
            ImGui:TableSetupColumn("Item")
            ImGui:TableHeadersRow()
            ImGui:TableNextRow()
            if ImGui:TableNextColumn() then
                if ImGui:ItemIcon("luatweaker_test:crystal_sword", 32, 1) then
                    picked = "sword"
                end
            end
            if ImGui:TableNextColumn() then ImGui:Text("Crystal Sword") end
            ImGui:EndTable()
        end
        ImGui:EndTabItem() -- only inside the true branch (same rule as TreePop)
    end
    ImGui:EndTabBar()
end
sel = ImGui:ListBox("Mode", sel, { "Gentle", "Focused", "Overdrive" }) -- 1-based
ImGui:Image("minecraft:textures/gui/icons.png", 16, 16) -- registry texture, V-flipped upright
```

- Tables take 1+ columns and a CSV flag subset (`None,Resizable,Reorderable,
  Hideable,Sortable,RowBg,Borders,SizingFixedFit,SizingStretchProp,ScrollX,
  ScrollY,NoSavedSettings`); unknown names fail fast like window flags.
- `BeginMenuBar` needs the window `MenuBar` flag; `MenuItem(label, shortcut?,
  selected?, enabled?)` returns click; disabled items render greyed.
- `TreePop/EndMenu/EndTabItem` run **only when their Begin returned true**
  (the platform ignores strays with a debug line instead of underflowing).
- `BeginTable/BeginTabBar` always pair with their End, like `Begin/End`.
- `Image` draws any client-registered texture (first use uploads
  synchronously on the render thread — one hitch per unique texture, then
  cached by the vanilla `TextureManager`, resource packs included).
  Orientation follows vanilla mapping (no V flip); visually UNVERIFIED
  in-game — if the live check shows inversion, it is a one-line fix.
- **Static PNG vs animated (`mcmeta`) vs atlas — read this:**
  - Static `.png` anywhere in `assets/` (mod jars, datapacks' resource
    packs, vanilla): works via `Image`.
  - `.mcmeta` frame animation on a standalone GUI/item PNG: does **not**
    animate — vanilla `SimpleTexture` uploads once and ignores the frames
    (verified in source). Flipbook it yourself: N frames as one strip +
    `ImageSub` with Lua-advanced UVs, or swap `Image` ids on a timer.
  - Animated **atlas** sprites (`fire_0`, water, lava, portal): work live.
    The atlas ticks itself in the game loop and re-uploads frames into the
    same UV box, so `GetSpriteUv(atlas, sprite)` once + `ImageSub` with the
    fixed box animates for free. Unknown atlas/sprite returns nil (the
    missing-texture sprite never surfaces — check nil, not pixels).

## 8. Popups, tooltips, disabled scopes, drag & drop

```lua
-- Right-click a row -> context menu (open + body pair by id).
ImGui:OpenPopupOnItemClick("KitMenu")
if ImGui:BeginPopupContextItem("KitMenu") then
    if ImGui:MenuItem("Inspect", "", false, true) then picked = "inspecting..." end
    ImGui:EndPopup() -- only inside the true branch
end
-- Modal dialog (blocks the rest of the UI until closed).
if ImGui:BeginPopupModal("Confirm wipe?", confirmOpen) then
    if ImGui:Button("Wipe") then confirmOpen = false ImGui:CloseCurrentPopup() end
    ImGui:EndPopup()
end
-- Tooltips: one-liner, or a custom box.
if ImGui:IsItemHovered() then ImGui:SetTooltip("hint text") end
-- Grey out a scope.
ImGui:BeginDisabled(flow <= 0)
if ImGui:Button("Pulse!") then ... end
ImGui:EndDisabled() -- always pairs, like Begin/End
-- Drag an item id onto a slot.
if ImGui:BeginDragDropSource() then
    ImGui:SetDragDropPayload("LT_ITEM", "luatweaker_test:crystal_sword")
    ImGui:EndDragDropSource()
end
if ImGui:BeginDragDropTarget() then
    local got = ImGui:AcceptDragDropPayload("LT_ITEM") -- nil when nothing hovers
    if got ~= nil then picked = got end
    ImGui:EndDragDropTarget()
end
```

Rules: `EndPopup/EndMenu/EndTabItem/EndCombo/EndDragDropSource/EndDragDropTarget`
run **only when their Begin returned true** (strays are ignored with a debug
line). `EndDisabled/EndTooltip/EndTable/EndTabBar/End` always pair.
`BeginMainMenuBar` needs its End only on true (fullscreen top bar).

## 9. Plots, text tools, item state, layout width

```lua
ImGui:PlotLines("Flow history", flowHistory) -- number array, keep it short (< few hundred)
ImGui:PlotHistogram("Damage", samples)
ImGui:TextColored("Overheating!", 1.0, 0.2, 0.1)
ImGui:TextWrapped("long paragraph...")
ImGui:LabelText("Owner", "Steve")
ImGui:TextDisabled(" (locked)")
if ImGui:ArrowButton("next", "Right") then page = page + 1 end
if ImGui:TabItemButton("+") then newTab() end
ImGui:PushItemWidth(200) ... ImGui:PopItemWidth() -- or SetNextItemWidth(200) once
if ImGui:BeginCombo("Pick", preview) then
    for _, it in ipairs(items) do if ImGui:Selectable(it, it == sel) then sel = it end end
    ImGui:EndCombo()
end
label = ImGui:InputTextFlags("PIN", label, "Password") -- or CharsDecimal,CharsHexadecimal,NoUndoRedo
ImGui:SetScrollHereY() -- right after an item: scroll it into view
ImGui:SetKeyboardFocusHere() -- before a field: autofocus it
ImGui:SetNextItemOpen(true) -- expand the next TreeNode
if ImGui:IsItemEdited() then dirty = true end
local size = ImGui:GetItemRectSize() -- {w, h} of the last widget
local tw = ImGui:CalcTextSize(text) -- measure before right-aligning
```

## 10. Vietnamese text (read this if your UI has diacritics)

The ImGui default font has **no Vietnamese glyphs** — without setup every
`ă â đ ê ô ơ ư` renders as `?`. The engine fixes this at startup: it probes
`config/luatweaker_font.ttf` first, then OS system fonts (Segoe UI/Arial on
Windows, DejaVu Sans on Linux, Arial on macOS), and builds the atlas with
the Vietnamese glyph ranges. To pin a font, drop any TTF with Vietnamese
coverage at `config/luatweaker_font.ttf` (repo root `config/` of the game
dir). If nothing is found the default font loads and a warn names the
miss — Latin still renders, diacritics show `?` until a font is provided.

## 11. Cost rules and forward path

Per-frame costs were kept off the hot path on purpose:

- Flag/color strings parse once per unique value (bounded 256-entry LRU);
  the static parse functions stay pure so headless tests cover the fail-fast
  contract without natives.
- Slider/drag/input widgets reuse render-thread scratch buffers instead of
  allocating per widget per frame. This is safe because every widget runs
  synchronously inside the render-thread frame (the input hook never calls
  widgets) and values are copied out before each call returns.
- Text buffers still allocate per edited field per frame — fine for config
  panels, not for per-entity inspector lists (aggregate first, then show).

- One frame per render tick even with zero windows: keep `OnFrame`
  handlers lean; hide panels with the `show` flag instead of disconnecting.
- Text buffers allocate per edited field per frame — fine for config
  panels, not for per-entity inspector lists (aggregate first, then show).
- Dedicated servers get the same table with safe echo defaults
  (`GetVersion()` → `"unavailable"`); gate demos on that like every client
  demo.
- **Not in Core (on demand):** Tables, TabBar, MenuBar, ListBox details,
  Drag&Drop payloads, multi-viewport, custom DrawList. Ask with a real panel
  that needs one — the bridge is built to extend (`IPlatformImGui` +
  one override, no consumer changes).

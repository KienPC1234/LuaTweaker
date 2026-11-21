package com.luatweaker.api.client;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;

/**
 * Immediate-mode GUI (Dear ImGui) for advanced Lua-driven interfaces:
 * config screens, machine panels, debug overlays, inspectors.
 *
 * <p>Every widget MUST be called inside {@code ImGui.OnFrame} (fired once per
 * render tick by the engine). Calls outside the frame throw a Lua error naming
 * the signal. For simple HUD drawing (health bars, text) prefer
 * {@code GuiService.OnRenderHUD} — it is cheaper and has no frame rules.</p>
 *
 * <p>Widgets return their new value so change detection stays in Lua:</p>
 * <pre>{@code
 * showPanel = ImGui:Begin("Arcane Panel", showPanel)
 * mana = ImGui:SliderFloat("Mana flow", mana, 0.0, 100.0)
 * ImGui:End()
 * }</pre>
 */
@LuaDoc(description = "Service for advanced immediate-mode GUI (Dear ImGui). Call only inside ImGui.OnFrame.")
public interface IImGuiService {

    @LuaDoc(
        description = "Opens a window. Returns the new open state (false when the user clicked [X]). Every Begin needs a matching End, even when it returns false.",
        params = {"title: string", "open: boolean (optional, default true)", "flags: string (optional CSV: NoTitleBar,NoResize,NoMove,NoScrollbar,NoCollapse,AlwaysAutoResize,NoBackground,MenuBar,NoDecoration)"},
        returnType = LuaDocFragments.Types.BOOLEAN
    )
    boolean begin(String title, @LuaDefault(LuaDocFragments.Defaults.TRUE) boolean open, @LuaDefault("") String flags);

    @LuaDoc(description = "Closes the innermost Begin window. Must pair 1:1 with Begin.", params = {})
    void end();

    @LuaDoc(
        description = "Opens a scrollable child region. Returns false when collapsed (still needs EndChild).",
        params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Geometry.WIDTH_NUMBER, LuaDocFragments.Geometry.HEIGHT_NUMBER},
        returnType = LuaDocFragments.Types.BOOLEAN
    )
    boolean beginChild(String id, double width, double height);

    @LuaDoc(description = "Closes the innermost BeginChild region.", params = {})
    void endChild();

    @LuaDoc(description = "Sets the size of the NEXT Begin window (call before Begin).", params = {LuaDocFragments.Geometry.WIDTH_NUMBER, LuaDocFragments.Geometry.HEIGHT_NUMBER})
    void setNextWindowSize(double width, double height);

    @LuaDoc(description = "Sets the position of the NEXT Begin window (call before Begin).", params = {"x: number", "y: number"})
    void setNextWindowPos(double x, double y);

    @LuaDoc(description = "Places the next widget on the same line.", params = {})
    void sameLine();

    @LuaDoc(description = "Line break.", params = {})
    void newLine();

    @LuaDoc(description = "Vertical spacing.", params = {})
    void spacing();

    @LuaDoc(description = "Horizontal separator line.", params = {})
    void separator();

    @LuaDoc(description = "Indents subsequent widgets.", params = {})
    void indent();

    @LuaDoc(description = "Undoes indent().", params = {})
    void unindent();

    @LuaDoc(description = "Moves the cursor to an X offset inside the window.", params = {"x: number"})
    void setCursorPosX(double x);

    @LuaDoc(description = "Plain text line.", params = {LuaDocFragments.Common.MESSAGE_STRING})
    void text(String message);

    @LuaDoc(description = "Bulleted text line.", params = {LuaDocFragments.Common.MESSAGE_STRING})
    void bulletText(String message);

    @LuaDoc(description = "Clickable button. Returns true on click.", params = {LuaDocFragments.Common.LABEL_STRING}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean button(String label);

    @LuaDoc(description = "Compact button (no extra padding). Returns true on click.", params = {LuaDocFragments.Common.LABEL_STRING}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean smallButton(String label);

    @LuaDoc(description = "Checkbox. Returns the new checked state.", params = {LuaDocFragments.Common.LABEL_STRING, "checked: boolean"}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean checkbox(String label, boolean checked);

    @LuaDoc(description = "Radio button. Returns true when clicked.", params = {LuaDocFragments.Common.LABEL_STRING, "active: boolean"}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean radioButton(String label, boolean active);

    @LuaDoc(description = "Float slider. Returns the new value.", params = {LuaDocFragments.Common.LABEL_STRING, LuaDocFragments.Common.VALUE_NUMBER, LuaDocFragments.Geometry.MIN_NUMBER, LuaDocFragments.Geometry.MAX_NUMBER}, returnType = LuaDocFragments.Types.NUMBER)
    double sliderFloat(String label, double value, double min, double max);

    @LuaDoc(description = "Integer slider. Returns the new value.", params = {LuaDocFragments.Common.LABEL_STRING, LuaDocFragments.Common.VALUE_NUMBER, LuaDocFragments.Geometry.MIN_NUMBER, LuaDocFragments.Geometry.MAX_NUMBER}, returnType = LuaDocFragments.Types.NUMBER)
    int sliderInt(String label, int value, int min, int max);

    @LuaDoc(description = "Drag-to-edit float. Returns the new value.", params = {LuaDocFragments.Common.LABEL_STRING, LuaDocFragments.Common.VALUE_NUMBER, "speed: number", LuaDocFragments.Geometry.MIN_NUMBER, LuaDocFragments.Geometry.MAX_NUMBER}, returnType = LuaDocFragments.Types.NUMBER)
    double dragFloat(String label, double value, double speed, double min, double max);

    @LuaDoc(description = "Drag-to-edit integer. Returns the new value.", params = {LuaDocFragments.Common.LABEL_STRING, LuaDocFragments.Common.VALUE_NUMBER, "speed: number", LuaDocFragments.Geometry.MIN_NUMBER, LuaDocFragments.Geometry.MAX_NUMBER}, returnType = LuaDocFragments.Types.NUMBER)
    int dragInt(String label, int value, double speed, int min, int max);

    @LuaDoc(description = "Single-line text field. Returns the new text (compare with the old one to detect edits).", params = {LuaDocFragments.Common.LABEL_STRING, "current: string"}, returnType = LuaDocFragments.Types.STRING)
    String inputText(String label, String current);

    @LuaDoc(description = "Multi-line text field. Returns the new text.", params = {LuaDocFragments.Common.LABEL_STRING, "current: string"}, returnType = LuaDocFragments.Types.STRING)
    String inputTextMultiline(String label, String current);

    @LuaDoc(description = "Integer field. Returns the new value.", params = {LuaDocFragments.Common.LABEL_STRING, LuaDocFragments.Common.VALUE_NUMBER}, returnType = LuaDocFragments.Types.NUMBER)
    int inputInt(String label, int value);

    @LuaDoc(description = "Float field. Returns the new value.", params = {LuaDocFragments.Common.LABEL_STRING, LuaDocFragments.Common.VALUE_NUMBER}, returnType = LuaDocFragments.Types.NUMBER)
    double inputFloat(String label, double value);

    @LuaDoc(description = "Dropdown. current is 1-based (Lua convention). Returns the new 1-based selection.", params = {LuaDocFragments.Common.LABEL_STRING, "current: number (1-based)", "items: table (array of strings)"}, returnType = LuaDocFragments.Types.NUMBER)
    int combo(String label, int current, String... items);

    @LuaDoc(description = "RGB color picker. Returns {r, g, b} (0-1).", params = {LuaDocFragments.Common.LABEL_STRING, "r: number", "g: number", "b: number"}, returnType = LuaDocFragments.Types.TABLE)
    java.util.List<Double> colorEdit3(String label, double r, double g, double b);

    @LuaDoc(description = "RGBA color picker. Returns {r, g, b, a} (0-1).", params = {LuaDocFragments.Common.LABEL_STRING, "r: number", "g: number", "b: number", "a: number"}, returnType = LuaDocFragments.Types.TABLE)
    java.util.List<Double> colorEdit4(String label, double r, double g, double b, double a);

    @LuaDoc(description = "Progress bar (0-1).", params = {"fraction: number", "label: string (optional)"})
    void progressBar(double fraction, @LuaDefault("") String label);

    @LuaDoc(description = "Collapsible header. Returns true when open (draw the body only then).", params = {LuaDocFragments.Common.LABEL_STRING}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean collapsingHeader(String label);

    @LuaDoc(description = "Tree node. Returns true when open (draw children then call TreePop).", params = {LuaDocFragments.Common.LABEL_STRING}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean treeNode(String label);

    @LuaDoc(description = "Closes the innermost open TreeNode.", params = {})
    void treePop();

    @LuaDoc(description = "Selectable row. Returns true when clicked.", params = {LuaDocFragments.Common.LABEL_STRING, "selected: boolean"}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean selectable(String label, boolean selected);

    @LuaDoc(description = "True when the mouse hovers the last widget. Only valid inside OnFrame.", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean isItemHovered();

    @LuaDoc(description = "True when the last widget was clicked this frame.", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean isItemClicked();

    @LuaDoc(description = "True when the mouse hovers the current window.", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean isWindowHovered();

    @LuaDoc(description = "True when ImGui wants the mouse (a window is hovered). Gate game clicks on this.", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean wantCaptureMouse();

    @LuaDoc(description = "True when ImGui wants the keyboard (a field is focused). Gate game keys on this.", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean wantCaptureKeyboard();

    @LuaDoc(description = "Overrides one theme color (ARGB integer, same convention as GuiService). Popped by PopStyleColor.", params = {"colorName: string (Text,WindowBg,Button,ButtonHovered,ButtonActive,FrameBg,Header,CheckMark,SliderGrab,TitleBg,Separator)", "argbColor: number"})
    void pushStyleColor(String colorName, int argbColor);

    @LuaDoc(description = "Undoes PushStyleColor.", params = {"count: number (optional, default 1)"})
    void popStyleColor(@LuaDefault("1") int count);

    @LuaDoc(
        description = "Clickable item-icon slot (preview only, NOT a real container slot). Draws the item with count overlay; returns true on click. Wire inventory logic yourself via Network/Storage.",
        params = {"itemId: string", "sizePx: number (optional, default 32)", "count: number (optional, default 1)"},
        returnType = LuaDocFragments.Types.BOOLEAN
    )
    boolean itemIcon(String itemId, @LuaDefault("32") int sizePx, @LuaDefault("1") int count);

    @LuaDoc(description = "Opens/closes the built-in widget gallery (great for discovering widgets). Returns the new open state.", params = {"open: boolean"}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean showDemoWindow(boolean open);

    @LuaDoc(description = "Dear ImGui version string behind this build.", params = {}, returnType = LuaDocFragments.Types.STRING)
    String getVersion();

    @LuaDoc(
        description = "Opens a table with N columns. Returns false when collapsed (still needs EndTable).",
        params = {LuaDocFragments.Common.ID_STRING, "columns: number", "flags: string (optional CSV: None,Resizable,Reorderable,Hideable,Sortable,RowBg,Borders,SizingFixedFit,SizingStretchProp,ScrollX,ScrollY,NoSavedSettings)"},
        returnType = LuaDocFragments.Types.BOOLEAN
    )
    boolean beginTable(String id, int columns, @LuaDefault("") String flags);

    @LuaDoc(description = "Closes the innermost open BeginTable. Must pair 1:1.", params = {})
    void endTable();

    @LuaDoc(description = "Starts the next table row. Call TableSetupColumn/TableHeadersRow once before the first row.", params = {})
    void tableNextRow();

    @LuaDoc(description = "Advances to the next column; draw the cell right after. Returns false past the last column.", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean tableNextColumn();

    @LuaDoc(description = "Declares one column (call once per column before the first TableNextRow). width 0 = auto; flags CSV: None,WidthFixed,WidthStretch,NoResize,NoReorder,NoHide,NoSort.", params = {"name: string", "width: number (optional, default 0)", "flags: string (optional)"})
    void tableSetupColumn(String name, @LuaDefault("0") double width, @LuaDefault("") String flags);

    @LuaDoc(description = "Draws the header row from the declared columns.", params = {})
    void tableHeadersRow();

    @LuaDoc(description = "Opens a tab bar. Returns false when collapsed (still needs EndTabBar).", params = {LuaDocFragments.Common.ID_STRING}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean beginTabBar(String id);

    @LuaDoc(description = "Closes the innermost open BeginTabBar.", params = {})
    void endTabBar();

    @LuaDoc(description = "Opens one tab. Returns true when selected (draw the body then call EndTabItem).", params = {LuaDocFragments.Common.LABEL_STRING}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean beginTabItem(String label);

    @LuaDoc(description = "Closes the innermost open tab item.", params = {})
    void endTabItem();

    @LuaDoc(description = "Opens the window menu bar (window must have the MenuBar flag). Returns false when collapsed (still needs EndMenuBar).", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean beginMenuBar();

    @LuaDoc(description = "Closes the innermost open BeginMenuBar.", params = {})
    void endMenuBar();

    @LuaDoc(description = "Opens a dropdown menu. Returns true when open (add MenuItems then call EndMenu).", params = {LuaDocFragments.Common.LABEL_STRING}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean beginMenu(String label);

    @LuaDoc(description = "Closes the innermost open BeginMenu.", params = {})
    void endMenu();

    @LuaDoc(description = "Clickable menu row with optional shortcut hint and checkmark. Returns true on click.", params = {LuaDocFragments.Common.LABEL_STRING, "shortcut: string (optional)", "selected: boolean (optional, default false)", "enabled: boolean (optional, default true)"}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean menuItem(String label, @LuaDefault("") String shortcut, @LuaDefault("false") boolean selected, @LuaDefault(LuaDocFragments.Defaults.TRUE) boolean enabled);

    @LuaDoc(description = "Scrollable list box. current is 1-based (Lua convention). Returns the new 1-based selection.", params = {LuaDocFragments.Common.LABEL_STRING, "current: number (1-based)", "items: table (array of strings)"}, returnType = LuaDocFragments.Types.NUMBER)
    int listBox(String label, int current, String... items);

    @LuaDoc(description = "Draws a texture from the client texture registry (e.g. 'minecraft:textures/gui/icons.png'). Unknown ids draw nothing (one debug line per id).", params = {"textureId: string", LuaDocFragments.Geometry.WIDTH_NUMBER, LuaDocFragments.Geometry.HEIGHT_NUMBER})
    void image(String textureId, double width, double height);

    @LuaDoc(description = "Draws a UV sub-rect of a texture (sprite sheets, atlas sprites). Combine with GetSpriteUv for animated atlas sprites — the atlas ticks itself, so a fixed box animates for free.", params = {"textureId: string", LuaDocFragments.Geometry.WIDTH_NUMBER, LuaDocFragments.Geometry.HEIGHT_NUMBER, "u0: number", "v0: number", "u1: number", "v1: number"})
    void imageSub(String textureId, double width, double height, double u0, double v0, double u1, double v1);

    @LuaDoc(description = "UV box {u0, v0, u1, v1} of one sprite in an atlas, or nil when the atlas/sprite is unknown. Example: GetSpriteUv('minecraft:textures/atlas/blocks.png', 'minecraft:block/fire_0').", params = {"atlasId: string", "spriteId: string"}, returnType = "table|nil")
    java.util.List<Double> getSpriteUv(String atlasId, String spriteId);

    @LuaDoc(description = "Opens a popup by id (shows on the matching BeginPopup). Call from a click handler, then draw in BeginPopup.", params = {LuaDocFragments.Common.ID_STRING})
    void openPopup(String id);

    @LuaDoc(description = "Opens a popup body. Returns true when open (draw then EndPopup).", params = {LuaDocFragments.Common.ID_STRING}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean beginPopup(String id);

    @LuaDoc(description = "Closes the innermost open BeginPopup/BeginPopupModal.", params = {})
    void endPopup();

    @LuaDoc(description = "Opens a modal dialog (blocks the rest of the UI). Returns the new open state. Pair with EndPopup.", params = {"title: string", "open: boolean (optional, default true)"}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean beginPopupModal(String title, @LuaDefault(LuaDocFragments.Defaults.TRUE) boolean open);

    @LuaDoc(description = "Closes the current popup from inside its body (e.g. after an OK button).", params = {})
    void closeCurrentPopup();

    @LuaDoc(description = "Right-click context menu for the last item (or current window when id is ''). Returns true when open (add MenuItems then EndPopup).", params = {"id: string (optional)"}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean beginPopupContextItem(@LuaDefault("") String id);

    @LuaDoc(description = "Opens the last item's context popup on right-click. Pair with BeginPopupContextItem.", params = {"id: string (optional)"})
    void openPopupOnItemClick(@LuaDefault("") String id);

    @LuaDoc(description = "Opens a tooltip box (use inside 'if IsItemHovered() then ... end').", params = {})
    void beginTooltip();

    @LuaDoc(description = "Closes the innermost BeginTooltip.", params = {})
    void endTooltip();

    @LuaDoc(description = "One-line hover tooltip for the last item (no Begin/End needed).", params = {"text: string"})
    void setTooltip(String text);

    @LuaDoc(description = "Greys out everything until EndDisabled (disabled=true) or a no-op scope (false). Must pair 1:1.", params = {"disabled: boolean (optional, default true)"})
    void beginDisabled(@LuaDefault(LuaDocFragments.Defaults.TRUE) boolean disabled);

    @LuaDoc(description = "Closes the innermost BeginDisabled.", params = {})
    void endDisabled();

    @LuaDoc(description = "Scrolls the parent (child/table/list) so the last item is visible. Call right after the item.", params = {})
    void setScrollHereY();

    @LuaDoc(description = "Moves keyboard focus to the next widget (call before it, e.g. to autofocus a field).", params = {})
    void setKeyboardFocusHere();

    @LuaDoc(description = "Forces the next TreeNode/CollapsingHeader open (true) or closed (false).", params = {"open: boolean"})
    void setNextItemOpen(boolean open);

    @LuaDoc(description = "Colored text line (RGB 0-1, opaque).", params = {"text: string", "r: number", "g: number", "b: number"})
    void textColored(String text, double r, double g, double b);

    @LuaDoc(description = "Word-wrapped text line.", params = {LuaDocFragments.Common.MESSAGE_STRING})
    void textWrapped(String message);

    @LuaDoc(description = "Greyed-out text line.", params = {LuaDocFragments.Common.MESSAGE_STRING})
    void textDisabled(String message);

    @LuaDoc(description = "Two-column label: value row.", params = {LuaDocFragments.Common.LABEL_STRING, "value: string"})
    void labelText(String label, String value);

    @LuaDoc(description = "Directional arrow button. Returns true on click. direction: Left,Right,Up,Down.", params = {LuaDocFragments.Common.ID_STRING, "direction: string"}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean arrowButton(String id, String direction);

    @LuaDoc(description = "Tab-bar trailing button (e.g. a '+' new-tab button). Returns true on click.", params = {LuaDocFragments.Common.LABEL_STRING}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean tabItemButton(String label);

    @LuaDoc(description = "Fullscreen top menu bar. Call EndMainMenuBar ONLY when this returns true.", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean beginMainMenuBar();

    @LuaDoc(description = "Closes the innermost BeginMainMenuBar.", params = {})
    void endMainMenuBar();

    @LuaDoc(description = "Fixes the next widget's width in pixels.", params = {LuaDocFragments.Geometry.WIDTH_NUMBER})
    void pushItemWidth(double width);

    @LuaDoc(description = "Undoes PushItemWidth.", params = {})
    void popItemWidth();

    @LuaDoc(description = "Fixes one widget's width (call before it).", params = {LuaDocFragments.Geometry.WIDTH_NUMBER})
    void setNextItemWidth(double width);

    @LuaDoc(description = "Custom dropdown body (rows with icons/checkmarks). Returns true when open (draw rows then EndCombo).", params = {LuaDocFragments.Common.LABEL_STRING, "preview: string"}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean beginCombo(String label, String preview);

    @LuaDoc(description = "Closes the innermost BeginCombo.", params = {})
    void endCombo();

    @LuaDoc(description = "Line graph from a number array (e.g. last N tick times).", params = {LuaDocFragments.Common.LABEL_STRING, "values: table (array of numbers)"})
    void plotLines(String label, float... values);

    @LuaDoc(description = "Histogram from a number array.", params = {LuaDocFragments.Common.LABEL_STRING, "values: table (array of numbers)"})
    void plotHistogram(String label, float... values);

    @LuaDoc(description = "Single-line text field with flags CSV: None,CharsDecimal,CharsHexadecimal,Password,NoUndoRedo. Returns the new text.", params = {LuaDocFragments.Common.LABEL_STRING, "current: string", "flags: string (optional)"}, returnType = LuaDocFragments.Types.STRING)
    String inputTextFlags(String label, String current, @LuaDefault("") String flags);

    @LuaDoc(description = "True while the last widget is held down (dragging a slider, holding a button).", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean isItemActive();

    @LuaDoc(description = "True while the last widget has keyboard focus.", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean isItemFocused();

    @LuaDoc(description = "True when the last widget's rectangle is visible (not clipped).", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean isItemVisible();

    @LuaDoc(description = "True when the last widget changed its value this frame.", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean isItemEdited();

    @LuaDoc(description = "Pixel size the text would take: {width, height}. Use with Image/Sub planning and right-aligned rows.", params = {"text: string"}, returnType = LuaDocFragments.Types.TABLE)
    java.util.List<Double> calcTextSize(String text);

    @LuaDoc(description = "Pixel size of the last widget: {width, height}.", params = {}, returnType = LuaDocFragments.Types.TABLE)
    java.util.List<Double> getItemRectSize();

    @LuaDoc(description = "Starts a drag source on the last item. Returns true while dragging (set the payload then EndDragDropSource).", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean beginDragDropSource();

    @LuaDoc(description = "Attaches a string payload (e.g. an item id) to the active drag. Returns true when accepted into the drag.", params = {"type: string (payload family, e.g. 'LT_ITEM')", "data: string"}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean setDragDropPayload(String type, String data);

    @LuaDoc(description = "Closes the innermost BeginDragDropSource.", params = {})
    void endDragDropSource();

    @LuaDoc(description = "Starts a drop target at the cursor. Returns true while a matching payload hovers (accept then EndDragDropTarget).", params = {}, returnType = LuaDocFragments.Types.BOOLEAN)
    boolean beginDragDropTarget();

    @LuaDoc(description = "Reads the hovered payload of a family, or nil when none. Call inside BeginDragDropTarget.", params = {"type: string"}, returnType = "string|nil")
    String acceptDragDropPayload(String type);

    @LuaDoc(description = "Closes the innermost BeginDragDropTarget.", params = {})
    void endDragDropTarget();
}

package com.luatweaker.api.pal;

import org.jetbrains.annotations.Nullable;

/**
 * Immediate-mode GUI bridge (Dear ImGui) that only exists on a client process.
 *
 * <p>Extended by {@link IPlatformClient}; the only implementation lives in
 * {@code neoforge-platform} (the sole module allowed to touch {@code imgui.*}).
 * Every method must be safe to call from Lua on the render thread during the
 * {@code ImGui.OnFrame} signal; called anywhere else it throws
 * {@link IllegalStateException} with a message naming the signal.</p>
 *
 * <p>Lua-facing signatures stay Lua-idiomatic on purpose: widgets return their
 * new value ({@code checked = ImGui:Checkbox("x", checked)}) so scripts detect
 * changes by comparison instead of juggling out-param wrapper objects.</p>
 */
public interface IPlatformImGui {

    /** True while the engine-owned ImGui frame is open (inside OnFrame). */
    default boolean imguiIsFrameOpen() { return false; }

    /**
     * Opens the engine-owned frame. Called once per render tick by the render
     * listener; never call from Lua.
     */
    default void imguiBeginFrame() {}

    /** Closes the engine-owned frame, auto-closing leaked windows. */
    default void imguiEndFrame() {}

    /**
     * @param windowFlagsCsv comma-separated Dear ImGui window flags
     *        (e.g. "NoResize,NoMove"); blank for none. Unknown names fail fast.
     * @return the new open state (false after the user clicked [X])
     */
    default boolean imguiBegin(String title, boolean open, String windowFlagsCsv) { return open; }

    default boolean imguiBeginTable(String id, int columns, String flagsCsv) { return true; }

    default void imguiEndTable() {}

    default void imguiTableNextRow() {}

    default boolean imguiTableNextColumn() { return true; }

    default void imguiTableSetupColumn(String name, float width, String flagsCsv) {}

    /**
     * Name-only overload kept for existing call sites. Platforms that already
     * implement the name-only form are dispatched to it; others fall through to
     * the width/flags variant above.
     */
    default void imguiTableSetupColumn(String name) {
        imguiTableSetupColumn(name, 0.0f, "");
    }

    default void imguiTableHeadersRow() {}

    default boolean imguiBeginTabBar(String id) { return true; }

    default void imguiEndTabBar() {}

    default boolean imguiBeginTabItem(String label) { return true; }

    default void imguiEndTabItem() {}

    default boolean imguiBeginMenuBar() { return false; }

    default void imguiEndMenuBar() {}

    default boolean imguiBeginMenu(String label) { return false; }

    default void imguiEndMenu() {}

    default boolean imguiMenuItem(String label, String shortcut, boolean selected, boolean enabled) { return false; }

    /**
     * @param currentOneBased selected index, 1-based (Lua convention); clamped
     * @return the new 1-based selection
     */
    default int imguiListBox(String label, int currentOneBased, String... items) { return currentOneBased; }

    /** Draws a registry texture rect. Unknown ids are skipped with one debug line. */
    default void imguiImage(String textureId, float width, float height) {}

    /**
     * Draws a UV sub-rect of a registry texture (sprite sheets, atlas
     * sprites). UVs are raw GL-space values exactly as vanilla sampling uses
     * them, so atlas regions render the same orientation the game gives them.
     */
    default void imguiImageSub(String textureId, float width, float height,
                               float u0, float v0, float u1, float v1) {}

    /**
     * UV box of one sprite inside an atlas (e.g. atlas
     * "minecraft:textures/atlas/blocks.png", sprite "minecraft:block/fire_0").
     * Atlases tick themselves, so an animated sprite drawn from a fixed box
     * animates for free. Null when the atlas or sprite is unknown.
     */
    default @Nullable java.util.List<Double> imguiSpriteUv(String atlasId, String spriteId) { return null; }

    default void imguiOpenPopup(String id) {}

    default boolean imguiBeginPopup(String id) { return false; }

    default void imguiEndPopup() {}

    default boolean imguiBeginPopupModal(String title, boolean open) { return open; }

    default void imguiCloseCurrentPopup() {}

    default boolean imguiBeginPopupContextItem(String id) { return false; }

    default void imguiOpenPopupOnItemClick(String id) {}

    default void imguiBeginTooltip() {}

    default void imguiEndTooltip() {}

    default void imguiSetTooltip(String text) {}

    default void imguiBeginDisabled(boolean disabled) {}

    default void imguiEndDisabled() {}

    default void imguiSetScrollHereY() {}

    default void imguiSetKeyboardFocusHere() {}

    default void imguiSetNextItemOpen(boolean open) {}

    default void imguiTextColored(float r, float g, float b, String message) {}

    default void imguiTextWrapped(String message) {}

    default void imguiTextDisabled(String message) {}

    default void imguiLabelText(String label, String value) {}

    default boolean imguiArrowButton(String id, String direction) { return false; }

    default boolean imguiTabItemButton(String label) { return false; }

    default boolean imguiBeginMainMenuBar() { return false; }

    default void imguiEndMainMenuBar() {}

    default void imguiPushItemWidth(float width) {}

    default void imguiPopItemWidth() {}

    default void imguiSetNextItemWidth(float width) {}

    default boolean imguiBeginCombo(String label, String preview) { return false; }

    default void imguiEndCombo() {}

    default void imguiPlotLines(String label, float... values) {}

    default void imguiPlotHistogram(String label, float... values) {}

    default String imguiInputTextFlags(String label, String current, String flagsCsv) {
        return current == null ? "" : current;
    }

    default boolean imguiIsItemActive() { return false; }

    default boolean imguiIsItemFocused() { return false; }

    default boolean imguiIsItemVisible() { return false; }

    default boolean imguiIsItemEdited() { return false; }

    default java.util.List<Double> imguiCalcTextSize(String text) { return java.util.List.of(0.0, 0.0); }

    default java.util.List<Double> imguiGetItemRectSize() { return java.util.List.of(0.0, 0.0); }

    default boolean imguiBeginDragDropSource() { return false; }

    default boolean imguiSetDragDropPayload(String type, String data) { return false; }

    default void imguiEndDragDropSource() {}

    default boolean imguiBeginDragDropTarget() { return false; }

    default String imguiAcceptDragDropPayload(String type) { return null; }

    default void imguiEndDragDropTarget() {}

    default void imguiEnd() {}

    default boolean imguiBeginChild(String id, float width, float height) { return true; }

    default void imguiEndChild() {}

    default void imguiSetNextWindowSize(float width, float height) {}

    default void imguiSetNextWindowPos(float x, float y) {}

    default void imguiSameLine() {}

    default void imguiNewLine() {}

    default void imguiSpacing() {}

    default void imguiSeparator() {}

    default void imguiIndent() {}

    default void imguiUnindent() {}

    default void imguiSetCursorPosX(float x) {}

    default void imguiText(String message) {}

    default void imguiBulletText(String message) {}

    default boolean imguiButton(String label) { return false; }

    default boolean imguiSmallButton(String label) { return false; }

    default boolean imguiCheckbox(String label, boolean checked) { return checked; }

    default boolean imguiRadioButton(String label, boolean active) { return false; }

    default double imguiSliderFloat(String label, double value, double min, double max) { return value; }

    default int imguiSliderInt(String label, int value, int min, int max) { return value; }

    default double imguiDragFloat(String label, double value, double speed, double min, double max) { return value; }

    default int imguiDragInt(String label, int value, double speed, int min, int max) { return value; }

    default String imguiInputText(String label, String current, boolean multiline) {
        return current == null ? "" : current;
    }

    default int imguiInputInt(String label, int value) { return value; }

    default double imguiInputFloat(String label, double value) { return value; }

    /**
     * @param currentOneBased selected index, 1-based (Lua convention); clamped
     * @return the new 1-based selection
     */
    default int imguiCombo(String label, int currentOneBased, String... items) { return currentOneBased; }

    default java.util.List<Double> imguiColorEdit(String label, double r, double g, double b, double a) {
        return java.util.List.of(r, g, b, a);
    }

    default void imguiProgressBar(double fraction, String label) {}

    default boolean imguiCollapsingHeader(String label) { return false; }

    default boolean imguiTreeNode(String label) { return false; }

    default void imguiTreePop() {}

    default boolean imguiSelectable(String label, boolean selected) { return false; }

    default boolean imguiIsItemHovered() { return false; }

    default boolean imguiIsItemClicked() { return false; }

    default boolean imguiIsWindowHovered() { return false; }

    default boolean imguiWantCaptureMouse() { return false; }

    default boolean imguiWantCaptureKeyboard() { return false; }

    /**
     * Pushes one theme color override (ARGB integer, same convention as
     * GuiService). Unknown names fail fast; the platform maps names with the
     * real ImGuiCol constants.
     */
    default void imguiPushStyleColor(String colorName, int argb) {}

    default void imguiPopStyleColor(int count) {}

    /**
     * Clickable item-icon slot (rendered with the vanilla item renderer after
     * the ImGui pass). This is a preview/click surface only — real container
     * sync stays in Lua via Network/Storage. Unknown ids draw nothing.
     *
     * @return true on click
     */
    default boolean imguiItemIcon(String itemId, int sizePx, int count) { return false; }

    default boolean imguiShowDemoWindow(boolean open) { return open; }

    default String imguiGetVersion() { return "unavailable"; }

    default void imguiSetScreen(boolean open) {}

    default boolean imguiIsScreenOpen() { return false; }

    default boolean imguiToggleScreen() { return false; }

    default void imguiProgressBar(double fraction, String label, double width, double height) {
        imguiProgressBar(fraction, label);
    }

    default boolean imguiInvisibleButton(String id, double width, double height) { return false; }

    default double imguiGetMousePosX() { return 0.0; }

    default double imguiGetMousePosY() { return 0.0; }

    default double imguiGetCursorPosX() { return 0.0; }

    default double imguiGetCursorPosY() { return 0.0; }

    default void imguiSetCursorPos(double x, double y) {}

    default double imguiGetWindowPosX() { return 0.0; }

    default double imguiGetWindowPosY() { return 0.0; }

    default double imguiGetWindowWidth() { return 0.0; }

    default double imguiGetWindowHeight() { return 0.0; }
}

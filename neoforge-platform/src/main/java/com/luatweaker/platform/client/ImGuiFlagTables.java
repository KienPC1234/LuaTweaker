package com.luatweaker.platform.client;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Name-to-constant tables for the ImGui string DSL exposed to Lua.
 *
 * <p>Each Lua-visible name maps to the real Dear ImGui constant, so a typo fails loudly in
 * {@link NeoForgeClientPlatform} instead of silently producing {@code 0}. Keeping the tables
 * here leaves the parser on the platform class branch-free.</p>
 */
final class ImGuiFlagTables {

    private static final String NONE = "None";

    private ImGuiFlagTables() {
    }

    static @NotNull Map<String, Integer> windowFlags() {
        return Map.ofEntries(
                Map.entry(NONE, imgui.flag.ImGuiWindowFlags.None),
                Map.entry("NoTitleBar", imgui.flag.ImGuiWindowFlags.NoTitleBar),
                Map.entry("NoResize", imgui.flag.ImGuiWindowFlags.NoResize),
                Map.entry("NoMove", imgui.flag.ImGuiWindowFlags.NoMove),
                Map.entry("NoScrollbar", imgui.flag.ImGuiWindowFlags.NoScrollbar),
                Map.entry("NoCollapse", imgui.flag.ImGuiWindowFlags.NoCollapse),
                Map.entry("AlwaysAutoResize", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize),
                Map.entry("NoBackground", imgui.flag.ImGuiWindowFlags.NoBackground),
                Map.entry("NoMouseInputs", imgui.flag.ImGuiWindowFlags.NoMouseInputs),
                Map.entry("MenuBar", imgui.flag.ImGuiWindowFlags.MenuBar),
                Map.entry("HorizontalScrollbar", imgui.flag.ImGuiWindowFlags.HorizontalScrollbar),
                Map.entry("NoFocusOnAppearing", imgui.flag.ImGuiWindowFlags.NoFocusOnAppearing),
                Map.entry("NoBringToFrontOnFocus", imgui.flag.ImGuiWindowFlags.NoBringToFrontOnFocus),
                Map.entry("AlwaysVerticalScrollbar", imgui.flag.ImGuiWindowFlags.AlwaysVerticalScrollbar),
                Map.entry("AlwaysHorizontalScrollbar", imgui.flag.ImGuiWindowFlags.AlwaysHorizontalScrollbar),
                Map.entry("NoNavInputs", imgui.flag.ImGuiWindowFlags.NoNavInputs),
                Map.entry("NoDecoration", imgui.flag.ImGuiWindowFlags.NoDecoration));
    }

    static @NotNull Map<String, Integer> tableFlags() {
        return Map.ofEntries(
                Map.entry(NONE, imgui.flag.ImGuiTableFlags.None),
                Map.entry("Resizable", imgui.flag.ImGuiTableFlags.Resizable),
                Map.entry("Reorderable", imgui.flag.ImGuiTableFlags.Reorderable),
                Map.entry("Hideable", imgui.flag.ImGuiTableFlags.Hideable),
                Map.entry("Sortable", imgui.flag.ImGuiTableFlags.Sortable),
                Map.entry("NoSavedSettings", imgui.flag.ImGuiTableFlags.NoSavedSettings),
                Map.entry("RowBg", imgui.flag.ImGuiTableFlags.RowBg),
                Map.entry("Borders", imgui.flag.ImGuiTableFlags.Borders),
                Map.entry("SizingFixedFit", imgui.flag.ImGuiTableFlags.SizingFixedFit),
                Map.entry("SizingStretchProp", imgui.flag.ImGuiTableFlags.SizingStretchProp),
                Map.entry("ScrollX", imgui.flag.ImGuiTableFlags.ScrollX),
                Map.entry("ScrollY", imgui.flag.ImGuiTableFlags.ScrollY));
    }

    static @NotNull Map<String, Integer> tableColumnFlags() {
        return Map.ofEntries(
                Map.entry(NONE, imgui.flag.ImGuiTableColumnFlags.None),
                Map.entry("WidthFixed", imgui.flag.ImGuiTableColumnFlags.WidthFixed),
                Map.entry("WidthStretch", imgui.flag.ImGuiTableColumnFlags.WidthStretch),
                Map.entry("NoResize", imgui.flag.ImGuiTableColumnFlags.NoResize),
                Map.entry("NoReorder", imgui.flag.ImGuiTableColumnFlags.NoReorder),
                Map.entry("NoHide", imgui.flag.ImGuiTableColumnFlags.NoHide),
                Map.entry("NoSort", imgui.flag.ImGuiTableColumnFlags.NoSort),
                Map.entry("NoHeaderLabel", imgui.flag.ImGuiTableColumnFlags.NoHeaderLabel));
    }

    static @NotNull Map<String, Integer> inputFlags() {
        return Map.ofEntries(
                Map.entry(NONE, imgui.flag.ImGuiInputTextFlags.None),
                Map.entry("CharsDecimal", imgui.flag.ImGuiInputTextFlags.CharsDecimal),
                Map.entry("CharsHexadecimal", imgui.flag.ImGuiInputTextFlags.CharsHexadecimal),
                Map.entry("Password", imgui.flag.ImGuiInputTextFlags.Password),
                Map.entry("NoUndoRedo", imgui.flag.ImGuiInputTextFlags.NoUndoRedo));
    }

    static @NotNull Map<String, Integer> colors() {
        return Map.ofEntries(
                Map.entry("Text", imgui.flag.ImGuiCol.Text),
                Map.entry("TextDisabled", imgui.flag.ImGuiCol.TextDisabled),
                Map.entry("WindowBg", imgui.flag.ImGuiCol.WindowBg),
                Map.entry("Button", imgui.flag.ImGuiCol.Button),
                Map.entry("ButtonHovered", imgui.flag.ImGuiCol.ButtonHovered),
                Map.entry("ButtonActive", imgui.flag.ImGuiCol.ButtonActive),
                Map.entry("FrameBg", imgui.flag.ImGuiCol.FrameBg),
                Map.entry("FrameBgHovered", imgui.flag.ImGuiCol.FrameBgHovered),
                Map.entry("FrameBgActive", imgui.flag.ImGuiCol.FrameBgActive),
                Map.entry("Header", imgui.flag.ImGuiCol.Header),
                Map.entry("HeaderHovered", imgui.flag.ImGuiCol.HeaderHovered),
                Map.entry("HeaderActive", imgui.flag.ImGuiCol.HeaderActive),
                Map.entry("CheckMark", imgui.flag.ImGuiCol.CheckMark),
                Map.entry("SliderGrab", imgui.flag.ImGuiCol.SliderGrab),
                Map.entry("SliderGrabActive", imgui.flag.ImGuiCol.SliderGrabActive),
                Map.entry("TitleBg", imgui.flag.ImGuiCol.TitleBg),
                Map.entry("TitleBgActive", imgui.flag.ImGuiCol.TitleBgActive),
                Map.entry("Separator", imgui.flag.ImGuiCol.Separator),
                Map.entry("Border", imgui.flag.ImGuiCol.Border));
    }
}

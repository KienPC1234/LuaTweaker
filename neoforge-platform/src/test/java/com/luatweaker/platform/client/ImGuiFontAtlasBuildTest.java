package com.luatweaker.platform.client;

import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.ImGuiIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The loaded (or fallback) font must build into a usable ImGui atlas. */
class ImGuiFontAtlasBuildTest {

    @Test
    void vietnameseRangesExist() {
        ImGui.createContext();
        try {
            short[] ranges = ImGui.getIO().getFonts().getGlyphRangesVietnamese();

            assertNotNull(ranges, "the Vietnamese glyph ranges must exist for Lua UI text");
        } finally {
            ImGui.destroyContext();
        }
    }

    @Test
    void vietnameseRangesAreNonTrivial() {
        ImGui.createContext();
        try {
            short[] ranges = ImGui.getIO().getFonts().getGlyphRangesVietnamese();

            assertTrue(ranges.length > 4,
                    "the Vietnamese glyph ranges must cover more than a stub pair");
        } finally {
            ImGui.destroyContext();
        }
    }

    @Test
    void missingSystemFontFallsBackToTheDefaultFont() {
        byte[] bytes = ImGuiFontLoader.loadFontBytes();
        assumeTrue(bytes == null,
                "system font present; the no-font fallback path is not exercised here");
        // No system font on this machine: the bridge must still fall back
        // to the default font instead of shipping an empty atlas.
        ImGui.createContext();
        try {
            ImGuiIO io = ImGui.getIO();
            io.getFonts().addFontDefault();
            io.getFonts().build();

            assertTrue(io.getFonts().isBuilt(),
                    "the fallback default font must still build a usable atlas");
        } finally {
            ImGui.destroyContext();
        }
    }

    @Test
    void loadedFontBuildsAnAtlas() {
        byte[] bytes = ImGuiFontLoader.loadFontBytes();
        assumeTrue(bytes != null,
                "no system font on this machine; the load path is not exercised here");
        ImGui.createContext();
        try {
            ImGuiIO io = ImGui.getIO();
            ImFontAtlas fonts = io.getFonts();
            fonts.addFontFromMemoryTTF(bytes, ImGuiFontLoader.FONT_SIZE_PX, new imgui.ImFontConfig(),
                    fonts.getGlyphRangesVietnamese());

            assertTrue(fonts.build(),
                    "the loaded system font must build into the atlas");
        } finally {
            ImGui.destroyContext();
        }
    }

    @Test
    void builtAtlasReportsBuilt() {
        byte[] bytes = ImGuiFontLoader.loadFontBytes();
        assumeTrue(bytes != null,
                "no system font on this machine; the load path is not exercised here");
        ImGui.createContext();
        try {
            ImGuiIO io = ImGui.getIO();
            ImFontAtlas fonts = io.getFonts();
            fonts.addFontFromMemoryTTF(bytes, ImGuiFontLoader.FONT_SIZE_PX, new imgui.ImFontConfig(),
                    fonts.getGlyphRangesVietnamese());
            fonts.build();

            assertTrue(fonts.isBuilt(),
                    "a built atlas must report itself built before rendering");
        } finally {
            ImGui.destroyContext();
        }
    }
}

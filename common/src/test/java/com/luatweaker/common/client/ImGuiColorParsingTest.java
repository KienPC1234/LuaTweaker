package com.luatweaker.common.client;

import imgui.flag.ImGuiCol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Color-name parsing maps names to the real ImGui color constants. */
class ImGuiColorParsingTest {

    @Test
    void textMapsToRealConstant() {
        assertEquals(ImGuiCol.Text, MinecraftClientPlatform.parseImGuiColor("Text"),
                "Text must map to the real ImGui color constant");
    }

    @Test
    void buttonHoveredMapsToRealConstant() {
        assertEquals(ImGuiCol.ButtonHovered, MinecraftClientPlatform.parseImGuiColor("ButtonHovered"),
                "ButtonHovered must map to the real ImGui color constant");
    }

    @Test
    void windowBgMapsToRealConstant() {
        assertEquals(ImGuiCol.WindowBg, MinecraftClientPlatform.parseImGuiColor("WindowBg"),
                "WindowBg must map to the real ImGui color constant");
    }

    @Test
    void unknownColorThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> MinecraftClientPlatform.parseImGuiColor("Buton"),
                "an unknown color name must fail fast instead of silently mapping to zero");
    }

    @Test
    void nullColorThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> MinecraftClientPlatform.parseImGuiColor(null),
                "a null color name must fail fast instead of silently mapping to zero");
    }
}

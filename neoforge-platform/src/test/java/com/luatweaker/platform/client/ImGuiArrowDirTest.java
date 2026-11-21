package com.luatweaker.platform.client;

import imgui.flag.ImGuiDir;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Arrow-direction parsing maps names to the real ImGui direction constants. */
class ImGuiArrowDirTest {

    @Test
    void rightMapsToRealConstant() {
        assertEquals(ImGuiDir.Right, NeoForgeClientPlatform.parseImGuiArrowDir("Right"),
                "Right must map to the real ImGui direction constant");
    }

    @Test
    void unknownDirectionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> NeoForgeClientPlatform.parseImGuiArrowDir("Sideways"),
                "an unknown arrow direction must fail fast instead of silently mapping to zero");
    }
}

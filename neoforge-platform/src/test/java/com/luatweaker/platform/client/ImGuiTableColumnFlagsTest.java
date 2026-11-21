package com.luatweaker.platform.client;

import imgui.flag.ImGuiTableColumnFlags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Table-column-flag CSV parsing maps names to the real ImGui column constants. */
class ImGuiTableColumnFlagsTest {

    @Test
    void emptyParsesToZero() {
        assertEquals(0, NeoForgeClientPlatform.parseImGuiTableColumnFlags(""),
                "an empty flag list must mean no flags");
    }

    @Test
    void widthFixedMapsToRealConstant() {
        assertEquals(ImGuiTableColumnFlags.WidthFixed,
                NeoForgeClientPlatform.parseImGuiTableColumnFlags("WidthFixed"),
                "WidthFixed must map to the real ImGui column constant");
    }

    @Test
    void widthStretchAndNoResizeCombine() {
        assertEquals(ImGuiTableColumnFlags.WidthStretch | ImGuiTableColumnFlags.NoResize,
                NeoForgeClientPlatform.parseImGuiTableColumnFlags("WidthStretch,NoResize"),
                "comma-separated column flags must combine with bitwise OR");
    }

    @Test
    void unknownColumnFlagThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> NeoForgeClientPlatform.parseImGuiTableColumnFlags("Wide"),
                "an unknown column flag must fail fast instead of silently mapping to zero");
    }
}

package com.luatweaker.platform.client;

import imgui.flag.ImGuiTableFlags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Table-flag CSV parsing maps names to the real ImGui table constants. */
class ImGuiTableFlagsTest {

    @Test
    void nullParsesToZero() {
        assertEquals(0, NeoForgeClientPlatform.parseImGuiTableFlags(null),
                "a missing flag list must mean no flags");
    }

    @Test
    void emptyParsesToZero() {
        assertEquals(0, NeoForgeClientPlatform.parseImGuiTableFlags(""),
                "an empty flag list must mean no flags");
    }

    @Test
    void rowBgAndBordersCombine() {
        assertEquals(ImGuiTableFlags.RowBg | ImGuiTableFlags.Borders,
                NeoForgeClientPlatform.parseImGuiTableFlags("RowBg,Borders"),
                "comma-separated table flags must combine with bitwise OR");
    }

    @Test
    void resizableAndScrollYCombine() {
        assertEquals(ImGuiTableFlags.Resizable | ImGuiTableFlags.ScrollY,
                NeoForgeClientPlatform.parseImGuiTableFlags("Resizable,ScrollY"),
                "Resizable and ScrollY must map to the real ImGui table constants");
    }

    @Test
    void unknownTableFlagThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> NeoForgeClientPlatform.parseImGuiTableFlags("Borderz"),
                "an unknown table flag must fail fast instead of silently mapping to zero");
    }
}

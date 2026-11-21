package com.luatweaker.platform.client;

import imgui.flag.ImGuiWindowFlags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Window-flag CSV parsing maps names to the real ImGui window constants. */
class ImGuiWindowFlagsTest {

    @Test
    void nullParsesToZero() {
        assertEquals(0, NeoForgeClientPlatform.parseImGuiWindowFlags(null),
                "a missing flag list must mean no flags");
    }

    @Test
    void emptyParsesToZero() {
        assertEquals(0, NeoForgeClientPlatform.parseImGuiWindowFlags(""),
                "an empty flag list must mean no flags");
    }

    @Test
    void blankParsesToZero() {
        assertEquals(0, NeoForgeClientPlatform.parseImGuiWindowFlags("  "),
                "a whitespace-only flag list must mean no flags");
    }

    @Test
    void noneMapsToRealConstant() {
        assertEquals(ImGuiWindowFlags.None, NeoForgeClientPlatform.parseImGuiWindowFlags("None"),
                "None must map to the real ImGui None constant");
    }

    @Test
    void combinedFlagsOrTogether() {
        assertEquals(ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove,
                NeoForgeClientPlatform.parseImGuiWindowFlags("NoResize,NoMove"),
                "comma-separated flags must combine with bitwise OR");
    }

    @Test
    void noDecorationParses() {
        assertEquals(ImGuiWindowFlags.NoDecoration,
                NeoForgeClientPlatform.parseImGuiWindowFlags("NoDecoration"),
                "NoDecoration must map to the real ImGui constant");
    }

    @Test
    void spacedListWithAliasesParses() {
        assertEquals(ImGuiWindowFlags.NoDecoration,
                NeoForgeClientPlatform.parseImGuiWindowFlags(
                        " NoTitleBar , NoResize,NoScrollbar ,NoCollapse "),
                "surrounding spaces and alias names must still resolve to NoDecoration");
    }

    @Test
    void unknownFlagThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> NeoForgeClientPlatform.parseImGuiWindowFlags("NoReszie"),
                "an unknown window flag must fail fast instead of silently mapping to zero");
    }
}

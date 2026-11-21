package com.luatweaker.platform.client;

import imgui.flag.ImGuiInputTextFlags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Input-text-flag CSV parsing maps names to the real ImGui input constants. */
class ImGuiInputFlagsTest {

    @Test
    void passwordMapsToRealConstant() {
        assertEquals(ImGuiInputTextFlags.Password,
                NeoForgeClientPlatform.parseImGuiInputFlags("Password"),
                "Password must map to the real ImGui input constant");
    }

    @Test
    void decimalAndHexadecimalCombine() {
        assertEquals(ImGuiInputTextFlags.CharsDecimal | ImGuiInputTextFlags.CharsHexadecimal,
                NeoForgeClientPlatform.parseImGuiInputFlags("CharsDecimal,CharsHexadecimal"),
                "comma-separated input flags must combine with bitwise OR");
    }

    @Test
    void unknownInputFlagThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> NeoForgeClientPlatform.parseImGuiInputFlags("Passwrod"),
                "an unknown input flag must fail fast instead of silently mapping to zero");
    }
}

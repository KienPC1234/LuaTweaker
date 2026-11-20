package com.luatweaker.client;

import com.luatweaker.api.client.KeyNames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Key-name table: every pinned value mirrors {@code org.lwjgl.glfw.GLFW} in
 * LWJGL 3.3.3 (letters/digits are contiguous ASCII, generated in code).
 */
public class KeyNamesTest {

    @Test
    void lettersAndDigits_MapToAsciiCodes() {
        assertEquals(65, KeyNames.parseKeyCode("A"));
        assertEquals(71, KeyNames.parseKeyCode("G"));
        assertEquals(90, KeyNames.parseKeyCode("Z"));
        assertEquals(48, KeyNames.parseKeyCode("0"));
        assertEquals(57, KeyNames.parseKeyCode("9"));
    }

    @Test
    void names_AreCaseInsensitiveAndAcceptKeyPrefix() {
        assertEquals(32, KeyNames.parseKeyCode("space"));
        assertEquals(32, KeyNames.parseKeyCode("  Space  "));
        assertEquals(71, KeyNames.parseKeyCode("KEY_G"));
        assertEquals(257, KeyNames.parseKeyCode("enter"));
        assertEquals(257, KeyNames.parseKeyCode("RETURN"));
        assertEquals(256, KeyNames.parseKeyCode("ESC"));
    }

    @Test
    void specialKeys_MatchGlfwCodes() {
        assertEquals(256, KeyNames.parseKeyCode("ESCAPE"));
        assertEquals(258, KeyNames.parseKeyCode("TAB"));
        assertEquals(259, KeyNames.parseKeyCode("BACKSPACE"));
        assertEquals(261, KeyNames.parseKeyCode("DELETE"));
        assertEquals(262, KeyNames.parseKeyCode("RIGHT"));
        assertEquals(265, KeyNames.parseKeyCode("UP"));
        assertEquals(266, KeyNames.parseKeyCode("PAGE_UP"));
        assertEquals(269, KeyNames.parseKeyCode("END"));
        assertEquals(280, KeyNames.parseKeyCode("CAPS_LOCK"));
        assertEquals(290, KeyNames.parseKeyCode("F1"));
        assertEquals(301, KeyNames.parseKeyCode("F12"));
        assertEquals(340, KeyNames.parseKeyCode("LEFT_SHIFT"));
        assertEquals(345, KeyNames.parseKeyCode("RIGHT_CONTROL"));
        assertEquals(348, KeyNames.parseKeyCode("MENU"));
    }

    @Test
    void unknownName_FailsFast() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> KeyNames.parseKeyCode("NOT_A_KEY"));
        assertTrue(error.getMessage().contains("NOT_A_KEY"), error.getMessage());
    }

    @Test
    void keyboardParser_RejectsMouseNamesWithButtonPointer() {
        for (String mouse : new String[]{"MOUSE_LEFT", "MOUSE_RIGHT", "MOUSE_MIDDLE", "BUTTON_1"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> KeyNames.parseKeyCode(mouse), mouse + " must not silently misbind");
            assertTrue(error.getMessage().contains("mouse button"), error.getMessage());
        }
    }

    @Test
    void parseMouseButton_MapsButtonsToCodes() {
        assertEquals(0, KeyNames.parseMouseButton("MOUSE_LEFT"));
        assertEquals(2, KeyNames.parseMouseButton("MOUSE_MIDDLE"));
        assertEquals(1, KeyNames.parseMouseButton("MOUSE_RIGHT"));
        assertEquals(0, KeyNames.parseMouseButton("mouse_button_1"));
        assertEquals(7, KeyNames.parseMouseButton("BUTTON_8"));
        assertThrows(IllegalArgumentException.class, () -> KeyNames.parseMouseButton("MOUSE_WHEEL"));
        assertThrows(IllegalArgumentException.class, () -> KeyNames.parseMouseButton("BUTTON_9"));
    }

    @Test
    void parseModifier_NormalizesAndRejects() {
        assertEquals("none", KeyNames.parseModifier(null));
        assertEquals("none", KeyNames.parseModifier(""));
        assertEquals("none", KeyNames.parseModifier("NONE"));
        assertEquals("shift", KeyNames.parseModifier("Shift"));
        assertEquals("ctrl", KeyNames.parseModifier("CONTROL"));
        assertEquals("alt", KeyNames.parseModifier("ALT"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> KeyNames.parseModifier("hyper"));
        assertTrue(error.getMessage().contains("hyper"), error.getMessage());
    }
}

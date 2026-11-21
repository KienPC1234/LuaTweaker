package com.luatweaker.client;

import com.luatweaker.api.client.KeyNames;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Key-name table: every pinned value mirrors {@code org.lwjgl.glfw.GLFW} in LWJGL 3.3.3
 * (letters/digits are contiguous ASCII, generated in code). Mouse names must not reach the
 * keyboard parser.
 */
class KeyNamesTest {

    private static final List<String> MOUSE_NAMES =
            List.of("MOUSE_LEFT", "MOUSE_RIGHT", "MOUSE_MIDDLE", "BUTTON_1");

    @Test
    void lettersAndDigitsMapToAsciiCodes() {
        assertEquals(Map.of("A", 65, "G", 71, "Z", 90, "0", 48, "9", 57),
                KeyNameParsers.parsed(List.of("A", "G", "Z", "0", "9"),
                        KeyNames::parseKeyCode),
                "letters and digits must map to their contiguous GLFW ASCII codes");
    }

    @Test
    void namesAreCaseInsensitiveAndAcceptKeyPrefix() {
        assertEquals(Map.of("space", 32, "  Space  ", 32, "KEY_G", 71, "enter", 257,
                        "RETURN", 257, "ESC", 256),
                KeyNameParsers.parsed(List.of("space", "  Space  ", "KEY_G", "enter", "RETURN",
                        "ESC"), KeyNames::parseKeyCode),
                "names must be trimmed, case-insensitive and accept a leading KEY_ prefix");
    }

    @Test
    void specialKeysMatchGlfwCodes() {
        assertEquals(Map.ofEntries(
                        Map.entry("ESCAPE", 256), Map.entry("TAB", 258),
                        Map.entry("BACKSPACE", 259), Map.entry("DELETE", 261),
                        Map.entry("RIGHT", 262), Map.entry("UP", 265),
                        Map.entry("PAGE_UP", 266), Map.entry("END", 269),
                        Map.entry("CAPS_LOCK", 280), Map.entry("F1", 290),
                        Map.entry("F12", 301), Map.entry("LEFT_SHIFT", 340),
                        Map.entry("RIGHT_CONTROL", 345), Map.entry("MENU", 348)),
                KeyNameParsers.parsed(List.of("ESCAPE", "TAB", "BACKSPACE", "DELETE", "RIGHT",
                        "UP", "PAGE_UP", "END", "CAPS_LOCK", "F1", "F12", "LEFT_SHIFT",
                        "RIGHT_CONTROL", "MENU"), KeyNames::parseKeyCode),
                "every pinned special key must match the LWJGL 3.3.3 GLFW code");
    }

    @Test
    void unknownKeyNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> KeyNames.parseKeyCode("NOT_A_KEY"),
                "a typo must fail fast instead of binding an arbitrary keysym");
    }

    @Test
    void unknownKeyNameErrorNamesTheInput() {
        assertTrue(KeyNameParsers.rejectionMessages(List.of("NOT_A_KEY"),
                        KeyNames::parseKeyCode).get(0).contains("NOT_A_KEY"),
                "the rejection message must quote the offending key name");
    }

    @Test
    void mouseButtonNamesAreRejectedByTheKeyboardParser() {
        assertTrue(KeyNameParsers.rejectionMessages(MOUSE_NAMES,
                                KeyNames::parseKeyCode).stream()
                        .allMatch(message -> message.contains("mouse button")),
                "a mouse name in a keyboard slot must be rejected and explained");
    }

    @Test
    void mouseButtonsMapToGlfwCodes() {
        assertEquals(Map.of("MOUSE_LEFT", 0, "MOUSE_MIDDLE", 2, "MOUSE_RIGHT", 1,
                        "mouse_button_1", 0, "BUTTON_8", 7),
                KeyNameParsers.parsed(List.of("MOUSE_LEFT", "MOUSE_MIDDLE", "MOUSE_RIGHT",
                        "mouse_button_1", "BUTTON_8"), KeyNames::parseMouseButton),
                "mouse names must map to the GLFW button codes (BUTTON_n is 1-based)");
    }

    @Test
    void unknownMouseButtonsAreRejected() {
        assertTrue(KeyNameParsers.rejectionMessages(List.of("MOUSE_WHEEL", "BUTTON_9"),
                                KeyNames::parseMouseButton).stream()
                        .allMatch(message -> message.contains("unknown mouse button")),
                "an unknown or out-of-range mouse button must be rejected and explained");
    }

    @Test
    void nullModifierBecomesNone() {
        assertEquals("none", KeyNames.parseModifier(null),
                "a nil modifier must default to 'none' instead of failing registration");
    }

    @Test
    void emptyModifierBecomesNone() {
        assertEquals("none", KeyNames.parseModifier(""),
                "a blank modifier must default to 'none' instead of failing registration");
    }
}

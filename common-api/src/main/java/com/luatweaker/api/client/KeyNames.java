package com.luatweaker.api.client;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Human-readable key names for keybind registration ({@code "G"},
 * {@code "SPACE"}, {@code "F5"}, {@code "LEFT_SHIFT"}), mapped to the GLFW
 * key codes the client consumes.
 *
 * <p>All values verified against {@code org.lwjgl.glfw.GLFW} constants in
 * LWJGL 3.3.3 (letters/digits are contiguous ASCII, generated
 * programmatically; the rest are pinned literals). Matching is
 * case-insensitive; a leading {@code KEY_} prefix is accepted so GLFW names
 * paste in verbatim.
 *
 * <p>Mouse buttons are deliberately NOT mapped: keybind entries are tracked
 * as {@code KEYSYM} mappings platform-side, so a mouse code would silently
 * misbind. {@code MOUSE_*} names fail fast and point at
 * {@code UserInputService:IsMouseButtonDown} for polling.
 */
public final class KeyNames {

    private static final Map<String, Integer> BY_NAME = new HashMap<>();

    static {
        // Letters A-Z: GLFW_KEY_A = 65, contiguous ASCII.
        for (int i = 0; i < 26; i++) {
            BY_NAME.put(String.valueOf((char) ('A' + i)), 65 + i);
        }
        // Digits 0-9: GLFW_KEY_0 = 48, contiguous ASCII.
        for (int i = 0; i < 10; i++) {
            BY_NAME.put(String.valueOf((char) ('0' + i)), 48 + i);
        }
        // Pinned literals below (LWJGL 3.3.3).
        put("SPACE", 32);
        put("ESCAPE", 256);
        put("ESC", 256);
        put("ENTER", 257);
        put("RETURN", 257);
        put("TAB", 258);
        put("BACKSPACE", 259);
        put("INSERT", 260);
        put("DELETE", 261);
        put("RIGHT", 262);
        put("LEFT", 263);
        put("DOWN", 264);
        put("UP", 265);
        put("PAGE_UP", 266);
        put("PAGE_DOWN", 267);
        put("HOME", 268);
        put("END", 269);
        put("CAPS_LOCK", 280);
        put("SCROLL_LOCK", 281);
        put("NUM_LOCK", 282);
        put("PRINT_SCREEN", 283);
        put("PAUSE", 284);
        for (int i = 1; i <= 12; i++) {
            put("F" + i, 289 + i);
        }
        put("LEFT_SHIFT", 340);
        put("LEFT_CONTROL", 341);
        put("LEFT_ALT", 342);
        put("LEFT_SUPER", 343);
        put("RIGHT_SHIFT", 344);
        put("RIGHT_CONTROL", 345);
        put("RIGHT_ALT", 346);
        put("RIGHT_SUPER", 347);
        put("MENU", 348);
    }

    private KeyNames() {}

    private static void put(@NotNull String name, int code) {
        BY_NAME.put(name, code);
    }

    /**
     * Parses a keyboard key name to its GLFW code. Unknown names throw (always
     * typos). Mouse names throw here too: a button code would silently misbind
     * as a keysym, so buttons go through {@link #parseMouseButton(String)}
     * where the mouse input type is explicit.
     */
    public static int parseKeyCode(@NotNull String name) {
        if (name == null) {
            throw new IllegalArgumentException("key name must not be null");
        }
        String key = name.strip().toUpperCase(Locale.ROOT);
        if (key.startsWith("KEY_")) {
            key = key.substring("KEY_".length());
        }
        Integer code = BY_NAME.get(key);
        if (code != null) {
            return code;
        }
        if (key.startsWith("MOUSE_") || key.startsWith("BUTTON_")) {
            throw new IllegalArgumentException("'" + name + "' is a mouse button: "
                    + "this parser resolves keyboard keys only; "
                    + "use it where a button is expected (registerKeyBinding accepts "
                    + "mouse names, Content.NewKeyMapping has MouseButton)");
        }
        throw new IllegalArgumentException("unknown key name '" + name
                + "' (e.g. 'G', 'SPACE', 'F5', 'LEFT_SHIFT'); raw GLFW ints are also accepted");
    }

    /**
     * A parsed default key: which input stream it lives on plus the raw code.
     * Keyboard names/ints resolve to {@code key}; mouse names to {@code mouse}.
     */
    public record KeyRef(@NotNull String inputType, int code) {}

    /**
     * Parses a Lua keybind default value. Numbers pass through as raw keyboard
     * codes; strings resolve via mouse names first, then keyboard names, so
     * {@code "MOUSE_LEFT"} binds button 0 instead of misbinding keysym 0.
     */
    public static @NotNull KeyRef parseKeyRef(com.luatweaker.api.vm.ILuaValue value, @NotNull String what) {
        if (value == null || value.isNil()) {
            throw new IllegalArgumentException(what + " is required (key name, mouse button or GLFW code)");
        }
        if (value.isNumber()) {
            return new KeyRef("key", value.asInt());
        }
        if (value.isString()) {
            String name = value.asString();
            if (isMouseButtonName(name)) {
                return new KeyRef("mouse", parseMouseButton(name));
            }
            return new KeyRef("key", parseKeyCode(name));
        }
        throw new IllegalArgumentException(what + " must be a key name, mouse button or number");
    }

    /**
     * Parses a mouse-button name to its GLFW button code
     * ({@code MOUSE_LEFT}=0, {@code MOUSE_RIGHT}=1, {@code MOUSE_MIDDLE}=2;
     * {@code MOUSE_BUTTON_n}/{@code BUTTON_n} map to n-1). Values verified
     * against {@code org.lwjgl.glfw.GLFW} in LWJGL 3.3.3.
     */
    public static int parseMouseButton(@NotNull String name) {
        if (name == null) {
            throw new IllegalArgumentException("mouse button name must not be null");
        }
        String key = name.strip().toUpperCase(Locale.ROOT);
        if (key.startsWith("MOUSE_")) {
            key = key.substring("MOUSE_".length());
        }
        switch (key) {
            case "LEFT":
            case "BUTTON_1":
                return 0;
            case "RIGHT":
            case "BUTTON_2":
                return 1;
            case "MIDDLE":
            case "BUTTON_3":
                return 2;
            default:
                break;
        }
        if (key.startsWith("BUTTON_")) {
            try {
                int index = Integer.parseInt(key.substring("BUTTON_".length()));
                if (index >= 1 && index <= 8) {
                    return index - 1;
                }
            } catch (NumberFormatException ignored) {
                // Falls through to the error below.
            }
        }
        throw new IllegalArgumentException("unknown mouse button '" + name
                + "' (use MOUSE_LEFT, MOUSE_MIDDLE, MOUSE_RIGHT or MOUSE_BUTTON_1-8)");
    }

    /** True when the name addresses a mouse button instead of a keyboard key. */
    public static boolean isMouseButtonName(String name) {
        if (name == null) {
            return false;
        }
        String key = name.strip().toUpperCase(Locale.ROOT);
        return key.startsWith("MOUSE_") || key.startsWith("BUTTON_");
    }

    /**
     * Normalizes a modifier name ({@code none}|{@code shift}|{@code ctrl}|
     * {@code alt}, case-insensitive, blank/null means none). Anything else
     * fails fast: an unknown modifier would silently never match.
     */
    public static @NotNull String parseModifier(String name) {
        if (name == null || name.strip().isEmpty()) {
            return "none";
        }
        String key = name.strip().toUpperCase(Locale.ROOT);
        switch (key) {
            case "NONE":
                return "none";
            case "SHIFT":
                return "shift";
            case "CTRL":
            case "CONTROL":
                return "ctrl";
            case "ALT":
                return "alt";
            default:
                throw new IllegalArgumentException("unknown modifier '" + name
                        + "' (use none, shift, ctrl or alt)");
        }
    }
}

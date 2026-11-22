package com.luatweaker.common.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every ImGui flag parser fails fast with an error that names the offending
 * token and suggests the closest valid name, so Lua typos are diagnosable.
 */
class ImGuiFlagErrorMessagesTest {

    @FunctionalInterface
    private interface FlagParser {
        int parse(String csv);
    }

    private static String captureMessage(FlagParser parser, String input) {
        try {
            parser.parse(input);
        } catch (IllegalArgumentException expected) {
            return expected.getMessage();
        }
        return null;
    }

    @Test
    void windowFlagErrorNamesTheTypo() {
        assertTrue(captureMessage(MinecraftClientPlatform::parseImGuiWindowFlags, "NoReszie")
                        .contains("NoReszie"),
                "the error must name the mistyped window flag so the author can find it");
    }

    @Test
    void windowFlagErrorSuggestsTheClosestFlag() {
        assertTrue(captureMessage(MinecraftClientPlatform::parseImGuiWindowFlags, "NoReszie")
                        .contains("NoResize"),
                "the error must suggest the closest valid window flag");
    }

    @Test
    void colorErrorNamesTheTypo() {
        assertTrue(captureMessage(MinecraftClientPlatform::parseImGuiColor, "Buton")
                        .contains("Buton"),
                "the error must name the mistyped color so the author can find it");
    }

    @Test
    void colorErrorSuggestsTheClosestColor() {
        assertTrue(captureMessage(MinecraftClientPlatform::parseImGuiColor, "Buton")
                        .contains("ButtonHovered"),
                "the error must suggest the closest valid color name");
    }

    @Test
    void tableFlagErrorNamesTheTypo() {
        assertTrue(captureMessage(MinecraftClientPlatform::parseImGuiTableFlags, "Borderz")
                        .contains("Borderz"),
                "the error must name the mistyped table flag so the author can find it");
    }

    @Test
    void tableFlagErrorSuggestsTheClosestFlag() {
        assertTrue(captureMessage(MinecraftClientPlatform::parseImGuiTableFlags, "Borderz")
                        .contains("Borders"),
                "the error must suggest the closest valid table flag");
    }

    @Test
    void columnFlagErrorSuggestsTheClosestFlag() {
        assertTrue(captureMessage(MinecraftClientPlatform::parseImGuiTableColumnFlags, "Wide")
                        .contains("WidthFixed"),
                "the error must suggest the closest valid column flag");
    }

    @Test
    void inputFlagErrorSuggestsTheClosestFlag() {
        assertTrue(captureMessage(MinecraftClientPlatform::parseImGuiInputFlags, "Passwrod")
                        .contains("Password"),
                "the error must suggest the closest valid input flag");
    }

    @Test
    void arrowDirErrorListsTheValidDirections() {
        assertTrue(captureMessage(MinecraftClientPlatform::parseImGuiArrowDir, "Sideways")
                        .contains("Left,Right,Up,Down"),
                "the error must list every valid arrow direction");
    }
}

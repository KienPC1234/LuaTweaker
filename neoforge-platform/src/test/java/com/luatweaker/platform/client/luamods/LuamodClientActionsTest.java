package com.luatweaker.platform.client.luamods;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuamodClientActionsTest {

    @Test
    void toggleConfirmTextTurnsOnWhenDisabled() {
        assertEquals("Turn ON", LuamodClientActions.toggleConfirmText(false, List.of()),
                "a disabled mod must offer to turn on");
    }

    @Test
    void toggleConfirmTextTurnsOffWhenEnabled() {
        assertEquals("Turn OFF", LuamodClientActions.toggleConfirmText(true, List.of()),
                "an enabled mod with no dependents must offer a plain turn-off");
    }

    @Test
    void toggleConfirmTextTurnsOffWithOneDependent() {
        assertEquals("Turn OFF", LuamodClientActions.toggleConfirmText(true, List.of("solo")),
                "a single dependent must not force the confirm flow");
    }

    @Test
    void toggleConfirmTextConfirmsWhenTwoDependents() {
        assertEquals("Confirm OFF (mid, top)",
                LuamodClientActions.toggleConfirmText(true, List.of("lib", "mid", "top")),
                "two affected dependents must be named in the confirm text");
    }

    @Test
    void toggleConfirmTextSummarizesManyDependents() {
        assertEquals("Confirm OFF (5 mods)",
                LuamodClientActions.toggleConfirmText(
                        true, List.of("lib", "mid-a", "mid-b", "top-a", "top-b", "extra")),
                "a crowd of dependents must collapse into a count");
    }

    @Test
    void updateHelpersStayQuietWithoutServiceData() {
        assertTrue(LuamodClientActions.updateAvailableIds().isEmpty(),
                "no update service data must mean no available updates");
    }

    @Test
    void updateInfoIsNullWithoutServiceData() {
        assertNull(LuamodClientActions.updateInfoFor("anything"),
                "no update service data must mean no info for any mod");
    }
}

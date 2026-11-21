package com.luatweaker.client;

import com.luatweaker.api.client.IKeyBindService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins {@link KeyBindServiceImpl}: key/modifier normalization, blank-id and unknown-type rejection,
 * and the release listener path.
 */
class KeyBindServiceImplTest {

    private static final String PAYLOAD = "P";
    private static final String CAST_ID = "cast";
    private static final String CATEGORY = "Cat";

    private final KeyBindServiceImpl service = new KeyBindServiceImpl();

    private IKeyBindService.KeyBindEntry entry(int index) {
        return service.getRegisteredKeyBinds().get(index);
    }

    @Test
    void registerKeyBindDefaultsInputTypeToKey() {
        service.registerKeyBind(CAST_ID, CAST_ID, CATEGORY, 71, PAYLOAD);
        assertEquals("key", entry(0).inputType(),
                "a numeric/keysym default must be registered as a keyboard input");
    }

    @Test
    void registerKeyBindDefaultsModifierToNone() {
        service.registerKeyBind(CAST_ID, CAST_ID, CATEGORY, 71, PAYLOAD);
        assertEquals("none", entry(0).modifier(),
                "omitting the modifier must register as 'none', not as a missing value");
    }

    @Test
    void registerKeyBindNormalizesMouseType() {
        service.registerKeyBind("click", "Click", CATEGORY, 0, PAYLOAD, "MOUSE", "Shift");
        assertEquals("mouse", entry(0).inputType(),
                "an upper-case MOUSE input type must be normalized to 'mouse'");
    }

    @Test
    void registerKeyBindNormalizesShiftModifier() {
        service.registerKeyBind("click", "Click", CATEGORY, 0, PAYLOAD, "MOUSE", "Shift");
        assertEquals("shift", entry(0).modifier(),
                "an upper-case Shift modifier must be normalized to 'shift'");
    }

    @Test
    void registerKeyBindRejectsBlankId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registerKeyBind("", "x", "y", 71, PAYLOAD),
                "a blank keybind id must be rejected instead of registering an unreachable bind");
    }

    @Test
    void registerKeyBindRejectsUnknownInputType() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registerKeyBind("x", "x", "y", 71, PAYLOAD, "touch", "none"),
                "an unknown input type must be rejected instead of falling back to a keysym");
    }

    @Test
    void registerKeyBindRejectsUnknownModifier() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registerKeyBind("x", "x", "y", 71, PAYLOAD, "key", "hyper"),
                "an unknown modifier must be rejected instead of registering a bind that never fires");
    }

    @Test
    void releaseListenerReceivesTheReleasedBind() {
        List<String> released = new ArrayList<>();
        service.setKeyBindReleaseListener((id, payload) -> released.add(id + "=" + payload));
        service.registerKeyBind(CAST_ID, CAST_ID, CATEGORY, 71, PAYLOAD);

        service.triggerKeyBindRelease(CAST_ID, PAYLOAD);
        assertEquals(List.of("cast=P"), released,
                "triggerKeyBindRelease must notify the listener with id and payload");
    }

}

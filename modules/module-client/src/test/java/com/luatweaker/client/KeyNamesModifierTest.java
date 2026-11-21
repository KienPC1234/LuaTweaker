package com.luatweaker.client;

import com.luatweaker.api.client.KeyNames;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link KeyNames#parseModifier}: the four accepted modifiers are normalized to lower case and
 * anything else fails fast so a bind cannot silently never match.
 */
class KeyNamesModifierTest {

    @Test
    void noneModifierStaysNone() {
        assertEquals("none", KeyNames.parseModifier("NONE"),
                "the explicit NONE modifier must normalize to 'none'");
    }

    @Test
    void shiftModifierIsLowercased() {
        assertEquals("shift", KeyNames.parseModifier("Shift"),
                "a mixed-case Shift modifier must normalize to 'shift'");
    }

    @Test
    void controlModifierBecomesCtrl() {
        assertEquals("ctrl", KeyNames.parseModifier("CONTROL"),
                "CONTROL must normalize to the internal 'ctrl' spelling");
    }

    @Test
    void altModifierStaysAlt() {
        assertEquals("alt", KeyNames.parseModifier("ALT"),
                "ALT must normalize to 'alt'");
    }

    @Test
    void unknownModifierIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> KeyNames.parseModifier("hyper"),
                "an unknown modifier must fail fast instead of registering a bind that never fires");
    }

    @Test
    void unknownModifierErrorNamesTheInput() {
        assertTrue(KeyNameParsers.rejectionMessages(List.of("hyper"),
                        KeyNames::parseModifier).get(0).contains("hyper"),
                "the rejection message must quote the offending modifier");
    }
}

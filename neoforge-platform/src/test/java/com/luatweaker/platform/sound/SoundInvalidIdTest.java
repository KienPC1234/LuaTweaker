package com.luatweaker.platform.sound;

import net.minecraft.ResourceLocationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Invalid sound ids must return null instead of throwing into the Lua call. */
class SoundInvalidIdTest {

    @Test
    void nullIdReturnsNull() {
        assertNull(SoundHelper.parseId(null),
                "a null id must return null instead of throwing");
    }

    @Test
    void blankIdReturnsNull() {
        assertNull(SoundHelper.parseId("   "),
                "a blank id must return null instead of throwing");
    }

    @Test
    void idWithSpacesReturnsNull() {
        assertNull(SoundHelper.parseId("Not A Valid Id"),
                "spaces must be rejected");
    }

    @Test
    void vanillaParserThrowsForSpaces() {
        // The reason this matters: NeoForgeEntityWrapper used ResourceLocation.parse(), which
        // threw ResourceLocationException straight into the Lua call.
        assertThrows(ResourceLocationException.class,
                () -> net.minecraft.resources.ResourceLocation.parse("Not A Valid Id"),
                "the vanilla parser must reject spaces so the wrapper has something to guard");
    }
}

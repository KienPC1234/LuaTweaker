package com.luatweaker.client;

import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the built-in effect parameters: documented defaults, clamping of out-of-range values and
 * rejection of NaN / malformed colours (a bad effect must never silently apply).
 */
class ShaderEffectParamsTest extends ShaderTestFixture {

    private static final String LETTERBOX = "letterbox";

    @Test
    void letterboxDefaultsToTwelvePercent() {
        service.setEffect(LETTERBOX, null);
        assertEquals(0.12, ShaderServiceImpl.getLetterboxHeight(), 1e-9,
                "a letterbox without params must use the documented 12% default height");
    }

    @Test
    void clearingLetterboxResetsTheHeight() {
        service.setEffect(LETTERBOX, null);
        service.clearEffect(LETTERBOX);
        assertEquals(0.0, ShaderServiceImpl.getLetterboxHeight(), 1e-9,
                "clearing the letterbox must reset the height instead of leaving the last value");
    }

    @Test
    void outOfRangeParamsAreClamped() {
        ILuaTable letterbox = params();
        letterbox.rawset("height", 99.0);
        service.setEffect(LETTERBOX, letterbox);
        assertEquals(0.4, ShaderServiceImpl.getLetterboxHeight(), 1e-9,
                "a 99% bar must clamp to the 40% maximum instead of covering the screen");
    }

    @Test
    void nanParamsAreRejected() {
        ILuaTable letterbox = params();
        letterbox.rawset("height", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> service.setEffect(LETTERBOX, letterbox),
                "a NaN bar height must be rejected instead of poisoning the uniform");
    }

    @Test
    void tintRejectsUnparseableColor() {
        ILuaTable tint = params();
        tint.rawset("strength", 0.5);
        tint.rawset("color", "not-a-color");
        assertThrows(IllegalArgumentException.class, () -> service.setEffect("tint", tint),
                "an unparseable tint colour must be rejected, not applied as black");
    }

    @Test
    void tintRejectsShortHexColor() {
        ILuaTable tint = params();
        tint.rawset("color", "#FFF");
        assertThrows(IllegalArgumentException.class, () -> service.setEffect("tint", tint),
                "a 3-digit hex colour must be rejected instead of being misread as ARGB");
    }

    @Test
    void tintAcceptsValidColorAndStrength() {
        ILuaTable tint = params();
        tint.rawset("strength", 0.5);
        tint.rawset("color", "#FF0000");
        service.setEffect("tint", tint);
        assertEquals(List.of(0.5, 0xFFFF0000),
                List.of(ShaderServiceImpl.getTintStrength(), ShaderServiceImpl.getTintArgb()),
                "a valid #RRGGBB tint must apply the strength and expand the colour to opaque ARGB");
    }
}

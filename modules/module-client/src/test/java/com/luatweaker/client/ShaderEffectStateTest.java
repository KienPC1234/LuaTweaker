package com.luatweaker.client;

import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins effect state across the service lifetime: camera pulses expire, clearAll resets every static
 * uniform, built-in effects publish their program and clearing the active builtin deactivates it.
 */
class ShaderEffectStateTest extends ShaderTestFixture {

    private static final long KICK_LIFETIME_MS = 60_000L;

    @Test
    void freshKickProducesCameraOffsets() {
        service.setEffect("kick", null);
        assertNotNull(ShaderServiceImpl.getCameraDelta(System.currentTimeMillis()),
                "a fresh kick must produce camera offsets for the renderer");
    }

    @Test
    void expiredKickGoesIdle() {
        service.setEffect("kick", null);
        assertNull(ShaderServiceImpl.getCameraDelta(System.currentTimeMillis() + KICK_LIFETIME_MS),
                "a kick older than its lifetime must report no offsets");
    }

    @Test
    void clearAllResetsLetterboxHeight() {
        service.setEffect("letterbox", null);
        service.clearAll();
        assertEquals(0.0, ShaderServiceImpl.getLetterboxHeight(), 1e-9,
                "clearAll must reset the letterbox height to 0");
    }

    @Test
    void clearAllResetsVignetteStrength() {
        service.setEffect("vignette", null);
        service.clearAll();
        assertEquals(0.0, ShaderServiceImpl.getVignetteStrength(), 1e-9,
                "clearAll must reset the vignette strength to 0");
    }

    @Test
    void clearAllClearsTheActivePass() {
        service.registerGlslPass("warp", BODY);
        service.activatePass("warp");
        service.clearAll();
        assertNull(service.getActivePass(),
                "clearAll must deactivate the running pass, not leave a dead program bound");
    }

    @Test
    void builtinGrainRegistersAndActivates() {
        service.setEffect("grain", null);
        assertEquals("builtin_grain", service.getActivePass(),
                "enabling the grain effect must register and activate its builtin pass");
    }

    @Test
    void builtinGrainPublishesItsFragment() {
        service.setEffect("grain", null);
        String fragment = fragmentSource("builtin_grain");
        assertEquals(List.of(true, true, true),
                List.of(fragment != null, fragment != null && fragment.contains("hash("),
                        fragment != null && fragment.contains("#version 150")),
                "the grain builtin must be published with its body and the injected version header");
    }

    @Test
    void builtinGradeAppliesItsParameters() {
        ILuaTable grade = params();
        grade.rawset("brightness", 0.2);
        grade.rawset("contrast", 1.5);
        grade.rawset("saturation", 0.5);
        service.setEffect("grade", grade);
        assertEquals("builtin_grade", service.getActivePass(),
                "the grade effect must accept brightness/contrast/saturation and activate");
    }

    @Test
    void clearingTheActiveBuiltinDeactivates() {
        service.setEffect("grade", null);
        service.clearEffect("grade");
        assertNull(service.getActivePass(),
                "clearing the active builtin must deactivate the pass");
    }

    @Test
    void builtinNamesAreListed() {
        assertEquals(List.of(true, true, true, 24),
                List.of(ShaderServiceImpl.SUPPORTED_EFFECTS.contains("grain"),
                        ShaderServiceImpl.SUPPORTED_EFFECTS.contains("wobble"),
                        ShaderServiceImpl.SUPPORTED_EFFECTS.contains("kick"),
                        ShaderServiceImpl.SUPPORTED_EFFECTS.size()),
                "SUPPORTED_EFFECTS must list the builtins and keep the documented 11+13 entries");
    }
}

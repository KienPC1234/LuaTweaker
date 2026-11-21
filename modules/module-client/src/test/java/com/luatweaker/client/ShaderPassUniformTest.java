package com.luatweaker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the GLSL pass lifecycle and scalar uniforms: activation round-trips, deactivation clears the
 * pass, and uniform writes require an active pass with a finite value and a real name.
 */
class ShaderPassUniformTest extends ShaderTestFixture {

    private static final String WARP = "warp";
    private static final String GLOW = "Glow";

    private String unknownPassError() {
        return assertThrows(IllegalArgumentException.class, () -> service.activatePass("ghost"),
                "activating an unregistered pass must fail loudly").getMessage();
    }

    private String noActivePassError() {
        service.registerGlslPass(WARP, BODY);
        service.deactivatePass();
        return assertThrows(IllegalArgumentException.class,
                () -> service.setPassUniform(GLOW, 1.0)).getMessage();
    }

    @Test
    void activateUnknownPassErrorPointsAtRegistration() {
        assertTrue(unknownPassError().contains("registerGlslPass"),
                "the activation error must point at registerGlslPass: " + unknownPassError());
    }

    @Test
    void activatePassRoundTrips() {
        service.registerGlslPass(WARP, BODY);
        service.activatePass(WARP);
        assertEquals(WARP, service.getActivePass(),
                "the activated pass id must be readable back through getActivePass");
    }

    @Test
    void deactivatePassClearsTheActivePass() {
        service.registerGlslPass(WARP, BODY);
        service.activatePass(WARP);
        service.deactivatePass();
        assertNull(service.getActivePass(),
                "deactivating must leave no active pass so later uniforms cannot target a dead program");
    }

    @Test
    void setPassUniformRequiresAnActivePass() {
        service.registerGlslPass(WARP, BODY);
        service.deactivatePass();
        assertThrows(IllegalArgumentException.class, () -> service.setPassUniform(GLOW, 1.0),
                "writing a uniform without an active pass must be rejected");
    }

    @Test
    void setPassUniformErrorMentionsActivation() {
        assertTrue(noActivePassError().contains("activatePass"),
                "the missing-active-pass error must point at activatePass: " + noActivePassError());
    }

    @Test
    void setPassUniformRejectsNaN() {
        service.registerGlslPass(WARP, BODY);
        service.activatePass(WARP);
        assertThrows(IllegalArgumentException.class,
                () -> service.setPassUniform(GLOW, Double.NaN),
                "a NaN uniform must be rejected instead of corrupting the shader");
    }

    @Test
    void setPassUniformRejectsBlankName() {
        service.registerGlslPass(WARP, BODY);
        service.activatePass(WARP);
        assertThrows(IllegalArgumentException.class, () -> service.setPassUniform("  ", 1.0),
                "a blank uniform name must be rejected instead of being sent to the program");
    }

    @Test
    void setPassUniformAcceptsNumbersWhenHeadless() {
        service.registerGlslPass(WARP, BODY);
        service.activatePass(WARP);
        assertDoesNotThrow(() -> service.setPassUniform(GLOW, 0.75),
                "headless (no Platform client) uniform writes must be recorded without throwing");
    }
}

package com.luatweaker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins {@code setPassUniformVec}: exactly three finite components are accepted, every other shape
 * is rejected so a mis-sized vector cannot silently shift the uniform.
 */
class ShaderVecUniformTest extends ShaderTestFixture {

    private static final String TINT = "Tint";

    @Test
    void vecUniformRejectsOneComponent() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setPassUniformVec(TINT, new float[] { 1.0f }),
                "a 1-component vec3 uniform must be rejected");
    }

    @Test
    void vecUniformRejectsTwoComponents() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setPassUniformVec(TINT, new float[] { 1.0f, 0.5f }),
                "a 2-component vec3 uniform must be rejected");
    }

    @Test
    void vecUniformRejectsFiveComponents() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setPassUniformVec(TINT,
                        new float[] { 1.0f, 2.0f, 3.0f, 4.0f, 5.0f }),
                "a 5-component vec3 uniform must be rejected");
    }

    @Test
    void vecUniformRejectsNonFiniteComponent() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setPassUniformVec(TINT, new float[] { 1.0f, Float.NaN, 0.0f }),
                "a NaN component must be rejected instead of corrupting the uniform");
    }

    @Test
    void vecUniformAcceptsThreeComponents() {
        service.registerGlslPass("vec", BODY);
        service.activatePass("vec");
        assertDoesNotThrow(() -> service.setPassUniformVec(TINT, new float[] { 1.0f, 0.5f, 0.0f }),
                "a three-component uniform on an active pass must be accepted");
    }
}

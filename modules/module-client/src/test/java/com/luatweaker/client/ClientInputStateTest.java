package com.luatweaker.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UserInputService:IsKeyDown used to always return false because nothing ever called onKeyPress.
 * The event listener now feeds this state; these tests pin the state machine the listener relies on.
 */
class ClientInputStateTest {

    private static final int KEY_W = 87;
    private static final int KEY_A = 65;
    private static final int MOUSE_LEFT = 0;

    private ClientServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClientServiceImpl();
    }

    @Test
    void pressedKeyReportsDown() {
        service.onKeyPress(KEY_W, true);
        assertTrue(service.isKeyDown(KEY_W), "a pressed key must report down");
    }

    @Test
    void pressedMouseButtonReportsDown() {
        service.onMouseButton(MOUSE_LEFT, true);
        assertTrue(service.isMouseButtonDown(MOUSE_LEFT), "a pressed mouse button must report down");
    }

    @Test
    void mouseButtonDoesNotLeakIntoKeyState() {
        service.onMouseButton(MOUSE_LEFT, true);
        assertFalse(service.isKeyDown(MOUSE_LEFT),
                "mouse button 0 must not be mistaken for keyboard key 0");
    }

    @Test
    void releasedKeyReportsUp() {
        service.onKeyPress(KEY_W, true);
        service.onKeyPress(KEY_W, false);
        assertFalse(service.isKeyDown(KEY_W), "a released key must report up");
    }

    @Test
    void releasedMouseButtonReportsUp() {
        service.onMouseButton(MOUSE_LEFT, true);
        service.onMouseButton(MOUSE_LEFT, false);
        assertFalse(service.isMouseButtonDown(MOUSE_LEFT), "a released mouse button must report up");
    }

    @Test
    void repeatedPressKeepsTheKeyDown() {
        service.onKeyPress(KEY_A, true);
        service.onKeyPress(KEY_A, true);
        assertTrue(service.isKeyDown(KEY_A), "a repeated press must keep reporting down");
    }

    @Test
    void oneReleaseClearsARepeatedPress() {
        service.onKeyPress(KEY_A, true);
        service.onKeyPress(KEY_A, true);
        service.onKeyPress(KEY_A, false);
        assertFalse(service.isKeyDown(KEY_A), "one release must clear the key even after repeats");
    }
}

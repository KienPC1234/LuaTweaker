package com.luatweaker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserInputService:IsKeyDown used to always return false because nothing ever called
 * onKeyPress. The event listener now feeds this state; this test pins the state machine
 * the listener relies on.
 */
public class ClientInputStateTest {

    @Test
    public void keysAndMouseButtonsAreTrackedSeparately() {
        ClientServiceImpl service = new ClientServiceImpl();

        service.onKeyPress(87, true);
        assertTrue(service.isKeyDown(87), "a pressed key must report down");

        // GLFW button 0 is the left mouse button; it must not be confused with key 0.
        service.onMouseButton(0, true);
        assertTrue(service.isMouseButtonDown(0));
        assertFalse(service.isKeyDown(0), "mouse buttons must not leak into key state");

        service.onKeyPress(87, false);
        assertFalse(service.isKeyDown(87), "a released key must report up");

        service.onMouseButton(0, false);
        assertFalse(service.isMouseButtonDown(0));
    }

    @Test
    public void repeatedPressIsIdempotent() {
        ClientServiceImpl service = new ClientServiceImpl();

        service.onKeyPress(65, true);
        service.onKeyPress(65, true);
        assertTrue(service.isKeyDown(65));

        service.onKeyPress(65, false);
        assertFalse(service.isKeyDown(65), "one release must clear the key even after repeats");
    }
}

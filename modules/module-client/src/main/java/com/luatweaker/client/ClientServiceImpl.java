package com.luatweaker.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client input state shared by {@code UserInputService:IsKeyDown/IsMouseButtonDown}.
 *
 * <p>Fed by {@code NeoForgeClientEventListener} on the raw GLFW callbacks. Key and mouse
 * ids live in separate sets because GLFW reuses the same small integers for both.</p>
 */
public class ClientServiceImpl {
    private final Set<Integer> pressedKeys = ConcurrentHashMap.newKeySet();
    private final Set<Integer> pressedMouseButtons = ConcurrentHashMap.newKeySet();

    public void onKeyPress(int keyCode, boolean isPressed) {
        if (isPressed) {
            pressedKeys.add(keyCode);
        } else {
            pressedKeys.remove(keyCode);
        }
    }

    public boolean isKeyDown(int keyCode) {
        return pressedKeys.contains(keyCode);
    }

    public void onMouseButton(int button, boolean isPressed) {
        if (isPressed) {
            pressedMouseButtons.add(button);
        } else {
            pressedMouseButtons.remove(button);
        }
    }

    public boolean isMouseButtonDown(int button) {
        return pressedMouseButtons.contains(button);
    }
}

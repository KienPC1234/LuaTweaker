package com.luatweaker.platform.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Container open/close calls outside an ImGui frame must fail with the frame signal. */
class ImGuiFrameBeginEndGuardTest {

    private static NeoForgeClientPlatform platform() {
        return new NeoForgeClientPlatform();
    }

    @Test
    void beginTableOutsideFrameThrows() {
        assertThrows(IllegalStateException.class, () -> platform().imguiBeginTable("t", 2, ""),
                "tables need a live frame for column layout");
    }

    @Test
    void endTableOutsideFrameThrows() {
        assertThrows(IllegalStateException.class, platform()::imguiEndTable,
                "ending a table without a frame must fail instead of popping an empty stack");
    }

    @Test
    void beginTabBarOutsideFrameThrows() {
        assertThrows(IllegalStateException.class, () -> platform().imguiBeginTabBar("t"),
                "tab bars need a live frame for tab layout");
    }

    @Test
    void beginMenuOutsideFrameThrows() {
        assertThrows(IllegalStateException.class, () -> platform().imguiBeginMenu("m"),
                "menus need a live frame for the menu stack");
    }

    @Test
    void beginOutsideFrameThrows() {
        assertThrows(IllegalStateException.class, () -> platform().imguiBegin("x", true, ""),
                "windows need a live frame for the window stack");
    }

    @Test
    void endOutsideFrameThrows() {
        assertThrows(IllegalStateException.class, platform()::imguiEnd,
                "ending a window without a frame must fail instead of popping an empty stack");
    }
}

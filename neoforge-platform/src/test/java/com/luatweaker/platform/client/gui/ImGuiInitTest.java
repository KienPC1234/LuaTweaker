package com.luatweaker.platform.client.gui;

import imgui.ImGui;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ImGuiInitTest {

    @Test
    void testImGuiNativeLoad() {
        ImGui.createContext();

        assertNotNull(ImGui.getIO(), "a fresh ImGui context must expose its IO table");

        ImGui.destroyContext();
    }
}

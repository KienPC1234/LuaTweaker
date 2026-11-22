package com.luatweaker.common.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Widget calls outside an ImGui frame must fail instead of touching GL state. */
class ImGuiWidgetFrameGuardTest {

    private static final String FRAME_SIGNAL = "ImGui.OnFrame";

    @Test
    void frameStartsClosed() {
        assertFalse(new MinecraftClientPlatform().imguiIsFrameOpen(),
                "a fresh platform must report the frame closed until OnFrame runs");
    }

    @Test
    void buttonOutsideFrameThrows() {
        assertThrows(IllegalStateException.class, () -> new MinecraftClientPlatform().imguiButton("x"),
                "buttons need a live frame for interaction state");
    }

    @Test
    void buttonErrorNamesTheFrameSignal() {
        IllegalStateException error = null;
        try {
            new MinecraftClientPlatform().imguiButton("x");
        } catch (IllegalStateException expected) {
            error = expected;
        }
        assertTrue(error != null && error.getMessage().contains(FRAME_SIGNAL),
                "the error must name the OnFrame signal so Lua authors know where widgets belong");
    }

    @Test
    void menuItemOutsideFrameThrows() {
        assertThrows(IllegalStateException.class, () -> new MinecraftClientPlatform().imguiMenuItem("i", "", false, true),
                "menu items need a live frame for the menu stack");
    }

    @Test
    void listBoxOutsideFrameThrows() {
        assertThrows(IllegalStateException.class,
                () -> new MinecraftClientPlatform().imguiListBox("l", 1, new String[] {"a"}),
                "list boxes need a live frame for interaction state");
    }

    @Test
    void imageOutsideFrameThrows() {
        assertThrows(IllegalStateException.class,
                () -> new MinecraftClientPlatform().imguiImage("minecraft:textures/gui/icons.png", 16, 16),
                "images need a live frame for texture binding");
    }

    @Test
    void imageSubOutsideFrameThrows() {
        assertThrows(IllegalStateException.class,
                () -> new MinecraftClientPlatform().imguiImageSub("minecraft:textures/gui/icons.png", 16, 16, 0, 0, 1, 1),
                "image regions need a live frame for texture binding");
    }

    @Test
    void spriteUvOutsideFrameThrows() {
        // Sprite lookup guards the frame before touching Minecraft, so it is
        // headless-safe here; the null-Minecraft path needs a live game.
        assertThrows(IllegalStateException.class, () -> new MinecraftClientPlatform().imguiSpriteUv("atlas", "sprite"),
                "sprite lookups need a live frame before touching the atlas");
    }

    @Test
    void sliderFloatOutsideFrameThrows() {
        assertThrows(IllegalStateException.class, () -> new MinecraftClientPlatform().imguiSliderFloat("x", 1, 0, 2),
                "sliders need a live frame for interaction state");
    }

    @Test
    void itemIconOutsideFrameThrows() {
        assertThrows(IllegalStateException.class, () -> new MinecraftClientPlatform().imguiItemIcon("minecraft:stone", 32, 1),
                "item icons need a live frame for the recorded draw list");
    }
}

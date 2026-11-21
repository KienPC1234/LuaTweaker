package com.luatweaker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@code GuiService} drawing primitives added with the HUD API: every Lua call must reach
 * the platform with the exact argument set the renderer needs.
 */
class GuiDrawBindingTest extends GuiTestFixture {

    @Test
    void drawTextCenteredDispatches() {
        run("GuiService:DrawTextCentered('Mana', 100, 50, 0xFFFFFFFF, true)", "gui_text_centered");
        assertTrue(gui.calls.contains("textCentered Mana @100,50"),
                "DrawTextCentered must forward text and center position: " + gui.calls);
    }

    @Test
    void drawOutlineDispatches() {
        run("GuiService:DrawOutline(10, 10, 120, 40, 0xFF38BDF8)", "gui_outline");
        assertTrue(gui.calls.contains("outline 10,10 120x40"),
                "DrawOutline must forward position and size: " + gui.calls);
    }

    @Test
    void drawProgressBarDispatches() {
        run("GuiService:DrawProgressBar(0, 0, 100, 10, 0.5, 0xFF1E293B, 0xFF0284C7)", "gui_progress");
        assertTrue(gui.calls.contains("progress 0.5"),
                "DrawProgressBar must forward the fill fraction: " + gui.calls);
    }

    @Test
    void drawTextureDispatches() {
        run("GuiService:DrawTexture('minecraft:textures/gui/foo.png', 1, 2, 16, 16)", "gui_texture");
        assertTrue(gui.calls.contains("texture minecraft:textures/gui/foo.png"),
                "DrawTexture must forward the texture id: " + gui.calls);
    }

    @Test
    void drawRectDispatches() {
        run("GuiService:DrawRect(5, 5, 10, 10, 0xFF000000)", "gui_rect");
        assertTrue(gui.calls.contains("rect 5,5 10x10"),
                "DrawRect must forward position and size: " + gui.calls);
    }

    @Test
    void drawTextDispatches() {
        run("GuiService:DrawText('hello', 0, 0, 0xFFFFFFFF, false)", "gui_plain_text");
        assertTrue(gui.calls.contains("text hello"),
                "DrawText must forward the text: " + gui.calls);
    }

    @Test
    void pushClipDispatches() {
        run("GuiService:PushClip(10, 20, 100, 50)", "gui_clip_push");
        assertTrue(gui.calls.contains("clipPush 10,20 100x50"),
                "PushClip must forward the clip rectangle: " + gui.calls);
    }

    @Test
    void drawLineDefaultsThicknessToOne() {
        run("GuiService:DrawLine(0, 0, 50, 25, 0xFFFFFFFF)", "gui_line_default");
        assertTrue(gui.calls.contains("line 0,0 -> 50,25 t1"),
                "DrawLine must default the thickness to 1: " + gui.calls);
    }

    @Test
    void drawLineUsesExplicitThickness() {
        run("GuiService:DrawLine(0, 0, 50, 25, 0xFFFFFFFF, 3)", "gui_line_thick");
        assertTrue(gui.calls.contains("line 0,0 -> 50,25 t3"),
                "DrawLine must forward an explicit thickness: " + gui.calls);
    }

    @Test
    void drawItemDefaultsCountToOne() {
        run("GuiService:DrawItem('minecraft:diamond', 8, 8)", "gui_item_default");
        assertTrue(gui.calls.contains("item minecraft:diamondx1 @8,8"),
                "DrawItem must default the stack count to 1: " + gui.calls);
    }
}

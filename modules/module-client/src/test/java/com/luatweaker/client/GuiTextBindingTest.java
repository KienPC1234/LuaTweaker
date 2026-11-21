package com.luatweaker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@code GuiService} text, sprite and pose primitives: argument parsing (including the
 * optional z default) and dispatch to the platform.
 */
class GuiTextBindingTest extends GuiTestFixture {

    @Test
    void drawItemUsesExplicitCount() {
        run("GuiService:DrawItem('minecraft:diamond', 8, 8, 5)", "gui_item_count");
        assertTrue(gui.calls.contains("item minecraft:diamondx5 @8,8"),
                "DrawItem must forward an explicit stack count: " + gui.calls);
    }

    @Test
    void popClipDispatches() {
        run("GuiService:PopClip()", "gui_clip_pop");
        assertTrue(gui.calls.contains("clipPop"), "PopClip must reach the implementation: " + gui.calls);
    }

    @Test
    void pushPoseDispatches() {
        run("GuiService:PushPose()", "gui_pose_push");
        assertTrue(gui.calls.contains("posePush"), "PushPose must reach the implementation: " + gui.calls);
    }

    @Test
    void translateDefaultsZToZero() {
        run("GuiService:Translate(10, 20)", "gui_translate");
        assertTrue(gui.calls.contains("translate 10.0,20.0,0.0"),
                "Translate must default z to 0: " + gui.calls);
    }

    @Test
    void scaleDefaultsZToOne() {
        run("GuiService:Scale(2, 2)", "gui_scale");
        assertTrue(gui.calls.contains("scale 2.0,2.0,1.0"),
                "Scale must default z to 1: " + gui.calls);
    }

    @Test
    void drawGradientDispatches() {
        run("GuiService:DrawGradient(0, 0, 100, 10, 0xFF00FF00, 0xFFFF0000)", "gui_gradient");
        assertTrue(gui.calls.contains("gradient 0,0 100x10"),
                "DrawGradient must forward position and size: " + gui.calls);
    }

    @Test
    void drawSpriteDispatches() {
        run("GuiService:DrawSprite('minecraft:hud/experience_bar_background', 0, 0, 16, 16)",
                "gui_sprite");
        assertTrue(gui.calls.contains("sprite minecraft:hud/experience_bar_background"),
                "DrawSprite must forward the sprite id: " + gui.calls);
    }

    @Test
    void drawTooltipDispatches() {
        run("GuiService:DrawTooltip('Ruby', 5, 5)", "gui_tooltip");
        assertTrue(gui.calls.contains("tooltip Ruby"),
                "DrawTooltip must forward the tooltip text: " + gui.calls);
    }

    @Test
    void popPoseDispatches() {
        run("GuiService:PopPose()", "gui_pose_pop");
        assertTrue(gui.calls.contains("posePop"), "PopPose must reach the implementation: " + gui.calls);
    }

    @Test
    void measureTextReachesTheImplementation() {
        run("""
                local size = GuiService:MeasureText('Mana')
                assert(size ~= nil, 'MeasureText must return a table')
                assert(size.Width == 24 and size.Height == 9, 'Width/Height mismatch')
                assert(size[1] == 24 and size[2] == 9, 'MeasureText must be indexable by 1,2')
                assert(GuiService:measureText('Mana').Width == 24, 'lowercase alias must exist')
                """, "MeasureTextTest");
        assertTrue(gui.calls.contains("measure Mana"),
                "MeasureText must reach the implementation: " + gui.calls);
    }
}

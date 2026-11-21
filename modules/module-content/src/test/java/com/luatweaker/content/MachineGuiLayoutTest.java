package com.luatweaker.content;

import java.io.IOException;

import com.luatweaker.api.content.IBlockBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Label align/scale/shadow and widget anchors round-trip through the builder. */
class MachineGuiLayoutTest extends ContentFixture {

    private static final String LAYOUT_CHUNK = "layout_chunk";
    private static final String LAYOUT_SCRIPT = """
            local Content = require("LuaTweaker.Content")
            local panel = Content.NewBlock("layout_panel")
                :Container(3, 9, "packed")
                :GuiBar("left_bar", 8, 20, 16, 50, "energy", 0xFF00E676)
                :GuiBar("right_bar", 8, 20, 16, 50, "fluid", 0xFF2196F3, "topright")
                :CustomBar("flux", 26, 20, 16, 50, { currentKey = "Flux", maxKey = "MaxFlux", anchor = "top" })
                :Button("go_btn", 68, 48, 48, 20, "Start")
                :Button("corner_btn", 4, 4, 48, 20, "X", "bottomright")
                :Label(40, 16, "plain", 0xCCCCCC)
                :Label(0, 16, "centered title", 0xFFFFFF, "center", 1.5, true)
                :Label(8, 40, "right note", 0xFFFFFF, "right")
                :Register()
        """;

    private IBlockBuilder panel() throws IOException {
        engine.executeScript(scriptFile(LAYOUT_SCRIPT), LAYOUT_CHUNK);
        return block("layout_panel");
    }

    @Nested
    class BarSpecs {

        @Test
        void defaultToTopLeft() throws IOException {
            assertEquals("topleft", panel().getGuiBars().get(0).anchor(),
                    "bars without an anchor must behave as before");
        }

        @Test
        void anchorIsKept() throws IOException {
            assertEquals("topright", panel().getGuiBars().get(1).anchor(),
                    "the bar anchor must be kept");
        }

        @Test
        void customBarTableAnchorIsKept() throws IOException {
            assertEquals("top", panel().getGuiBars().get(2).anchor(),
                    "the custom-bar table anchor must be kept");
        }
    }

    @Nested
    class ButtonSpecs {

        @Test
        void defaultToTopLeft() throws IOException {
            assertEquals("topleft", panel().getButtons().get(0).anchor(),
                    "buttons without an anchor must behave as before");
        }

        @Test
        void anchorIsKept() throws IOException {
            assertEquals("bottomright", panel().getButtons().get(1).anchor(),
                    "the button anchor must be kept");
        }
    }

    @Nested
    class LabelSpecs {

        @Test
        void defaultToLeftUnscaled() throws IOException {
            assertEquals("left", panel().getLabels().get(0).align(),
                    "labels without options must behave as before");
        }

        @Test
        void alignIsKept() throws IOException {
            assertEquals("center", panel().getLabels().get(1).align(),
                    "the label alignment must be kept");
        }

        @Test
        void scaleIsKept() throws IOException {
            assertEquals(1.5f, panel().getLabels().get(1).scale(),
                    "the label scale must be kept");
        }

        @Test
        void shadowIsKept() throws IOException {
            assertTrue(panel().getLabels().get(1).shadow(),
                    "the label shadow flag must be kept");
        }

        @Test
        void rightAlignIsKept() throws IOException {
            assertEquals("right", panel().getLabels().get(2).align(),
                    "right alignment must be kept");
        }
    }
}

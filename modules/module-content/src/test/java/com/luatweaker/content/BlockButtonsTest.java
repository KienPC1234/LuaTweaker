package com.luatweaker.content;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Block buttons keep id, geometry and label. */
class BlockButtonsTest extends ContentFixture {

    @Test
    void furnaceRegistered() throws IOException {
        furnace();

        assertEquals(1, contentService.getRegisteredBlocks().stream()
                .filter(candidate -> "advanced_furnace".equals(candidate.getId())).count(),
                "the furnace must be registered");
    }

    @Test
    void keepsOneButton() throws IOException {
        assertEquals(1, furnace().getButtons().size(), "exactly one button must be kept");
    }

    @Test
    void keepsButtonId() throws IOException {
        assertEquals("power_btn", furnace().getButtons().get(0).id(),
                "the button id must be kept");
    }

    @Test
    void keepsButtonX() throws IOException {
        assertEquals(10, furnace().getButtons().get(0).x(), "the button x must be kept");
    }

    @Test
    void keepsButtonY() throws IOException {
        assertEquals(20, furnace().getButtons().get(0).y(), "the button y must be kept");
    }

    @Test
    void keepsButtonWidth() throws IOException {
        assertEquals(30, furnace().getButtons().get(0).width(), "the button width must be kept");
    }

    @Test
    void keepsButtonHeight() throws IOException {
        assertEquals(15, furnace().getButtons().get(0).height(), "the button height must be kept");
    }

    @Test
    void keepsButtonLabel() throws IOException {
        assertEquals("Power", furnace().getButtons().get(0).label(),
                "the button label must be kept");
    }
}

package com.luatweaker.content;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Block labels and click/neighbor callbacks are stored for the platform. */
class BlockLabelsCallbacksTest extends ContentFixture {

    @Test
    void keepsOneLabel() throws IOException {
        assertEquals(1, furnace().getLabels().size(), "exactly one label must be kept");
    }

    @Test
    void keepsLabelX() throws IOException {
        assertEquals(5, furnace().getLabels().get(0).x(), "the label x must be kept");
    }

    @Test
    void keepsLabelY() throws IOException {
        assertEquals(10, furnace().getLabels().get(0).y(), "the label y must be kept");
    }

    @Test
    void keepsLabelText() throws IOException {
        assertEquals("Heat: {Temp}", furnace().getLabels().get(0).textOrVar(),
                "the label text must be kept");
    }

    @Test
    void keepsLabelColor() throws IOException {
        assertEquals(0xFF0000, furnace().getLabels().get(0).color(),
                "the label color must be kept");
    }

    @Test
    void keepsButtonClickHandler() throws IOException {
        assertNotNull(furnace().getButtonClickHandler(), "the button-click handler must be stored");
    }

    @Test
    void keepsNeighborChangedHandler() throws IOException {
        assertNotNull(furnace().getNeighborChangedHandler(),
                "the neighbor-changed handler must be stored");
    }
}

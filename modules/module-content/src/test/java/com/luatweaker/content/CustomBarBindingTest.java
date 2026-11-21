package com.luatweaker.content;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Custom GUI bars bind to data keys with a tooltip format. */
class CustomBarBindingTest extends ReactorFixture {

    @Test
    void keepsCustomSource() throws IOException {
        assertEquals("custom", reactor().getGuiBars().get(0).source(),
                "a custom bar must report a custom source");
    }

    @Test
    void keepsCurrentKey() throws IOException {
        assertEquals("CoreHeat", reactor().getGuiBars().get(0).currentKey(),
                "the current-value key must be kept");
    }

    @Test
    void keepsMaxKey() throws IOException {
        assertEquals("MaxHeat", reactor().getGuiBars().get(0).maxKey(),
                "the max-value key must be kept");
    }

    @Test
    void keepsColor() throws IOException {
        assertEquals(0xFF4400, reactor().getGuiBars().get(0).color(),
                "the bar color must be kept");
    }

    @Test
    void keepsTooltipFormat() throws IOException {
        assertEquals("Core: %d / %d C", reactor().getGuiBars().get(0).tooltipFormat(),
                "the tooltip format must be kept");
    }
}

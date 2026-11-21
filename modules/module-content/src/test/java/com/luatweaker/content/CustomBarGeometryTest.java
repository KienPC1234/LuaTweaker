package com.luatweaker.content;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Custom GUI bars keep geometry and identity. */
class CustomBarGeometryTest extends ReactorFixture {

    @Test
    void reactorRegistered() throws IOException {
        reactor();

        assertEquals(1, contentService.getRegisteredBlocks().stream()
                .filter(candidate -> "fusion_reactor".equals(candidate.getId())).count(),
                "the reactor must be registered");
    }

    @Test
    void keepsOneBar() throws IOException {
        assertEquals(1, reactor().getGuiBars().size(), "exactly one custom bar must be kept");
    }

    @Test
    void keepsBarId() throws IOException {
        assertEquals("heat_gauge", reactor().getGuiBars().get(0).id(),
                "the bar id must be kept");
    }

    @Test
    void keepsBarX() throws IOException {
        assertEquals(10, reactor().getGuiBars().get(0).x(), "the bar x must be kept");
    }

    @Test
    void keepsBarY() throws IOException {
        assertEquals(20, reactor().getGuiBars().get(0).y(), "the bar y must be kept");
    }

    @Test
    void keepsBarWidth() throws IOException {
        assertEquals(16, reactor().getGuiBars().get(0).width(), "the bar width must be kept");
    }

    @Test
    void keepsBarHeight() throws IOException {
        assertEquals(50, reactor().getGuiBars().get(0).height(), "the bar height must be kept");
    }
}

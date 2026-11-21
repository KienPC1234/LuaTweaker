package com.luatweaker.content;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Bulk block properties (color, factors, collision) round-trip through Lua. */
class BlockPropertiesTest extends ContentFixture {

    @Test
    void keepsId() throws IOException {
        assertEquals("prop_block", propBlock().getId(), "the block id must be kept");
    }

    @Test
    void keepsMapColor() throws IOException {
        assertEquals("RED", propBlock().getMapColor(), "the map color must be kept");
    }

    @Test
    void keepsJumpFactor() throws IOException {
        assertEquals(1.5f, propBlock().getJumpFactor(), "the jump factor must be kept");
    }

    @Test
    void keepsSpeedFactor() throws IOException {
        assertEquals(0.5f, propBlock().getSpeedFactor(), "the speed factor must be kept");
    }

    @Test
    void keepsNoCollision() throws IOException {
        assertTrue(propBlock().isNoCollision(), "no-collision must be kept");
    }

    @Test
    void keepsNoOcclusion() throws IOException {
        assertTrue(propBlock().isNoOcclusion(), "no-occlusion must be kept");
    }
}

package com.luatweaker.content;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Block behavior flags (push reaction, replaceability, redstone) round-trip. */
class BlockFlagsTest extends ContentFixture {

    @Test
    void keepsPushReaction() throws IOException {
        assertEquals("BLOCK", propBlock().getPushReaction(), "the push reaction must be kept");
    }

    @Test
    void keepsReplaceable() throws IOException {
        assertTrue(propBlock().isReplaceable(), "replaceable must be kept");
    }

    @Test
    void keepsIgnitedByLava() throws IOException {
        assertTrue(propBlock().isIgnitedByLava(), "ignited-by-lava must be kept");
    }

    @Test
    void liquidDefaultsToFalse() throws IOException {
        assertFalse(propBlock().isLiquid(), "liquid must default to false");
    }

    @Test
    void keepsOffsetType() throws IOException {
        assertEquals("XZ", propBlock().getOffsetType(), "the offset type must be kept");
    }

    @Test
    void keepsRedstoneConductor() throws IOException {
        assertEquals(Boolean.FALSE, propBlock().getRedstoneConductor(),
                "redstone conductor must be kept false");
    }
}

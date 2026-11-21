package com.luatweaker.content;

import java.io.IOException;

import com.luatweaker.api.content.IBlockBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Lua-defined blocks keep hardness, resistance, light and sound. */
class BlockBuilderTest extends ContentFixture {

    private static final String BLOCK_CHUNK = "block_chunk";
    private static final String BLOCK_SCRIPT = """
            startup:createBlock("custom_ruby_block", function(block)
                block:hardness(3.0)
                     :resistance(12.0)
                     :lightLevel(10)
                     :soundType("STONE")
            end)
        """;

    private IBlockBuilder rubyBlock() throws IOException {
        engine.executeScript(scriptFile(BLOCK_SCRIPT), BLOCK_CHUNK);
        return block("custom_ruby_block");
    }

    @Test
    void oneBlockRegistered() throws IOException {
        rubyBlock();

        assertEquals(1, contentService.getRegisteredBlocks().size(),
                "exactly one block must be registered");
    }

    @Test
    void keepsId() throws IOException {
        assertEquals("custom_ruby_block", rubyBlock().getId(), "the block id must be kept");
    }

    @Test
    void keepsHardness() throws IOException {
        assertEquals(3.0f, rubyBlock().getHardness(), "the hardness must be kept");
    }

    @Test
    void keepsResistance() throws IOException {
        assertEquals(12.0f, rubyBlock().getResistance(), "the resistance must be kept");
    }

    @Test
    void keepsLightLevel() throws IOException {
        assertEquals(10, rubyBlock().getLightLevel(), "the light level must be kept");
    }

    @Test
    void keepsSoundType() throws IOException {
        assertEquals("STONE", rubyBlock().getSoundType(), "the sound type must be kept");
    }
}

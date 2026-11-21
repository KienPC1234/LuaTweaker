package com.luatweaker.content;

import java.io.IOException;

import com.luatweaker.api.content.IBlockBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Facing, collision boxes and render handlers round-trip through Lua. */
class BlockBoxesTest extends ContentFixture {

    private static final String ALTAR_CHUNK = "altar_chunk";
    private static final String ALTAR_SCRIPT = """
            local Content = require("LuaTweaker.Content")
            local altar = Content.NewBlock("altar_block")
                :Facing("horizontal")
                :Box(0, 0, 0, 16, 4, 16)
                :Box(4, 4, 4, 12, 12, 12)
                :OnRender(function(renderer, data)
                    -- render callback
                end)
                :Register()
        """;

    private IBlockBuilder altar() throws IOException {
        engine.executeScript(scriptFile(ALTAR_SCRIPT), ALTAR_CHUNK);
        return block("altar_block");
    }

    @Test
    void altarRegistered() throws IOException {
        altar();

        assertDoesNotThrow(() -> block("altar_block"), "altar_block must be registered");
    }

    @Test
    void keepsFacing() throws IOException {
        assertEquals("HORIZONTAL", altar().getFacing(), "the facing must be kept");
    }

    @Test
    void keepsTwoBoxes() throws IOException {
        assertEquals(2, altar().getBoxes().size(), "both collision boxes must be kept");
    }

    @Test
    void keepsFirstBoxMinX() throws IOException {
        assertEquals(0.0, altar().getBoxes().get(0).minX(), "the first box minX must be kept");
    }

    @Test
    void keepsFirstBoxMaxX() throws IOException {
        assertEquals(16.0, altar().getBoxes().get(0).maxX(), "the first box maxX must be kept");
    }

    @Test
    void keepsFirstBoxMaxY() throws IOException {
        assertEquals(4.0, altar().getBoxes().get(0).maxY(), "the first box maxY must be kept");
    }

    @Test
    void keepsSecondBoxMinX() throws IOException {
        assertEquals(4.0, altar().getBoxes().get(1).minX(), "the second box minX must be kept");
    }

    @Test
    void keepsSecondBoxMaxX() throws IOException {
        assertEquals(12.0, altar().getBoxes().get(1).maxX(), "the second box maxX must be kept");
    }

    @Test
    void keepsRenderHandler() throws IOException {
        assertNotNull(altar().getRenderHandler(), "render handler must be stored");
    }
}

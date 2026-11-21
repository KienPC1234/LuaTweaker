package com.luatweaker.content;

import java.io.IOException;

import com.luatweaker.api.content.IBlockBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Container blocks keep custom use distance or default to vanilla reach. */
class ContainerDistanceTest extends ContentFixture {

    private static final String DISTANCE_CHUNK = "distance_chunk";
    private static final String DISTANCE_SCRIPT = """
            local Content = require("LuaTweaker.Content")
            local crate = Content.NewBlock("test_crate")
                :Container(4, 6, "packed")
                :ContainerUseDistance(10)
                :Register()
            local defaultCrate = Content.NewBlock("default_crate")
                :Container(2, 3, "spill")
                :Register()
        """;

    private void runDistanceScript() throws IOException {
        engine.executeScript(scriptFile(DISTANCE_SCRIPT), DISTANCE_CHUNK);
    }

    @Test
    void twoBlocksRegistered() throws IOException {
        runDistanceScript();

        assertEquals(2, contentService.getRegisteredBlocks().size(),
                "both crates must be registered");
    }

    @Test
    void customDistanceKept() throws IOException {
        runDistanceScript();

        assertEquals(10.0, block("test_crate").getContainerUseDistance(),
                "the custom use distance must be kept");
    }

    @Test
    void defaultDistanceIsVanillaReach() throws IOException {
        runDistanceScript();

        assertEquals(8.0, block("default_crate").getContainerUseDistance(),
                "use distance must default to vanilla reach (8 blocks)");
    }

    @Test
    void allAreContainers() throws IOException {
        runDistanceScript();

        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            assertTrue(builder.isContainer(), "every crate must be a container");
        }
    }

    @Test
    void customCrateRegistered() throws IOException {
        runDistanceScript();

        assertDoesNotThrow(() -> block("test_crate"),
                "custom-distance container must have been registered");
    }

    @Test
    void defaultCrateRegistered() throws IOException {
        runDistanceScript();

        assertDoesNotThrow(() -> block("default_crate"),
                "default-distance container must have been registered");
    }
}

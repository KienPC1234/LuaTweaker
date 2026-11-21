package com.luatweaker.platform.content;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Directional facing must generate all six rotations including up and down. */
class MachineFacingDirectionalTest {

    private static final String BLOCKSTATE_KEY = "assets/luatweaker/blockstates/pipe_end.json";

    private static Map<String, String> pipeEndFiles() {
        Map<String, String> files = MachineAssetFixtures.generateFacing("pipe_end", "DIRECTIONAL");
        MachineAssetFixtures.assertValidJson(files);
        return files;
    }

    private static String blockstate() {
        Map<String, String> files = pipeEndFiles();
        String blockstate = files.get(BLOCKSTATE_KEY);

        assertNotNull(blockstate,
                "the directional machine must emit a blockstate file");
        return blockstate;
    }

    @Test
    void blockstateListsDown() {
        assertTrue(blockstate().contains("\"facing=down\""),
                "the down rotation must be present");
    }

    @Test
    void blockstateListsUp() {
        assertTrue(blockstate().contains("\"facing=up\""),
                "the up rotation must be present");
    }

    @Test
    void blockstateListsNorth() {
        assertTrue(blockstate().contains("\"facing=north\""),
                "the north rotation must be present");
    }

    @Test
    void blockstateListsSouth() {
        assertTrue(blockstate().contains("\"facing=south\""),
                "the south rotation must be present");
    }

    @Test
    void blockstateListsWest() {
        assertTrue(blockstate().contains("\"facing=west\""),
                "the west rotation must be present");
    }

    @Test
    void blockstateListsEast() {
        assertTrue(blockstate().contains("\"facing=east\""),
                "the east rotation must be present");
    }

    @Test
    void upRotationTilts90Degrees() {
        assertTrue(blockstate().contains("\"x\": 90"),
                "the up rotation must tilt the model 90 degrees");
    }

    @Test
    void downRotationTilts270Degrees() {
        assertTrue(blockstate().contains("\"x\": 270"),
                "the down rotation must tilt the model 270 degrees");
    }
}

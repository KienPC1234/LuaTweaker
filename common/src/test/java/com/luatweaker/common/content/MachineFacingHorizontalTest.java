package com.luatweaker.common.content;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Horizontal facing must generate one rotated variant per direction. */
class MachineFacingHorizontalTest {

    private static final String BLOCKSTATE_KEY = "assets/luatweaker/blockstates/altar.json";

    private static Map<String, String> altarFiles() {
        Map<String, String> files = MachineAssetFixtures.generateFacing("altar", "HORIZONTAL");
        MachineAssetFixtures.assertValidJson(files);
        return files;
    }

    private static String blockstate() {
        Map<String, String> files = altarFiles();
        String blockstate = files.get(BLOCKSTATE_KEY);

        assertNotNull(blockstate,
                "the horizontal machine must emit a blockstate file");
        return blockstate;
    }

    @Test
    void blockstateListsNorth() {
        assertTrue(blockstate().contains("\"facing=north\""),
                "the north rotation must be present");
    }

    @Test
    void blockstateListsEast() {
        assertTrue(blockstate().contains("\"facing=east\""),
                "the east rotation must be present");
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
}

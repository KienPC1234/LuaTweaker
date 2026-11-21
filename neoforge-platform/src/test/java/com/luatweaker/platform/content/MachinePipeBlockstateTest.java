package com.luatweaker.platform.content;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pipe blockstate must enumerate every connection combination. */
class MachinePipeBlockstateTest {

    private static Map<String, String> pipeFiles() {
        Map<String, String> files = MachineAssetFixtures.generate(null, true);
        MachineAssetFixtures.assertValidJson(files);
        return files;
    }

    private static String blockstate() {
        Map<String, String> files = pipeFiles();
        String blockstate = files.get("assets/luatweaker/blockstates/test_machine.json");

        assertNotNull(blockstate,
                "the pipe machine must emit a blockstate file");
        return blockstate;
    }

    @Test
    void blockstateListsAllConnectionsOff() {
        assertTrue(blockstate()
                        .contains("\"north=false,east=false,south=false,west=false,up=false,down=false\""),
                "the isolated combination must be present");
    }

    @Test
    void blockstateListsAllConnectionsOn() {
        assertTrue(blockstate()
                        .contains("\"north=true,east=true,south=true,west=true,up=true,down=true\""),
                "the fully-connected combination must be present");
    }

    @Test
    void allOnVariantReferencesFullModel() {
        assertTrue(blockstate().contains("\"luatweaker:block/test_machine_pipe_111111\""),
                "the fully-connected variant must reference the full pipe model");
    }
}

package com.luatweaker.common.content;

import com.luatweaker.api.content.BooleanStateSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A boolean state multiplies the pipe connection rows without new models. */
class MachinePipeStateAssetTest {

    private static final BooleanStateSpec RUNNING_STATE = new BooleanStateSpec("running",
            "luatweaker:block/test_machine", "luatweaker:block/test_machine_running");

    private static Map<String, String> statePipeFiles() {
        Map<String, String> files = MachineAssetFixtures.generate(RUNNING_STATE, true);
        MachineAssetFixtures.assertValidJson(files);
        return files;
    }

    private static String blockstate() {
        return statePipeFiles().get("assets/luatweaker/blockstates/test_machine.json");
    }

    @Test
    void blockstateListsOffStateWithNoConnections() {
        assertTrue(blockstate()
                        .contains("\"running=false,north=false,east=false,south=false,west=false,up=false,down=false\""),
                "the off-state isolated row must be present");
    }

    @Test
    void blockstateListsOnStateWithNoConnections() {
        assertTrue(blockstate()
                        .contains("\"running=true,north=false,east=false,south=false,west=false,up=false,down=false\""),
                "the on-state isolated row must be present");
    }

    @Test
    void stateDoesNotGetItsOwnModels() {
        // still exactly 64 pipe models (state does not get its own model)
        assertEquals(64, statePipeFiles().keySet().stream()
                        .filter(key -> key.contains("models/block/test_machine_pipe_"))
                        .count(),
                "the boolean state must reuse the 64 connection models");
    }
}

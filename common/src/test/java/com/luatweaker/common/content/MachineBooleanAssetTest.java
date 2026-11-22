package com.luatweaker.common.content;

import com.luatweaker.api.content.BooleanStateSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Machine variant assets must be valid JSON covering every block-state combination. */
class MachineBooleanAssetTest {

    private static final BooleanStateSpec RUNNING_STATE = new BooleanStateSpec("running",
            "luatweaker:block/test_machine", "luatweaker:block/test_machine_running");

    private static final String BLOCKSTATE_KEY = "assets/luatweaker/blockstates/test_machine.json";

    private static Map<String, String> booleanFiles() {
        Map<String, String> files = MachineAssetFixtures.generate(RUNNING_STATE, false);
        MachineAssetFixtures.assertValidJson(files);
        return files;
    }

    @Test
    void blockstateHasOffVariant() {
        String blockstate = booleanFiles().get(BLOCKSTATE_KEY);

        assertTrue(blockstate.contains("\"running=false\""),
                "the off variant must be present");
    }

    @Test
    void blockstateHasOnVariant() {
        String blockstate = booleanFiles().get(BLOCKSTATE_KEY);

        assertTrue(blockstate.contains("\"running=true\""),
                "the on variant must be present");
    }

    @Test
    void onVariantUsesRunningTexture() {
        String blockstate = booleanFiles().get(BLOCKSTATE_KEY);

        assertTrue(blockstate.contains("\"luatweaker:block/test_machine_running\""),
                "the on variant must reference the running texture");
    }

    @Test
    void blockstateExists() {
        assertNotNull(booleanFiles().get(BLOCKSTATE_KEY),
                "the boolean machine must emit a blockstate file");
    }

    @Test
    void offModelExists() {
        assertTrue(booleanFiles().containsKey("assets/luatweaker/models/block/test_machine.json"),
                "the off-state block model must be generated");
    }

    @Test
    void onModelExists() {
        assertTrue(booleanFiles().containsKey("assets/luatweaker/models/block/test_machine_running.json"),
                "the on-state block model must be generated");
    }

    @Test
    void itemModelExists() {
        assertTrue(booleanFiles().containsKey("assets/luatweaker/models/item/test_machine.json"),
                "the item model must be generated");
    }

    @Test
    void offModelUsesBaseTexture() {
        String offModel = booleanFiles().get("assets/luatweaker/models/block/test_machine.json");

        assertTrue(offModel.contains("\"all\": \"luatweaker:block/test_machine\""),
                "the off-state model must reference the base texture");
    }

    @Test
    void unknownStatesProduceNoAssets() {
        Map<String, String> files = MachineAssetFixtures.generate(null, false);

        assertTrue(files.isEmpty(),
                "plain blocks must not generate machine assets");
    }
}

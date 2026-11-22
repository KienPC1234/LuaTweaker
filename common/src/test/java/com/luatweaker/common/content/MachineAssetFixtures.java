package com.luatweaker.common.content;

import com.luatweaker.api.content.BooleanStateSpec;
import com.luatweaker.content.DatapackServiceImpl;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Shared generators for the machine-asset tests. */
final class MachineAssetFixtures {

    private MachineAssetFixtures() {
    }

    static Map<String, String> generate(BooleanStateSpec state, boolean connections) {
        DatapackServiceImpl datapack = new DatapackServiceImpl();
        MachineAssetGenerator.generate(datapack, "luatweaker", "test_machine", state, connections,
                "luatweaker:block/test_machine");
        return datapack.getVirtualFiles();
    }

    static Map<String, String> generateFacing(String name, String facing) {
        DatapackServiceImpl datapack = new DatapackServiceImpl();
        MachineAssetGenerator.generate(datapack, "luatweaker", name, null, false, facing,
                "luatweaker:block/" + name, null);
        return datapack.getVirtualFiles();
    }

    static void assertValidJson(Map<String, String> files) {
        assertFalse(files.isEmpty(), "generated assets expected");
        for (Map.Entry<String, String> entry : files.entrySet()) {
            assertDoesNotThrow(() -> com.google.gson.JsonParser.parseString(entry.getValue()),
                    "generated JSON must parse: " + entry.getKey() + " = " + entry.getValue());
        }
    }
}

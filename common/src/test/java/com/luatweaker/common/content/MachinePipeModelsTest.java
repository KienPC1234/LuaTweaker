package com.luatweaker.common.content;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A pipe block must generate one model per connection combination. */
class MachinePipeModelsTest {

    private static Map<String, String> pipeFiles() {
        Map<String, String> files = MachineAssetFixtures.generate(null, true);
        MachineAssetFixtures.assertValidJson(files);
        return files;
    }

    @Test
    void generatesAll64ConnectionModels() {
        assertEquals(64, pipeFiles().keySet().stream()
                        .filter(key -> key.contains("models/block/test_machine_pipe_"))
                        .count(),
                "one model per connection combination");
    }

    @Test
    void emptyConnectionsModelExists() {
        assertTrue(pipeFiles().containsKey("assets/luatweaker/models/block/test_machine_pipe_000000.json"),
                "the isolated-pipe model must be generated");
    }

    @Test
    void fullConnectionsModelExists() {
        assertTrue(pipeFiles().containsKey("assets/luatweaker/models/block/test_machine_pipe_111111.json"),
                "the fully-connected pipe model must be generated");
    }
}

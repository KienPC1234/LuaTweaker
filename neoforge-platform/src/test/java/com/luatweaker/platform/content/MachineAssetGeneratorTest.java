package com.luatweaker.platform.content;

import com.luatweaker.api.content.BooleanStateSpec;
import com.luatweaker.content.DatapackServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Machine variant assets must be valid JSON covering every block-state
 * combination: 2 variants for a boolean state, 64 + state for pipes, with
 * models referencing the configured textures.
 */
public class MachineAssetGeneratorTest {

    private Map<String, String> generate(BooleanStateSpec state, boolean connections) {
        DatapackServiceImpl datapack = new DatapackServiceImpl();
        MachineAssetGenerator.generate(datapack, "luatweaker", "test_machine", state, connections, "luatweaker:block/test_machine");
        return datapack.getVirtualFiles();
    }

    private void assertValidJson(Map<String, String> files) {
        assertFalse(files.isEmpty(), "generated assets expected");
        for (Map.Entry<String, String> entry : files.entrySet()) {
            assertDoesNotThrow(() -> com.google.gson.JsonParser.parseString(entry.getValue()),
                    "generated JSON must parse: " + entry.getKey() + " = " + entry.getValue());
        }
    }

    @Test
    public void booleanStateGeneratesTwoVariantsAndModels() {
        Map<String, String> files = generate(
                new BooleanStateSpec("running", "luatweaker:block/test_machine", "luatweaker:block/test_machine_running"),
                false);

        assertValidJson(files);
        String blockstate = files.get("assets/luatweaker/blockstates/test_machine.json");
        assertNotNull(blockstate);
        assertTrue(blockstate.contains("\"running=false\""), "off variant required");
        assertTrue(blockstate.contains("\"running=true\""));
        assertTrue(blockstate.contains("\"luatweaker:block/test_machine_running\""));
        assertTrue(files.containsKey("assets/luatweaker/models/block/test_machine.json"));
        assertTrue(files.containsKey("assets/luatweaker/models/block/test_machine_running.json"));
        assertTrue(files.containsKey("assets/luatweaker/models/item/test_machine.json"));

        String offModel = files.get("assets/luatweaker/models/block/test_machine.json");
        assertTrue(offModel.contains("\"all\": \"luatweaker:block/test_machine\""));
    }

    @Test
    public void pipeGeneratesAll64ConnectionVariants() {
        Map<String, String> files = generate(null, true);

        assertValidJson(files);
        assertEquals(64, files.keySet().stream()
                .filter(k -> k.contains("models/block/test_machine_pipe_"))
                .count(), "one model per connection combination");
        assertTrue(files.containsKey("assets/luatweaker/models/block/test_machine_pipe_000000.json"));
        assertTrue(files.containsKey("assets/luatweaker/models/block/test_machine_pipe_111111.json"));

        String blockstate = files.get("assets/luatweaker/blockstates/test_machine.json");
        assertNotNull(blockstate);
        assertTrue(blockstate.contains("\"north=true,east=true,south=true,west=true,up=true,down=true\""));
        assertTrue(blockstate.contains("\"north=false,east=false,south=false,west=false,up=false,down=false\""));
        assertTrue(blockstate.contains("\"luatweaker:block/test_machine_pipe_111111\""));

        String fullModel = files.get("assets/luatweaker/models/block/test_machine_pipe_111111.json");
        assertTrue(fullModel.contains("\"#pipe\""), "pipe model must reference its texture");
        assertTrue(fullModel.contains("\"particle\": \"luatweaker:block/test_machine\""));
        // center cube + 6 arms = 7 elements
        assertEquals(7, com.google.gson.JsonParser.parseString(fullModel)
                .getAsJsonObject().getAsJsonArray("elements").size());
    }

    @Test
    public void pipeWithBooleanStateCoversStateTimesConnections() {
        Map<String, String> files = generate(
                new BooleanStateSpec("running", "luatweaker:block/test_machine", "luatweaker:block/test_machine_running"),
                true);

        assertValidJson(files);
        String blockstate = files.get("assets/luatweaker/blockstates/test_machine.json");
        assertTrue(blockstate.contains("\"running=false,north=false,east=false,south=false,west=false,up=false,down=false\""));
        assertTrue(blockstate.contains("\"running=true,north=false,east=false,south=false,west=false,up=false,down=false\""));
        // still exactly 64 pipe models (state does not get its own model)
        assertEquals(64, files.keySet().stream()
                .filter(k -> k.contains("models/block/test_machine_pipe_"))
                .count());
    }

    @Test
    public void unknownStatesProduceNoAssets() {
        Map<String, String> files = generate(null, false);
        assertTrue(files.isEmpty(), "plain blocks must not generate machine assets");
    }
}

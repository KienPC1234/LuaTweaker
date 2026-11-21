package com.luatweaker.platform.content;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fully-connected pipe model must be a hollow tube with a particle texture. */
class MachinePipeFullModelTest {

    private static String fullModel() {
        Map<String, String> files = MachineAssetFixtures.generate(null, true);
        MachineAssetFixtures.assertValidJson(files);
        return files.get("assets/luatweaker/models/block/test_machine_pipe_111111.json");
    }

    @Test
    void fullModelReferencesThePipeTexture() {
        assertTrue(fullModel().contains("\"#pipe\""),
                "pipe model must reference its texture");
    }

    @Test
    void fullModelDeclaresTheParticleTexture() {
        assertTrue(fullModel().contains("\"particle\": \"luatweaker:block/test_machine\""),
                "the full pipe model must declare the base particle texture");
    }

    @Test
    void fullModelHasSevenElements() {
        // center cube + 6 arms = 7 elements
        assertEquals(7, JsonParser.parseString(fullModel())
                        .getAsJsonObject().getAsJsonArray("elements").size(),
                "the full pipe model must be a center cube plus six arms");
    }
}

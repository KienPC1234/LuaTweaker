package com.luatweaker.platform.content;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Horizontal rotations and models must use the right angles and textures. */
class MachineFacingHorizontalRotationTest {

    private static Map<String, String> altarFiles() {
        Map<String, String> files = MachineAssetFixtures.generateFacing("altar", "HORIZONTAL");
        MachineAssetFixtures.assertValidJson(files);
        return files;
    }

    private static String blockstate() {
        return altarFiles().get("assets/luatweaker/blockstates/altar.json");
    }

    @Test
    void eastRotationIs90Degrees() {
        assertTrue(blockstate().contains("\"y\": 90"),
                "the east rotation must turn the model 90 degrees");
    }

    @Test
    void southRotationIs180Degrees() {
        assertTrue(blockstate().contains("\"y\": 180"),
                "the south rotation must turn the model 180 degrees");
    }

    @Test
    void westRotationIs270Degrees() {
        assertTrue(blockstate().contains("\"y\": 270"),
                "the west rotation must turn the model 270 degrees");
    }

    @Test
    void blockModelExists() {
        assertTrue(altarFiles().containsKey("assets/luatweaker/models/block/altar.json"),
                "the horizontal machine must generate a block model");
    }

    @Test
    void itemModelExists() {
        assertTrue(altarFiles().containsKey("assets/luatweaker/models/item/altar.json"),
                "the horizontal machine must generate an item model");
    }
}

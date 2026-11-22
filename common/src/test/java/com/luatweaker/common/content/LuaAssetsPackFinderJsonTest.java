package com.luatweaker.common.content;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.luatweaker.content.ContentServiceImpl;
import com.luatweaker.content.DatapackServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies that all JSON assets emitted by ContentVirtualFiles parse cleanly
 * without malformed syntax or unterminated string errors.
 */
class ContentVirtualFilesJsonTest {

    private static final String CRYSTAL_BLOCK_ID = "luatweaker:crystal_block";

    @Test
    void generatedAssetJsonMustBeWellFormed() {
        ContentServiceImpl content = new ContentServiceImpl();
        content.createItem("luatweaker:crystal_shard", null);
        content.createBlock(CRYSTAL_BLOCK_ID, null);
        content.createSlab("luatweaker:crystal_slab", CRYSTAL_BLOCK_ID);
        content.createStairs("luatweaker:crystal_stairs", CRYSTAL_BLOCK_ID);
        content.createWall("luatweaker:crystal_wall", CRYSTAL_BLOCK_ID);
        content.createFluid("luatweaker:liquid_crystal", null);
        content.createEntity("luatweaker:crystal_beast", e -> e.spawnEgg(0xFF0000, 0x00FF00));

        DatapackServiceImpl datapack = new DatapackServiceImpl();
        ContentVirtualFiles.rebuild(new File("build/tmp/test-lua"), content, datapack);

        List<String> failedFiles = new ArrayList<>();
        for (Map.Entry<String, String> entry : datapack.getVirtualFiles().entrySet()) {
            if (entry.getKey().endsWith(".json")) {
                try {
                    JsonParser.parseString(entry.getValue());
                } catch (JsonSyntaxException ex) {
                    failedFiles.add(entry.getKey() + " -> " + ex.getMessage());
                }
            }
        }

        assertDoesNotThrow(() -> {
            if (datapack.getVirtualFiles().isEmpty() || !failedFiles.isEmpty()) {
                throw new AssertionError("Emitted invalid or empty JSON assets: " + String.join(", ", failedFiles));
            }
        }, "Every generated JSON asset file must be syntactically valid and non-empty");
    }
}

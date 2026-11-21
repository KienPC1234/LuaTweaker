package com.luatweaker.platform.content;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.content.ContentServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies that all JSON assets emitted by LuaAssetsPackFinder parse cleanly
 * without malformed syntax or unterminated string errors.
 */
class LuaAssetsPackFinderJsonTest {

    private static final String CRYSTAL_BLOCK_ID = "luatweaker:crystal_block";

    private static final class FakeDatapackSink implements IDatapackService {
        private final Map<String, String> files = new ConcurrentHashMap<>();

        @Override
        public void addJsonRecipe(String recipeId, String jsonContent) {
            files.put(recipeId, jsonContent);
        }

        @Override
        public void addLootTable(String path, String jsonContent) {
            files.put(path, jsonContent);
        }

        @Override
        public void addAdvancement(String path, String jsonContent) {
            files.put(path, jsonContent);
        }

        @Override
        public void addFunction(String path, String commands) {
            files.put(path, commands);
        }

        @Override
        public void addData(String relPath, String jsonContent) {
            files.put(relPath, jsonContent);
        }

        @Override
        public void addTag(String tagType, String tagId, List<String> values) {
        }

        @Override
        public Map<String, String> getVirtualFiles() {
            return files;
        }

        @Override
        public void clear() {
            files.clear();
        }
    }

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

        FakeDatapackSink datapack = new FakeDatapackSink();
        LuaAssetsPackFinder finder = new LuaAssetsPackFinder(new File("build/tmp/test-lua"), datapack, content);
        finder.injectVirtualAssetFiles();

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

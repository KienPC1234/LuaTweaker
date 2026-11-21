package com.luatweaker.content;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Runtime storage and virtual datapack files work end to end through Lua. */
class StorageDatapackTest extends ContentFixture {

    private static final String STORAGE_CHUNK = "storage_chunk";
    private static final String STORAGE_SCRIPT = """
            storage:set("test_key", "hello_world")
            storage:set("number_key", 42)
        """;
    private static final String DATAPACK_CHUNK = "datapack_chunk";
    private static final String DATAPACK_SCRIPT = """
            datapack:addJsonRecipe("custom_crafting", '{"type":"minecraft:crafting_shapeless"}')
        """;
    private static final String RECIPE_FILE = "data/luatweaker/recipe/custom_crafting.json";

    private void runStorageScript() throws IOException {
        engine.executeScript(scriptFile(STORAGE_SCRIPT), STORAGE_CHUNK);
    }

    private void runDatapackScript() throws IOException {
        engine.executeScript(scriptFile(DATAPACK_SCRIPT), DATAPACK_CHUNK);
    }

    @Test
    void storageKeepsString() throws IOException {
        runStorageScript();

        assertEquals("hello_world", storageService.get("test_key", null),
                "the stored string must round-trip");
    }

    @Test
    void storageKeepsNumber() throws IOException {
        runStorageScript();

        assertEquals(42, ((Number) storageService.get("number_key", null)).intValue(),
                "the stored number must round-trip");
    }

    @Test
    void datapackFileCount() throws IOException {
        runDatapackScript();

        assertEquals(1, datapackService.getVirtualFiles().size(),
                "exactly one virtual file must be registered");
    }

    @Test
    void datapackFileKey() throws IOException {
        runDatapackScript();

        assertTrue(datapackService.getVirtualFiles().containsKey(RECIPE_FILE),
                "the recipe must land at its datapack path");
    }

    @Test
    void clearStartsNonEmpty() {
        datapackService.addJsonRecipe("custom_crafting", "{}");
        datapackService.addData("data/mymod/recipe/stale.json", "{}");
        datapackService.addTag("block", "minecraft:mineable/pickaxe", List.of("mymod:test_block"));

        assertFalse(datapackService.getVirtualFiles().isEmpty(),
                "the pack must be non-empty before clearing");
    }

    @Test
    void clearWipesEverything() {
        datapackService.addJsonRecipe("custom_crafting", "{}");
        datapackService.addData("data/mymod/recipe/stale.json", "{}");
        datapackService.addTag("block", "minecraft:mineable/pickaxe", List.of("mymod:test_block"));
        datapackService.clear();

        assertTrue(datapackService.getVirtualFiles().isEmpty(),
                "a reload must start from a clean virtual pack so stale files disappear");
    }
}

package com.luatweaker.content;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class ContentBuilderTest {

    private CobaltLuaEngine engine;
    private ContentServiceImpl contentService;
    private StorageServiceImpl storageService;
    private DatapackServiceImpl datapackService;

    @BeforeEach
    public void setUp() {
        engine = new CobaltLuaEngine();
        contentService = new ContentServiceImpl();
        storageService = new StorageServiceImpl(new File("build/tmp/test_storage.json"));
        datapackService = new DatapackServiceImpl();

        ContentLuaBinding.registerBindings(engine, contentService, storageService, datapackService);
    }

    private File createTempScript(String content) throws IOException {
        File file = File.createTempFile("test_script_", ".lua");
        file.deleteOnExit();
        Files.writeString(file.toPath(), content);
        return file;
    }

    @Test
    public void testCreateItemFromLua() throws IOException {
        String script = """
            startup:createItem("custom_ruby", function(item)
                item:maxStackSize(16)
                    :rarity("EPIC")
                    :burnTime(400)
                    :displayName("Enchanted Ruby Gem")
            end)
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        assertEquals(1, contentService.getRegisteredItems().size());
        IItemBuilder builder = contentService.getRegisteredItems().iterator().next();
        assertEquals("custom_ruby", builder.getId());
        assertEquals(16, builder.getMaxStackSize());
        assertEquals("EPIC", builder.getRarity());
        assertEquals(400, builder.getBurnTime());
        assertEquals("Enchanted Ruby Gem", builder.getDisplayName());
    }

    @Test
    public void testCreateBlockFromLua() throws IOException {
        String script = """
            startup:createBlock("custom_ruby_block", function(block)
                block:hardness(3.0)
                     :resistance(12.0)
                     :lightLevel(10)
                     :soundType("STONE")
            end)
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        assertEquals(1, contentService.getRegisteredBlocks().size());
        IBlockBuilder builder = contentService.getRegisteredBlocks().iterator().next();
        assertEquals("custom_ruby_block", builder.getId());
        assertEquals(3.0f, builder.getHardness());
        assertEquals(12.0f, builder.getResistance());
        assertEquals(10, builder.getLightLevel());
        assertEquals("STONE", builder.getSoundType());
    }

    @Test
    public void testStorageFromLua() throws IOException {
        String script = """
            storage:set("test_key", "hello_world")
            storage:set("number_key", 42)
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        assertEquals("hello_world", storageService.get("test_key", null));
        assertEquals(42, ((Number) storageService.get("number_key", null)).intValue());
    }

    @Test
    public void testDatapackFromLua() throws IOException {
        String script = """
            datapack:addJsonRecipe("custom_crafting", '{"type":"minecraft:crafting_shapeless"}')
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        assertEquals(1, datapackService.getVirtualFiles().size());
        assertTrue(datapackService.getVirtualFiles().containsKey("data/luatweaker/recipe/custom_crafting.json"));
    }
}

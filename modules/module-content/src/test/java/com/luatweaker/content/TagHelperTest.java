package com.luatweaker.content;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tag helpers register virtual datapack tag files through both call forms. */
class TagHelperTest extends ContentFixture {

    private static final String TAG_CHUNK = "tag_helpers";
    private static final String TAG_SCRIPT =
            "tag.item('c:gems', {'luatweaker:custom_ruby'})\n" +
            "tag('block', 'minecraft:beacon_base_blocks', {'luatweaker:ruby_block'})";

    private Map<String, String> runTagScript() {
        engine.executeString(TAG_SCRIPT, TAG_CHUNK);
        return datapackService.getVirtualFiles();
    }

    @Test
    void scriptRunsWithoutError() {
        runTagScript();

        assertNull(engine.getAndClearLastExecutionError(),
                "the tag script must execute without errors");
    }

    @Test
    void itemTagRegistered() {
        Map<String, String> files = runTagScript();

        assertTrue(files.containsKey("data/c/tags/item/gems.json"),
                "tag.item dot call must register the item tag: " + files.keySet());
    }

    @Test
    void blockTagRegistered() {
        Map<String, String> files = runTagScript();

        assertTrue(files.containsKey("data/minecraft/tags/block/beacon_base_blocks.json"),
                "tag(...) call form must register the block tag: " + files.keySet());
    }

    @Test
    void itemTagContent() {
        Map<String, String> files = runTagScript();

        assertTrue(files.get("data/c/tags/item/gems.json").contains("luatweaker:custom_ruby"),
                "the item tag must list the custom ruby");
    }

    @Test
    void blockTagContent() {
        Map<String, String> files = runTagScript();

        assertTrue(files.get("data/minecraft/tags/block/beacon_base_blocks.json")
                .contains("luatweaker:ruby_block"),
                "the block tag must list the ruby block");
    }
}

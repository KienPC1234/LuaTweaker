package com.luatweaker.platform.content;

import com.luatweaker.content.ContentServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block-tag computation feeds the virtual datapack after every reload
 * (content files are regenerated from the SAME content service), so it must be
 * deterministic and complete.
 */
public class ContentTagMapperTest {

    @Test
    public void computesMineableAndLevelTags() {
        ContentServiceImpl content = new ContentServiceImpl();
        content.createBlock("my_ore", b -> {
            b.mineableWith("pickaxe");
            b.miningLevel(2);
        });
        content.createBlock("my_iron_stairs", b -> {
            b.mineableWith("pickaxe");
        });

        Map<String, List<String>> tags = ContentTagMapper.computeBlockTags(content);

        assertEquals(List.of("luatweaker:my_ore", "luatweaker:my_iron_stairs"),
                tags.get("tags/block/mineable/pickaxe.json"));
        assertEquals(List.of("luatweaker:my_ore"), tags.get("tags/block/needs_iron_tool.json"));
        assertTrue(tags.containsKey("tags/block/stairs.json"));
    }

    @Test
    public void defaultsNamespaceWhenMissing() {
        ContentServiceImpl content = new ContentServiceImpl();
        content.createBlock("ruby_ore", b -> b.mineableWith("axe"));

        Map<String, List<String>> tags = ContentTagMapper.computeBlockTags(content);
        assertEquals(List.of("luatweaker:ruby_ore"), tags.get("tags/block/mineable/axe.json"));
    }

    @Test
    public void wallAndSlabSuffixesProduceShapeTags() {
        ContentServiceImpl content = new ContentServiceImpl();
        content.createBlock("my_wall", b -> {});
        content.createBlock("my_slab", b -> {});

        Map<String, List<String>> tags = ContentTagMapper.computeBlockTags(content);
        assertTrue(tags.containsKey("tags/block/walls.json"));
        assertTrue(tags.containsKey("tags/block/slabs.json"));
    }
}

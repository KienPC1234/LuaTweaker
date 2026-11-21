package com.luatweaker.platform.content;

import com.luatweaker.content.ContentServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block-tag computation feeds the virtual datapack after every reload
 * (content files are regenerated from the SAME content service), so it must be
 * deterministic and complete.
 */
class ContentTagMapperTest {

    private static Map<String, List<String>> computeTags(ContentServiceImpl content) {
        return ContentTagMapper.computeBlockTags(content);
    }

    private static ContentServiceImpl oreAndStairsContent() {
        ContentServiceImpl content = new ContentServiceImpl();
        content.createBlock("my_ore", block -> {
            block.mineableWith("pickaxe");
            block.miningLevel(2);
        });
        content.createBlock("my_iron_stairs", block -> {
            block.mineableWith("pickaxe");
        });
        return content;
    }

    @Test
    void pickaxeTagListsBothPickaxeBlocks() {
        // The mapper iterates a registry whose iteration order is not guaranteed,
        // so compare the tag contents as an unordered set.
        assertEquals(Set.of("luatweaker:my_ore", "luatweaker:my_iron_stairs"),
                new HashSet<>(computeTags(oreAndStairsContent()).get("tags/block/mineable/pickaxe.json")),
                "both pickaxe-mineable blocks must land in the pickaxe tag");
    }

    @Test
    void ironToolTagListsOnlyLevelTwoOre() {
        assertEquals(List.of("luatweaker:my_ore"),
                computeTags(oreAndStairsContent()).get("tags/block/needs_iron_tool.json"),
                "only the mining-level-2 ore must require an iron tool");
    }

    @Test
    void stairsSuffixProducesStairsTag() {
        assertTrue(computeTags(oreAndStairsContent()).containsKey("tags/block/stairs.json"),
                "the _stairs suffix must produce a stairs tag for stair placement rules");
    }

    @Test
    void axeTagUsesDefaultNamespace() {
        ContentServiceImpl content = new ContentServiceImpl();
        content.createBlock("ruby_ore", block -> block.mineableWith("axe"));

        assertEquals(List.of("luatweaker:ruby_ore"),
                computeTags(content).get("tags/block/mineable/axe.json"),
                "blocks without an explicit namespace must default to luatweaker");
    }

    @Test
    void wallSuffixProducesWallsTag() {
        ContentServiceImpl content = new ContentServiceImpl();
        content.createBlock("my_wall", block -> {
        });

        assertTrue(computeTags(content).containsKey("tags/block/walls.json"),
                "the _wall suffix must produce a walls tag for wall placement rules");
    }

    @Test
    void slabSuffixProducesSlabsTag() {
        ContentServiceImpl content = new ContentServiceImpl();
        content.createBlock("my_slab", block -> {
        });

        assertTrue(computeTags(content).containsKey("tags/block/slabs.json"),
                "the _slab suffix must produce a slabs tag for slab placement rules");
    }
}

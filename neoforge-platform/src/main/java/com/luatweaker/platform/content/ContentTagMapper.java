package com.luatweaker.platform.content;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IContentService;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure computation of the block tags a registered Lua block belongs to
 * (mineable tool + required mining level + stairs/walls/slabs).
 * MC-free so it is unit-testable; the virtual pack finder writes the result
 * into the datapack map.
 */
final class ContentTagMapper {

    private ContentTagMapper() {}

    /** tag path (e.g. "tags/block/mineable/pickaxe.json") -> block ids. */
    @NotNull
    static Map<String, List<String>> computeBlockTags(@NotNull IContentService contentService) {
        Map<String, List<String>> tagMap = new LinkedHashMap<>();
        for (IBlockBuilder b : contentService.getRegisteredBlocks()) {
            String[] parts = parseId(b.getId());
            String fullId = parts[0] + ":" + parts[1];

            if (b.getMineableWith() != null) {
                String tool = b.getMineableWith().toLowerCase();
                tagMap.computeIfAbsent("tags/block/mineable/" + tool + ".json", k -> new java.util.ArrayList<>()).add(fullId);
            }

            if (b.getMiningLevel() > 0) {
                String levelTag = switch (b.getMiningLevel()) {
                    case 1 -> "tags/block/needs_stone_tool.json";
                    case 2 -> "tags/block/needs_iron_tool.json";
                    case 3 -> "tags/block/needs_diamond_tool.json";
                    case 4 -> "tags/block/needs_netherite_tool.json";
                    default -> "tags/block/needs_stone_tool.json";
                };
                tagMap.computeIfAbsent(levelTag, k -> new java.util.ArrayList<>()).add(fullId);
            }

            if (parts[1].endsWith("_wall")) {
                tagMap.computeIfAbsent("tags/block/walls.json", k -> new java.util.ArrayList<>()).add(fullId);
            }
            if (parts[1].endsWith("_stairs")) {
                tagMap.computeIfAbsent("tags/block/stairs.json", k -> new java.util.ArrayList<>()).add(fullId);
            }
            if (parts[1].endsWith("_slab")) {
                tagMap.computeIfAbsent("tags/block/slabs.json", k -> new java.util.ArrayList<>()).add(fullId);
            }
        }
        return tagMap;
    }

    private static String[] parseId(String id) {
        if (id == null) return new String[]{"luatweaker", "unknown"};
        int colon = id.indexOf(':');
        if (colon > 0) return new String[]{id.substring(0, colon), id.substring(colon + 1)};
        return new String[]{"luatweaker", id};
    }
}

package com.luatweaker.common.content;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IContentService;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure computation of the block tags a registered Lua block belongs to
 * (mineable tool + required mining level + stairs/walls/slabs).
 * MC-free so it is unit-testable; the virtual pack finder writes the result
 * into the datapack map.
 */
final class ContentTagMapper {

    private static final String TAG_ROOT = "tags/block/";
    private static final String DEFAULT_NAMESPACE = "luatweaker";

    private ContentTagMapper() {}

    /** tag path (e.g. "tags/block/mineable/pickaxe.json") -> block ids. */
    @NotNull
    static Map<String, List<String>> computeBlockTags(@NotNull IContentService contentService) {
        var tagMap = new LinkedHashMap<String, List<String>>();
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            addTagsFor(tagMap, builder);
        }
        return tagMap;
    }

    private static final java.util.Set<String> VALID_TOOLS = java.util.Set.of(
            "PICKAXE", "AXE", "SHOVEL", "HOE");

    private static void addTagsFor(@NotNull Map<String, List<String>> tagMap, @NotNull IBlockBuilder builder) {
        String[] parts = parseId(builder.getId());
        String fullId = parts[0] + ":" + parts[1];
        if (builder.getMineableWith() != null) {
            String tool = builder.getMineableWith().toUpperCase(Locale.ROOT);
            if (VALID_TOOLS.contains(tool)) {
                addTag(tagMap, TAG_ROOT + "mineable/" + tool.toLowerCase(Locale.ROOT) + ".json", fullId);
            } else {
                com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                        "Block '" + fullId + "' has unknown mineableWith tool '" + builder.getMineableWith()
                        + "' (use 'PICKAXE', 'AXE', 'SHOVEL' or 'HOE'): skipping the mineable tag.");
            }
        }
        if (builder.getMiningLevel() > 0) {
            addTag(tagMap, levelTag(builder.getMiningLevel()), fullId);
        }
        if (parts[1].endsWith("_wall")) {
            addTag(tagMap, TAG_ROOT + "walls.json", fullId);
        }
        if (parts[1].endsWith("_stairs")) {
            addTag(tagMap, TAG_ROOT + "stairs.json", fullId);
        }
        if (parts[1].endsWith("_slab")) {
            addTag(tagMap, TAG_ROOT + "slabs.json", fullId);
        }
    }

    private static void addTag(@NotNull Map<String, List<String>> tagMap, @NotNull String key, @NotNull String id) {
        tagMap.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(id);
    }

    private static final int NETHERITE_MINING_LEVEL = 4;

    @NotNull
    private static String levelTag(int miningLevel) {
        if (miningLevel >= NETHERITE_MINING_LEVEL) {
            return TAG_ROOT + "needs_netherite_tool.json";
        }
        return switch (miningLevel) {
            case 2 -> TAG_ROOT + "needs_iron_tool.json";
            case 3 -> TAG_ROOT + "needs_diamond_tool.json";
            default -> TAG_ROOT + "needs_stone_tool.json";
        };
    }

    private static String[] parseId(String id) {
        if (id == null) {
            return new String[]{DEFAULT_NAMESPACE, "unknown"};
        }
        int colon = id.indexOf(':');
        if (colon > 0) {
            return new String[]{id.substring(0, colon), id.substring(colon + 1)};
        }
        return new String[]{DEFAULT_NAMESPACE, id};
    }
}

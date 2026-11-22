package com.luatweaker.common.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IDatapackService;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Writes mining-tool and vanilla-category block tags
 * ({@code data/minecraft/tags/block/*.json}) for every registered Lua block.
 */
final class ContentTagFiles {

    private ContentTagFiles() {
    }

    static void rebuild(@NotNull IContentService contentService, @NotNull IDatapackService datapackService) {
        for (Map.Entry<String, List<String>> entry
                : ContentTagMapper.computeBlockTags(contentService).entrySet()) {
            injectBlockTag(datapackService, entry);
        }
    }

    private static void injectBlockTag(@NotNull IDatapackService datapackService,
                                       @NotNull Map.Entry<String, List<String>> entry) {
        String virtualKey = "data/minecraft/" + entry.getKey();
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        json.add("values", mergeTagValues(datapackService, virtualKey, entry.getValue()));
        datapackService.addData(virtualKey, ContentAssetPaths.GSON.toJson(json));
    }

    private static JsonArray mergeTagValues(@NotNull IDatapackService datapackService,
                                            @NotNull String virtualKey, @NotNull List<String> elements) {
        JsonArray values = existingTagValues(datapackService, virtualKey);
        for (String element : elements) {
            if (!tagValueContains(values, element)) {
                values.add(element);
            }
        }
        return values;
    }

    private static JsonArray existingTagValues(@NotNull IDatapackService datapackService,
                                               @NotNull String virtualKey) {
        String existing = datapackService.getVirtualFiles().get(virtualKey);
        if (existing == null) {
            return new JsonArray();
        }
        return ContentAssetPaths.GSON.fromJson(existing, JsonObject.class).getAsJsonArray("values");
    }

    private static boolean tagValueContains(@NotNull JsonArray values, @NotNull String element) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).getAsString().equals(element)) {
                return true;
            }
        }
        return false;
    }
}

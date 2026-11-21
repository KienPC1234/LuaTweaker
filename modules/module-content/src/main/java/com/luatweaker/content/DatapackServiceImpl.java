package com.luatweaker.content;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luatweaker.api.content.IDatapackService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory (virtual) DataPack service.
 *
 * <p>Stores all Lua-registered data in a {@link ConcurrentHashMap} keyed by the canonical
 * "virtual path" ({@code data/<namespace>/<path>}). No files are ever written to disk.
 *
 * <p>The virtual pack ({@link com.luatweaker.platform.content.LuaTweakerVirtualPackResources})
 * reads from this map at the exact moment Minecraft asks for a resource.
 */
public class DatapackServiceImpl implements IDatapackService {

    private static final String DATA_PREFIX = "data/";
    private static final String JSON_SUFFIX = ".json";
    private static final String TAG_VALUES_KEY = "values";

    /**
     * Key format: {@code data/<namespace>/<path>}
     * Example: {@code data/minecraft/tags/block/mineable/pickaxe.json}
     */
    private final Map<String, String> virtualFiles = new ConcurrentHashMap<>();

    @Override
    public void addJsonRecipe(String recipeId, String jsonContent) {
        String[] parts = parseRL(recipeId);
        virtualFiles.put(DATA_PREFIX + parts[0] + "/recipe/" + parts[1] + JSON_SUFFIX, jsonContent);
    }

    @Override
    public void addLootTable(String path, String jsonContent) {
        String[] parts = parseRL(path);
        virtualFiles.put(DATA_PREFIX + parts[0] + "/loot_table/" + parts[1] + JSON_SUFFIX, jsonContent);
    }

    @Override
    public void addAdvancement(String path, String jsonContent) {
        String[] parts = parseRL(path);
        virtualFiles.put(DATA_PREFIX + parts[0] + "/advancement/" + parts[1] + JSON_SUFFIX, jsonContent);
    }

    @Override
    public void addFunction(String path, String commands) {
        String[] parts = parseRL(path);
        virtualFiles.put(DATA_PREFIX + parts[0] + "/function/" + parts[1] + ".mcfunction", commands);
    }

    @Override
    public void addData(String relPath, String jsonContent) {
        String key = (relPath.startsWith(DATA_PREFIX) || relPath.startsWith("assets/")) ? relPath : DATA_PREFIX + relPath;
        virtualFiles.put(key, jsonContent);
    }

    @Override
    public void addTag(String tagType, String tagId, List<String> values) {
        if (tagType == null || tagId == null || values == null || values.isEmpty()) {
            return;
        }

        String[] parts = parseRL(tagId);
        // e.g. data/luatweaker/tags/item/ruby_items.json
        String key = DATA_PREFIX + parts[0] + "/tags/" + tagType + "/" + parts[1] + JSON_SUFFIX;

        // Merge with existing virtual tag if already present
        JsonObject json = readExistingTag(key);
        JsonArray arr = json.has(TAG_VALUES_KEY) ? json.getAsJsonArray(TAG_VALUES_KEY) : new JsonArray();
        appendMissingValues(arr, values);
        json.add(TAG_VALUES_KEY, arr);

        virtualFiles.put(key, new GsonBuilder().setPrettyPrinting().create().toJson(json));
    }

    private JsonObject readExistingTag(String key) {
        if (virtualFiles.containsKey(key)) {
            return JsonParser.parseString(virtualFiles.get(key)).getAsJsonObject();
        }
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        return json;
    }

    private static void appendMissingValues(JsonArray arr, List<String> values) {
        for (String v : values) {
            boolean found = false;
            for (var elem : arr) {
                if (elem.getAsString().equals(v)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                arr.add(v);
            }
        }
    }

    @Override
    public Map<String, String> getVirtualFiles() {
        return Collections.unmodifiableMap(virtualFiles);
    }

    @Override
    public void clear() {
        virtualFiles.clear();
    }

    /** Parses "namespace:path" → ["namespace", "path"]; defaults namespace to "luatweaker". */
    private String[] parseRL(String raw) {
        if (raw == null) {
            return new String[]{"luatweaker", "unknown"};
        }
        int colon = raw.indexOf(':');
        if (colon > 0) {
            return new String[]{raw.substring(0, colon), raw.substring(colon + 1)};
        }
        return new String[]{"luatweaker", raw};
    }
}

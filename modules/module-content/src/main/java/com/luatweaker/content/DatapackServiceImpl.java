package com.luatweaker.content;

import com.luatweaker.api.content.IDatapackService;

import java.util.Collections;
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

    /**
     * Key format: {@code data/<namespace>/<path>}
     * Example: {@code data/minecraft/tags/block/mineable/pickaxe.json}
     */
    private final Map<String, String> virtualFiles = new ConcurrentHashMap<>();

    @Override
    public void addJsonRecipe(String recipeId, String jsonContent) {
        String[] parts = parseRL(recipeId);
        virtualFiles.put("data/" + parts[0] + "/recipe/" + parts[1] + ".json", jsonContent);
    }

    @Override
    public void addLootTable(String path, String jsonContent) {
        String[] parts = parseRL(path);
        virtualFiles.put("data/" + parts[0] + "/loot_table/" + parts[1] + ".json", jsonContent);
    }

    @Override
    public void addAdvancement(String path, String jsonContent) {
        String[] parts = parseRL(path);
        virtualFiles.put("data/" + parts[0] + "/advancement/" + parts[1] + ".json", jsonContent);
    }

    @Override
    public void addFunction(String path, String commands) {
        String[] parts = parseRL(path);
        virtualFiles.put("data/" + parts[0] + "/function/" + parts[1] + ".mcfunction", commands);
    }

    @Override
    public void addData(String relPath, String jsonContent) {
        String key = (relPath.startsWith("data/") || relPath.startsWith("assets/")) ? relPath : "data/" + relPath;
        virtualFiles.put(key, jsonContent);
    }

    @Override
    public void addTag(String tagType, String tagId, java.util.List<String> values) {
        if (tagType == null || tagId == null || values == null || values.isEmpty()) return;

        String[] parts = parseRL(tagId);
        // e.g. data/luatweaker/tags/item/ruby_items.json
        String key = "data/" + parts[0] + "/tags/" + tagType + "/" + parts[1] + ".json";

        // Merge with existing virtual tag if already present
        com.google.gson.JsonObject json;
        com.google.gson.JsonArray arr;
        if (virtualFiles.containsKey(key)) {
            json = com.google.gson.JsonParser.parseString(virtualFiles.get(key)).getAsJsonObject();
            arr = json.has("values") ? json.getAsJsonArray("values") : new com.google.gson.JsonArray();
        } else {
            json = new com.google.gson.JsonObject();
            json.addProperty("replace", false);
            arr = new com.google.gson.JsonArray();
            json.add("values", arr);
        }

        for (String v : values) {
            boolean found = false;
            for (var elem : arr) if (elem.getAsString().equals(v)) { found = true; break; }
            if (!found) arr.add(v);
        }

        virtualFiles.put(key, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
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
        if (raw == null) return new String[]{"luatweaker", "unknown"};
        int colon = raw.indexOf(':');
        if (colon > 0) return new String[]{raw.substring(0, colon), raw.substring(colon + 1)};
        return new String[]{"luatweaker", raw};
    }
}

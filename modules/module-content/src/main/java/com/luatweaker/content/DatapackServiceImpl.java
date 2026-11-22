package com.luatweaker.content;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luatweaker.api.content.IDatapackService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        virtualFiles.put(canonicalKey(relPath), jsonContent);
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
    public boolean hasData(@NotNull String relPath) {
        return virtualFiles.containsKey(canonicalKey(relPath));
    }

    @Override
    @Nullable
    public String getData(@NotNull String relPath) {
        return virtualFiles.get(canonicalKey(relPath));
    }

    @Override
    public boolean removeData(@NotNull String relPath) {
        return virtualFiles.remove(canonicalKey(relPath)) != null;
    }

    @Override
    @NotNull
    public List<String> listData(@NotNull String prefix) {
        String normalized = stripSlashes(prefix == null ? "" : prefix);
        Set<String> sorted = new TreeSet<>();
        for (String key : virtualFiles.keySet()) {
            if (normalized.isEmpty() || key.startsWith(normalized)) {
                sorted.add(key);
            }
        }
        return new ArrayList<>(sorted);
    }

    @Override
    @NotNull
    public List<String> listNamespaces(@NotNull String packDir) {
        String dir = stripSlashes(packDir == null ? "" : packDir);
        while (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        Set<String> namespaces = new TreeSet<>();
        if (!"data".equals(dir) && !"assets".equals(dir)) {
            return new ArrayList<>(namespaces);
        }
        String prefix = dir + "/";
        for (String key : virtualFiles.keySet()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String rest = key.substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                namespaces.add(rest.substring(0, slash));
            }
        }
        return new ArrayList<>(namespaces);
    }

    @Override
    public void clear() {
        virtualFiles.clear();
    }

    /** Canonical virtual key ({@code data/<ns>/<path>} or {@code assets/<ns>/<path>}). */
    private static String canonicalKey(String relPath) {
        if (relPath == null || relPath.isBlank()) {
            throw new IllegalArgumentException("relPath must not be blank (e.g. 'mymod/recipe/iron.json')");
        }
        String normalized = stripSlashes(relPath);
        if (normalized.isEmpty() || normalized.contains("..")) {
            throw new IllegalArgumentException("relPath must not contain path traversal, got: " + relPath);
        }
        if (normalized.startsWith(DATA_PREFIX) || normalized.startsWith("assets/")) {
            return normalized;
        }
        return DATA_PREFIX + normalized;
    }

    /** Trims whitespace, normalizes separators, and drops leading slashes. */
    private static String stripSlashes(String raw) {
        String normalized = raw.strip().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
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

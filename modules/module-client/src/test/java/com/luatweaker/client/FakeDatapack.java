package com.luatweaker.client;

import com.luatweaker.api.content.IDatapackService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link IDatapackService} double: stores the generated files by path so tests can assert
 * exactly what the shader service published.
 */
final class FakeDatapack implements IDatapackService {

    final Map<String, String> files = new ConcurrentHashMap<>();

    @Override
    public void addJsonRecipe(String id, String json) {
        files.put("recipe:" + id, json);
    }

    @Override
    public void addLootTable(String path, String json) {
        files.put("loot:" + path, json);
    }

    @Override
    public void addAdvancement(String path, String json) {
        files.put("adv:" + path, json);
    }

    @Override
    public void addFunction(String path, String commands) {
        files.put("fn:" + path, commands);
    }

    @Override
    public void addData(String relPath, String json) {
        files.put(relPath, json);
    }

    @Override
    public void addTag(String type, String id, List<String> values) {
        files.put("tag:" + id, id);
    }

    @Override
    public Map<String, String> getVirtualFiles() {
        return Map.copyOf(files);
    }

    @Override
    public void clear() {
        files.clear();
    }
}

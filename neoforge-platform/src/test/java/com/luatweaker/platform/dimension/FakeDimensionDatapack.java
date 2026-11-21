package com.luatweaker.platform.dimension;

import com.luatweaker.api.content.IDatapackService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory datapack sink for the dimension materialization tests. */
final class FakeDimensionDatapack implements IDatapackService {

    final Map<String, String> files = new ConcurrentHashMap<>();

    @Override
    public void addJsonRecipe(String recipeId, String jsonContent) {
        files.put("recipe/" + recipeId, jsonContent);
    }

    @Override
    public void addLootTable(String path, String jsonContent) {
        files.put("loot/" + path, jsonContent);
    }

    @Override
    public void addAdvancement(String path, String jsonContent) {
        files.put("adv/" + path, jsonContent);
    }

    @Override
    public void addFunction(String path, String commands) {
        files.put("fn/" + path, commands);
    }

    @Override
    public void addData(String relPath, String jsonContent) {
        files.put(relPath, jsonContent);
    }

    @Override
    public void addTag(String tagType, String tagId, List<String> values) {
    }

    @Override
    public Map<String, String> getVirtualFiles() {
        return files;
    }

    @Override
    public void clear() {
        files.clear();
    }
}

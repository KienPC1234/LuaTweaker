package com.luatweaker.dimension;

import com.luatweaker.api.content.IDatapackService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory datapack double for biome tests. Only {@code addData} stores content;
 * every other sink is a no-op because biome tests never exercise it.
 */
final class BiomesFakeDatapack implements IDatapackService {
    final Map<String, String> files = new ConcurrentHashMap<>();

    @Override
    public void addJsonRecipe(String recipeId, String jsonContent) {
        // Not needed for biome tests.
    }

    @Override
    public void addLootTable(String path, String jsonContent) {
        // Not needed for biome tests.
    }

    @Override
    public void addAdvancement(String path, String jsonContent) {
        // Not needed for biome tests.
    }

    @Override
    public void addFunction(String path, String commands) {
        // Not needed for biome tests.
    }

    @Override
    public void addData(String relPath, String jsonContent) {
        files.put(relPath, jsonContent);
    }

    @Override
    public void addTag(String tagType, String tagId, List<String> values) {
        // Not needed for biome tests.
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

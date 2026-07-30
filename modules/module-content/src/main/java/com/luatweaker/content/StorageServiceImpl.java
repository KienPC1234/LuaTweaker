package com.luatweaker.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.luatweaker.api.content.IStorageService;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StorageServiceImpl implements IStorageService {

    private final File storageFile;
    private final Map<String, Object> storageData = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public StorageServiceImpl(File storageFile) {
        this.storageFile = storageFile;
        load();
    }

    @Override
    public synchronized void set(String key, Object value) {
        if (value == null) {
            storageData.remove(key);
        } else {
            storageData.put(key, value);
        }
        save();
    }

    @Override
    public Object get(String key, Object defaultVal) {
        return storageData.getOrDefault(key, defaultVal);
    }

    @Override
    public synchronized void save() {
        try {
            if (!storageFile.getParentFile().exists()) {
                storageFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(storageFile)) {
                gson.toJson(storageData, writer);
            }
        } catch (Exception e) {
            System.err.println("LuaTweaker: Failed to save storage.json: " + e.getMessage());
        }
    }

    @Override
    public synchronized void load() {
        if (!storageFile.exists()) return;
        try (FileReader reader = new FileReader(storageFile)) {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                storageData.clear();
                storageData.putAll(loaded);
            }
        } catch (Exception e) {
            System.err.println("LuaTweaker: Failed to load storage.json: " + e.getMessage());
        }
    }
}

package com.luatweaker.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.luatweaker.api.content.IStorageService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class StorageServiceImpl implements IStorageService {

    private final File storageFile;
    private final Map<String, Object> storageData = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ReentrantLock ioLock = new ReentrantLock();

    public StorageServiceImpl(File storageFile) {
        this.storageFile = storageFile;
        readFromDisk();
    }

    @Override
    public void set(String key, Object value) {
        ioLock.lock();
        try {
            if (value == null) {
                storageData.remove(key);
            } else {
                storageData.put(key, value);
            }
            writeToDisk();
        } finally {
            ioLock.unlock();
        }
    }

    @Override
    public Object get(String key, Object defaultVal) {
        return storageData.getOrDefault(key, defaultVal);
    }

    @Override
    public void save() {
        ioLock.lock();
        try {
            writeToDisk();
        } finally {
            ioLock.unlock();
        }
    }

    @Override
    public void load() {
        ioLock.lock();
        try {
            readFromDisk();
        } finally {
            ioLock.unlock();
        }
    }

    private void writeToDisk() {
        try {
            if (!storageFile.getParentFile().exists()) {
                storageFile.getParentFile().mkdirs();
            }
            try (Writer writer = Files.newBufferedWriter(storageFile.toPath(), StandardCharsets.UTF_8)) {
                gson.toJson(storageData, writer);
            }
        } catch (IOException | JsonParseException e) {
            LuaTweakerLog.get().error(LogStage.STORAGE, "Failed to save storage.json: " + e.getMessage());
        }
    }

    private void readFromDisk() {
        if (!storageFile.exists()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(storageFile.toPath(), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                storageData.clear();
                storageData.putAll(loaded);
            }
        } catch (IOException | JsonParseException e) {
            LuaTweakerLog.get().error(LogStage.STORAGE, "Failed to load storage.json: " + e.getMessage());
        }
    }
}

package com.luatweaker.storage;

import com.luatweaker.api.storage.IRobloxStorageService;
import com.luatweaker.api.vm.ILuaEngine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * World-scoped store: memory-first concurrent map with debounced disk flush.
 */
final class WorldDataStore implements IRobloxStorageService.IDataStore {

    private final ILuaEngine engine;
    private final Map<String, Object> worldStore;
    private final AtomicBoolean worldDirty;
    private final Runnable scheduleFlush;
    private final Runnable flushNow;

    WorldDataStore(ILuaEngine engine, Map<String, Object> worldStore,
                   AtomicBoolean worldDirty, Runnable scheduleFlush, Runnable flushNow) {
        this.engine = engine;
        this.worldStore = worldStore;
        this.worldDirty = worldDirty;
        this.scheduleFlush = scheduleFlush;
        this.flushNow = flushNow;
    }

    @Override
    public Object GetAsync(String key) {
        return StorageBsonCodec.convertToJavaObject(engine, worldStore.get(key));
    }

    @Override
    public void SetAsync(String key, Object value) {
        if (value == null) {
            worldStore.remove(key);
        } else {
            worldStore.put(key, StorageValueCodec.toStorableValue(key, value));
        }
        worldDirty.set(true);
        scheduleFlush.run();
    }

    @Override
    public boolean RemoveAsync(String key) {
        boolean existed = worldStore.remove(key) != null;
        if (existed) {
            worldDirty.set(true);
            scheduleFlush.run();
        }
        return existed;
    }

    @Override
    public List<String> GetKeys() {
        return StorageValueCodec.sortedKeys(worldStore.keySet());
    }

    @Override
    public double IncrementAsync(String key, double delta) {
        double[] updated = new double[1];
        worldStore.compute(key, (k, existing) -> {
            updated[0] = StorageValueCodec.requireNumber(k, existing) + delta;
            return updated[0];
        });
        worldDirty.set(true);
        scheduleFlush.run();
        return updated[0];
    }

    @Override
    public void Flush() {
        flushNow.run();
    }
}

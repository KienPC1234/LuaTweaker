package com.luatweaker.storage;

import com.luatweaker.api.storage.IRobloxStorageService;
import com.luatweaker.api.vm.ILuaEngine;

import java.util.List;
import java.util.Map;

/**
 * Session-scoped store: memory only, never touches disk.
 */
final class SessionDataStore implements IRobloxStorageService.IDataStore {

    private final ILuaEngine engine;
    private final Map<String, Object> sessionStore;

    SessionDataStore(ILuaEngine engine, Map<String, Object> sessionStore) {
        this.engine = engine;
        this.sessionStore = sessionStore;
    }

    @Override
    public Object GetAsync(String key) {
        return StorageBsonCodec.convertToJavaObject(engine, sessionStore.get(key));
    }

    @Override
    public void SetAsync(String key, Object value) {
        if (value == null) {
            sessionStore.remove(key);
        } else {
            sessionStore.put(key, StorageValueCodec.toStorableValue(key, value));
        }
    }

    @Override
    public boolean RemoveAsync(String key) {
        return sessionStore.remove(key) != null;
    }

    @Override
    public List<String> GetKeys() {
        return StorageValueCodec.sortedKeys(sessionStore.keySet());
    }

    @Override
    public double IncrementAsync(String key, double delta) {
        double[] updated = new double[1];
        sessionStore.compute(key, (k, existing) -> {
            updated[0] = StorageValueCodec.requireNumber(k, existing) + delta;
            return updated[0];
        });
        return updated[0];
    }

    @Override
    public void Flush() {
        // In-memory only; no disk I/O required.
    }
}

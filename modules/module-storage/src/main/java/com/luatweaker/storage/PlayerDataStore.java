package com.luatweaker.storage;

import com.luatweaker.api.storage.IRobloxStorageService;
import com.luatweaker.api.vm.ILuaEngine;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-player store: copy-on-write player records with a subdocument cache, so
 * only dirty players are re-encoded to BSON.
 */
final class PlayerDataStore implements IRobloxStorageService.IDataStore {

    private final ILuaEngine engine;
    private final String playerUuid;
    private final Map<String, Map<String, Object>> playerStore;
    private final Map<String, Map<String, Object>> playerDocCache;
    private final AtomicBoolean playerDirty;
    private final Runnable scheduleFlush;
    private final Runnable flushNow;

    PlayerDataStore(ILuaEngine engine, String playerUuid,
                    Map<String, Map<String, Object>> playerStore,
                    Map<String, Map<String, Object>> playerDocCache,
                    AtomicBoolean playerDirty, Runnable scheduleFlush, Runnable flushNow) {
        this.engine = engine;
        this.playerUuid = playerUuid;
        this.playerStore = playerStore;
        this.playerDocCache = playerDocCache;
        this.playerDirty = playerDirty;
        this.scheduleFlush = scheduleFlush;
        this.flushNow = flushNow;
    }

    @Override
    public Object GetAsync(String key) {
        Map<String, Object> player = playerStore.get(playerUuid);
        return player == null ? null : StorageBsonCodec.convertToJavaObject(engine, player.get(key));
    }

    @Override
    public void SetAsync(String key, Object value) {
        Object stored = value == null ? null : StorageValueCodec.toStorableValue(key, value);
        playerStore.compute(playerUuid, (uuid, existing) -> {
            Map<String, Object> next = existing == null
                    ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(existing);
            if (stored == null) {
                next.remove(key);
            } else {
                next.put(key, stored);
            }
            return next;
        });
        playerDocCache.remove(playerUuid);
        playerDirty.set(true);
        scheduleFlush.run();
    }

    @Override
    public boolean RemoveAsync(String key) {
        boolean[] existed = new boolean[1];
        playerStore.compute(playerUuid, (uuid, existing) -> {
            if (existing == null || !existing.containsKey(key)) {
                return existing;
            }
            existed[0] = true;
            Map<String, Object> next = new ConcurrentHashMap<>(existing);
            next.remove(key);
            return next;
        });
        if (existed[0]) {
            playerDocCache.remove(playerUuid);
            playerDirty.set(true);
            scheduleFlush.run();
        }
        return existed[0];
    }

    @Override
    public List<String> GetKeys() {
        Map<String, Object> player = playerStore.get(playerUuid);
        return StorageValueCodec.sortedKeys(
                player == null ? Collections.emptySet() : player.keySet());
    }

    @Override
    public double IncrementAsync(String key, double delta) {
        double[] updated = new double[1];
        playerStore.compute(playerUuid, (uuid, existing) -> {
            Map<String, Object> next = existing == null
                    ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(existing);
            updated[0] = StorageValueCodec.requireNumber(key, next.get(key)) + delta;
            next.put(key, updated[0]);
            return next;
        });
        playerDocCache.remove(playerUuid);
        playerDirty.set(true);
        scheduleFlush.run();
        return updated[0];
    }

    @Override
    public void Flush() {
        flushNow.run();
    }
}

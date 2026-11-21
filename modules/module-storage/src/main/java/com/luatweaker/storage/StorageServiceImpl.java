package com.luatweaker.storage;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.storage.IRobloxStorageService;
import com.luatweaker.api.vm.ILuaEngine;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BSON-backed world/player/session storage.
 *
 * <p>Memory-first concurrent maps serve gameplay reads and writes; a debounced daemon
 * flush ({@link StorageFlushScheduler}) persists them atomically. Disk layout and BSON
 * encoding live in {@link StorageDiskIO} and {@link StorageBsonCodec}; value validation
 * in {@link StorageValueCodec}.</p>
 */
public class StorageServiceImpl implements IRobloxStorageService, AutoCloseable {
    private static final long DEFAULT_DEBOUNCE_MS = 100L;

    private static final Set<StorageServiceImpl> ACTIVE_INSTANCES = ConcurrentHashMap.newKeySet();

    private final ILuaEngine engine;
    private final ReentrantLock initLock = new ReentrantLock();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private final Map<String, Object> sessionStore = new ConcurrentHashMap<>();
    private final Map<String, Object> worldStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> playerStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> playerDocCache = new ConcurrentHashMap<>();

    private final AtomicBoolean worldDirty = new AtomicBoolean(false);
    private final AtomicBoolean playerDirty = new AtomicBoolean(false);

    private final StorageDiskIO diskIO;
    private final StorageFlushScheduler scheduler;

    private final IDataStore worldDataStore;
    private final IDataStore sessionDataStore;
    private final Map<String, IDataStore> playerDataStores = new ConcurrentHashMap<>();

    public StorageServiceImpl(ILuaEngine engine) {
        this(engine, DEFAULT_DEBOUNCE_MS);
    }

    public StorageServiceImpl(ILuaEngine engine, long debounceMs) {
        this.engine = engine;

        this.diskIO = new StorageDiskIO(worldStore, playerStore, playerDocCache, worldDirty, playerDirty);
        this.scheduler = new StorageFlushScheduler(diskIO, Math.max(0, debounceMs), worldDirty, playerDirty);

        this.worldDataStore = new WorldDataStore(engine, worldStore, worldDirty,
                scheduler::scheduleFlush, scheduler::flushNow);
        this.sessionDataStore = new SessionDataStore(engine, sessionStore);

        ACTIVE_INSTANCES.add(this);
    }

    /**
     * Flushes all active storage instances across the JVM.
     * Called before any storage instance reads from disk to guarantee data consistency.
     */
    public static void flushAllActive() {
        ACTIVE_INSTANCES.forEach(StorageServiceImpl::flushNow);
    }

    private void flushNow() {
        scheduler.flushNow();
    }

    /**
     * Loads the documents on first use instead of in the constructor. The platform resolves a
     * different directory before and after the server starts; deferring the load makes the first
     * real storage access use the per-world directory.
     */
    private void ensureInitialized() {
        if (initialized.get()) {
            return;
        }
        // Persist every active instance before reading from disk. Deliberately outside initLock:
        // flushNow() takes the scheduler's flushLock, and holding initLock while acquiring another
        // instance's flushLock deadlocks when two instances initialize in parallel
        // (initLock(A) -> flushLock(B) vs initLock(B) -> flushLock(A)).
        flushAllActive();
        initLock.lock();
        try {
            if (initialized.get()) {
                return;
            }
            File dir = Platform.getStorage().getStorageDirectory();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "Cannot create Lua storage directory: " + dir.getAbsolutePath());
            }
            // Publish the directory only after loading: a scheduled flush that saw it early could
            // otherwise overwrite the existing file with the still-empty in-memory store.
            if (dir != null) {
                diskIO.loadWorldStorage(dir);
                diskIO.loadPlayerStorage(dir);
            }
            scheduler.setDirectory(dir);
            initialized.set(true);
        } finally {
            initLock.unlock();
        }
    }

    @Override
    public IDataStore GetWorldStorage() {
        ensureInitialized();
        return worldDataStore;
    }

    @Override
    public IDataStore GetPlayerStorage(String playerUuid) {
        ensureInitialized();
        return playerDataStores.computeIfAbsent(playerUuid, uuid -> new PlayerDataStore(engine, uuid,
                playerStore, playerDocCache, playerDirty, scheduler::scheduleFlush, scheduler::flushNow));
    }

    @Override
    public IDataStore GetSessionStorage() {
        ensureInitialized();
        return sessionDataStore;
    }

    @Override
    public void Flush() {
        scheduler.flushNow();
    }

    @Override
    public void close() {
        Flush();
        ACTIVE_INSTANCES.remove(this);
    }
}

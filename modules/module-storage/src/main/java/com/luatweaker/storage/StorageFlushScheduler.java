package com.luatweaker.storage;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Debounced asynchronous disk flushing for the storage maps.
 *
 * <p>Rapid successive writes within the debounce window are coalesced into a single
 * atomic disk flush on a daemon thread from the common pool (via
 * {@link CompletableFuture#delayedExecutor(long, TimeUnit)}, whose delay thread is a
 * daemon), so gameplay and tick routines never wait on disk. A synchronous
 * {@link #flushNow()} with the same lock discipline lets {@code Flush()} wait for an
 * in-flight write before returning.</p>
 *
 * <p>Lock order: {@code scheduleLock} then {@code flushLock}; neither ever nests with
 * the service init lock, which keeps parallel initialization deadlock-free.</p>
 */
final class StorageFlushScheduler {

    private final StorageDiskIO diskIO;
    private final long debounceMs;
    private final ReentrantLock scheduleLock = new ReentrantLock();
    private final ReentrantLock flushLock = new ReentrantLock();
    private final AtomicReference<File> storageDir = new AtomicReference<>();
    private final AtomicBoolean worldDirty;
    private final AtomicBoolean playerDirty;
    private CompletableFuture<Void> pendingFlushTask;

    StorageFlushScheduler(StorageDiskIO diskIO, long debounceMs,
                          AtomicBoolean worldDirty, AtomicBoolean playerDirty) {
        this.diskIO = diskIO;
        this.debounceMs = debounceMs;
        this.worldDirty = worldDirty;
        this.playerDirty = playerDirty;
    }

    void setDirectory(File dir) {
        storageDir.set(dir);
    }

    void scheduleFlush() {
        if (debounceMs <= 0) {
            flushNow();
            return;
        }
        // scheduleLock only: a SetAsync from the game thread must never wait for a disk write.
        scheduleLock.lock();
        try {
            if (pendingFlushTask == null || pendingFlushTask.isDone()) {
                pendingFlushTask = CompletableFuture.runAsync(this::flushDirtyStores,
                        CompletableFuture.delayedExecutor(debounceMs, TimeUnit.MILLISECONDS));
            }
        } finally {
            scheduleLock.unlock();
        }
    }

    /**
     * Runs the pending write immediately. Waiting for flushLock is the point: an in-flight
     * scheduled write holds it, so returning only after acquiring it guarantees the data on
     * disk matches the dirty snapshot.
     */
    void flushNow() {
        File dir = storageDir.get();
        if (dir == null) {
            return;
        }
        scheduleLock.lock();
        try {
            if (pendingFlushTask != null && !pendingFlushTask.isDone()) {
                pendingFlushTask.cancel(false);
            }
        } finally {
            scheduleLock.unlock();
        }
        flushLock.lock();
        try {
            writeDirtyStores(dir);
        } finally {
            flushLock.unlock();
        }
    }

    private void flushDirtyStores() {
        File dir = storageDir.get();
        if (dir == null) {
            // Not initialized yet: keep the dirty flags so the first real flush persists them.
            return;
        }
        flushLock.lock();
        try {
            writeDirtyStores(dir);
        } finally {
            flushLock.unlock();
        }
    }

    /**
     * Persists the stores whose dirty flag is set. Caller must hold {@link #flushLock}.
     *
     * <p>Each flag is cleared before the write so a concurrent {@code SetAsync} re-marks the store
     * dirty and is picked up by the next flush instead of being swallowed. A failed write puts the
     * flag back, so the data is retried rather than silently lost.</p>
     */
    private void writeDirtyStores(File dir) {
        if (worldDirty.compareAndSet(true, false)) {
            diskIO.saveWorldStorage(dir);
        }
        if (playerDirty.compareAndSet(true, false)) {
            diskIO.savePlayerStorage(dir);
        }
    }
}

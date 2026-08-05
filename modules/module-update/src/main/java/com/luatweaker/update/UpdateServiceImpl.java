package com.luatweaker.update;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.update.IUpdateService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.mod.LuaMod;
import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.mod.LuaModManifest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Declarative update checker: reads the {@code update_url} from every loaded
 * mod's manifest.json, downloads the feed asynchronously on a daemon thread
 * (never on a game thread and never inside the Lua VM) and caches the result.
 *
 * <p>{@link #checkAll()} is invoked by the platform after every mod load
 * cycle. Lua code only ever reads the cached result through
 * {@link IUpdateService} / {@code mod:GetUpdateStatus()} - there is no Lua
 * path that can reach the network through this service.</p>
 */
public class UpdateServiceImpl implements IUpdateService {

    private static final Map<String, UpdateStatus> STATUS = new ConcurrentHashMap<>();
    private static final Set<String> SCHEDULED = ConcurrentHashMap.newKeySet();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "luatweaker-update-checker");
        t.setDaemon(true);
        return t;
    });

    private final ILuaEngine engine;

    public UpdateServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    /**
     * Schedules an async check for every loaded mod that declares an
     * {@code update_url}. Re-checks only mods whose installed version changed
     * since the last check or whose check is still in flight; completed checks
     * are kept for the session.
     */
    public static void checkAll() {
        for (LuaMod mod : LuaModManager.getLoadedMods().values()) {
            LuaModManifest manifest = mod.getManifest();
            String updateUrl = manifest.updateUrl();
            if (updateUrl == null || updateUrl.isBlank()) continue;

            String modId = manifest.id();
            UpdateStatus stored = STATUS.get(modId);
            boolean needsCheck = stored == null || stored.checking()
                    || !stored.currentVersion().equals(manifest.version());
            if (needsCheck && SCHEDULED.add(modId)) {
                STATUS.put(modId, new UpdateStatus(modId, manifest.version(), updateUrl,
                        null, null, null, null, true, null));
                EXECUTOR.submit(() -> {
                    try {
                        UpdateStatus result = UpdateChecker.check(modId, manifest.version(), updateUrl);
                        recordResult(result);
                    } catch (Exception e) {
                        // UpdateChecker never throws; defensive catch stays loud.
                        String msg = "update check crashed: " + e.getMessage();
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "[Update][" + modId + "] " + msg);
                        recordResult(new UpdateStatus(modId, manifest.version(), updateUrl,
                                null, null, null, null, false, msg));
                    } finally {
                        SCHEDULED.remove(modId);
                    }
                });
            }
        }
    }

    /** @return all cached statuses keyed by mod id (unmodifiable). */
    public static @NotNull Map<String, UpdateStatus> getUpdateStatuses() {
        return Collections.unmodifiableMap(STATUS);
    }

    /** @return cached statuses that have a confirmed available update, sorted by mod id. */
    public static @NotNull List<UpdateStatus> getUpdates() {
        return STATUS.values().stream()
                .filter(UpdateStatus::hasUpdate)
                .sorted(Comparator.comparing(UpdateStatus::modId))
                .toList();
    }

    /** Clears the cached statuses (used by tests; the checker re-fills on checkAll). */
    static void clearStatuses() {
        STATUS.clear();
        SCHEDULED.clear();
    }

    /** Stores a terminal or intermediate status and logs the outcome. */
    static void recordResult(@NotNull UpdateStatus status) {
        STATUS.put(status.modId(), status);
        logResult(status);
    }

    private static void logResult(@NotNull UpdateStatus status) {
        if (status.error() != null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Update][" + status.modId() + "] check failed: " + status.error());
        } else if (status.hasUpdate()) {
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "[Update][" + status.modId() + "] Update available: v" + status.currentVersion()
                            + " -> v" + status.latestVersion()
                            + (status.updateName() == null || status.updateName().isEmpty()
                                    ? "" : " (" + status.updateName() + ")"));
        } else {
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "[Update][" + status.modId() + "] Up to date (v" + status.currentVersion() + ")");
        }
    }

    @Override
    public @Nullable ILuaValue GetStatus(String modId) {
        UpdateStatus status = STATUS.get(modId);
        return status != null ? toLua(status) : null;
    }

    @Override
    public ILuaValue GetUpdates() {
        ILuaTable table = engine.createTable();
        for (UpdateStatus status : getUpdates()) {
            table.rawset(status.modId(), toLua(status));
        }
        return table;
    }

    private ILuaValue toLua(@NotNull UpdateStatus status) {
        ILuaTable table = engine.createTable();
        table.rawset("HasUpdate", status.hasUpdate());
        table.rawset("Checking", status.checking());
        table.rawset("CurrentVersion", status.currentVersion());
        if (status.updateUrl() != null) table.rawset("UpdateUrl", status.updateUrl());
        if (status.latestVersion() != null) table.rawset("LatestVersion", status.latestVersion());
        if (status.updateName() != null) table.rawset("Name", status.updateName());
        if (status.downloadUrl() != null) table.rawset("DownloadUrl", status.downloadUrl());
        if (status.changelog() != null) table.rawset("Changelog", status.changelog());
        if (status.error() != null) table.rawset("Error", status.error());
        return table;
    }
}

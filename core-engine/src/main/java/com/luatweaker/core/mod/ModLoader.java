package com.luatweaker.core.mod;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaException;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Runs the load cycle: discovery, switch defaults, ordered loading, teardown hooks.
 *
 * <p>Instance-scoped per load cycle: the engine, environment and shared result
 * maps are constructor state so the per-mod steps stay small and readable.</p>
 */
final class ModLoader {
    private final ILuaEngine engine;
    private final String currentEnv;
    private final LuaModLoadObserver observer;
    private final Map<String, LuaMod> loadedMods;
    private final Map<String, String> loadErrors;
    private final Map<String, Map<String, String>> integrationStatus;
    private final Map<String, String> installedMods;
    private final String engineVersion;
    private final String minecraftVersion;

    private static final String LOG_MOD = "[LuaMod][";
    private static final String LOG_MANAGER = "[LuaModManager] ";

    private ModLoader(ILuaEngine engine, String currentEnv, LuaModLoadObserver observer,
            Map<String, LuaMod> loadedMods, Map<String, String> loadErrors,
            Map<String, Map<String, String>> integrationStatus,
            Map<String, String> installedMods,
            String engineVersion, String minecraftVersion) {
        this.engine = engine;
        this.currentEnv = currentEnv;
        this.observer = observer;
        this.loadedMods = loadedMods;
        this.loadErrors = loadErrors;
        this.integrationStatus = integrationStatus;
        this.installedMods = installedMods;
        this.engineVersion = engineVersion;
        this.minecraftVersion = minecraftVersion;
    }

    static void load(@NotNull File targetDir, @NotNull ILuaEngine engine,
            @NotNull String currentEnv, @NotNull LuaModLoadObserver observer,
            @NotNull Map<String, LuaMod> loadedMods, @NotNull Map<String, String> loadErrors,
            @NotNull Map<String, Map<String, String>> integrationStatus,
            @NotNull Map<String, String> installedMods,
            @Nullable String engineVersion, @Nullable String minecraftVersion) {
        ModLifecycleHooks.fireModTeardownHooks(engine, loadedMods);
        loadedMods.clear();
        loadErrors.clear();
        integrationStatus.clear();

        File luamodsDir = LuaModManager.resolveLuamodsDir(targetDir);
        if (!luamodsDir.exists()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    LOG_MANAGER + "luamods directory not found: " + luamodsDir.getAbsolutePath());
            return;
        }
        List<DiscoveredMod> discovered = ModDiscovery.discoverMods(luamodsDir);
        if (discovered.isEmpty()) {
            return;
        }
        Map<String, Boolean> modsState = initModsState(luamodsDir, discovered);
        announceDiscovery(luamodsDir, discovered, modsState);
        List<DiscoveredMod> sorted = ModTopoSort.sortModsByDependencies(discovered);
        ModLoader loader = new ModLoader(engine, currentEnv, observer,
                loadedMods, loadErrors, integrationStatus, installedMods, engineVersion, minecraftVersion);
        loader.loadSortedMods(sorted, luamodsDir, modsState);
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                LOG_MANAGER + "Successfully initialized " + loadedMods.size() + " active LuaMod(s).");
        observer.onLoadComplete(loadedMods.size(), sorted.size());
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "========================================================================");
    }

    private static @NotNull Map<String, Boolean> initModsState(@NotNull File luamodsDir,
            @NotNull List<DiscoveredMod> discovered) {
        Map<String, Boolean> modsState = ModSwitches.readModsState(luamodsDir);
        boolean stateChanged = false;
        for (DiscoveredMod found : discovered) {
            String modId = found.manifest().id();
            if (!modsState.containsKey(modId)) {
                modsState.put(modId, true);
                stateChanged = true;
            }
        }
        if (stateChanged) {
            ModSwitches.writeModsState(luamodsDir, modsState);
        }
        return modsState;
    }

    private static void announceDiscovery(@NotNull File luamodsDir,
            @NotNull List<DiscoveredMod> discovered, @NotNull Map<String, Boolean> modsState) {
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "========================================================================");
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "                     LUATWEAKER LUAMOD MANAGER                          ");
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "========================================================================");
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                LOG_MANAGER + "Scanning directory: " + luamodsDir.getName() + " (" + luamodsDir.getAbsolutePath() + ")");
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                LOG_MANAGER + "Discovered " + discovered.size() + " Autonomous LuaMod(s):");
        for (DiscoveredMod found : discovered) {
            String modId = found.manifest().id();
            boolean isEnabled = !Boolean.FALSE.equals(modsState.get(modId));
            String statusStr = isEnabled ? "[ENABLED]" : "[DISABLED]";
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    LOG_MANAGER + "  - " + modId + " v" + found.manifest().version()
                            + " (" + found.manifest().name() + ") " + statusStr);
        }
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "------------------------------------------------------------------------");
    }

    private void loadSortedMods(@NotNull List<DiscoveredMod> sorted,
            @NotNull File luamodsDir, @NotNull Map<String, Boolean> modsState) {
        int totalMods = sorted.size();
        int modIndex = 0;
        for (DiscoveredMod found : sorted) {
            modIndex++;
            loadSingleMod(found, modIndex, totalMods, luamodsDir, modsState);
        }
    }

    private void loadSingleMod(@NotNull DiscoveredMod found, int modIndex, int totalMods,
            @NotNull File luamodsDir, @NotNull Map<String, Boolean> modsState) {
        String modId = found.manifest().id();
        observer.onModStart(modIndex, totalMods, modId);
        if (Boolean.FALSE.equals(modsState.get(modId))) {
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    LOG_MANAGER + "Mod '" + modId + "' is DISABLED in mods_manager.json - Skipping execution.");
            return;
        }
        if (skipModGates(found, modId)) {
            return;
        }
        if (!ModDependencyGates.shouldLoadInEnvironment(found.manifest().environment(), currentEnv)) {
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    LOG_MANAGER + "Mod '" + modId + "' is declared as '" + found.manifest().environment()
                            + "' but current environment is '" + currentEnv + "' - Skipping execution.");
            return;
        }
        loadModBody(found, modId, luamodsDir);
    }

    /** @return true when any gate fails (recorded, skip). */
    private boolean skipModGates(@NotNull DiscoveredMod found, @NotNull String modId) {
        String skipReason = ModDependencyGates.checkModGates(found, loadedMods, installedMods,
                engineVersion, minecraftVersion);
        if (skipReason == null) {
            return false;
        }
        loadErrors.put(modId, skipReason);
        LuaTweakerLog.get().error(LogStage.SYSTEM,
                LOG_MANAGER + "Skipping mod '" + modId + "': " + skipReason);
        return true;
    }

    private void loadModBody(@NotNull DiscoveredMod found, @NotNull String modId,
            @NotNull File luamodsDir) {
        com.luatweaker.core.logger.AsyncFileLogger.get().initModLog(modId);
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                LOG_MANAGER + "Loading active LuaMod: " + modId + " v" + found.manifest().version()
                        + " by " + found.manifest().author() + " (" + found.file().getName() + ")");

        LuaMod mod = new LuaMod(found.manifest(), found.file(), currentEnv, loadedMods, luamodsDir);
        mod.ensureConfigFile(found.defaultConfig());

        ILuaTable modTable = mod.createModTable(engine);
        engine.getGlobalEnvironment().rawset("mod", modTable);
        engine.getGlobalEnvironment().rawset("Mod", modTable);

        String mainScriptPath = found.manifest().main();
        LuaModContext.pushLoadingMod(modId);
        try {
            clearEngineError(engine);
            String mainCode = readScriptContent(found.file(), mainScriptPath);
            if (mainCode == null) {
                String missingMessage = "Main entrypoint script not found: " + mainScriptPath;
                loadErrors.put(modId, missingMessage);
                LuaTweakerLog.get().error(LogStage.SCRIPT_LOAD,
                        LOG_MOD + found.manifest().id() + "] " + missingMessage);
                return;
            }
            LuaTweakerLog.get().info(LogStage.SCRIPT_LOAD,
                    LOG_MOD + modId + "] Executing entrypoint: " + mainScriptPath);
            engine.executeString(mainCode, modId + "/" + mainScriptPath);
            String loadError = clearEngineError(engine);
            if (loadError != null) {
                loadErrors.put(modId, loadError);
                return;
            }
            ModIntegrationRunner.loadIntegrations(engine, found, integrationStatus, installedMods);
            ModLifecycleHooks.triggerOnEnable(engine, modTable, modId, loadErrors);
            if (loadErrors.containsKey(modId)) {
                return;
            }
            loadedMods.put(modId, mod);
            LuaTweakerLog.get().info(LogStage.SCRIPT_LOAD,
                    LOG_MOD + found.manifest().id() + "] Successfully loaded v" + found.manifest().version());
        } catch (IllegalArgumentException | LuaException e) {
            loadErrors.put(modId, errorMessage(e));
            LuaTweakerLog.get().error(LogStage.SCRIPT_LOAD,
                    LOG_MOD + found.manifest().id() + "] Error executing " + mainScriptPath + ": "
                            + errorMessage(e));
        } finally {
            LuaModContext.popLoadingMod();
        }
    }

    /**
     * Fires {@code OnDisable} for every loaded mod on the engine that loaded it. Callers must pass
     * the owning engine: firing old-VM closures on a new engine corrupts both VMs. The loaded map
     * is cleared afterwards so a later load call does not fire the hooks twice.
     */
    static String clearEngineError(ILuaEngine engine) {
        return engine.getAndClearLastExecutionError();
    }

    static @NotNull String errorMessage(@NotNull Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    static String readScriptContent(File fileOrDir, String relPath) {
        String validated = LuaModPathSecurity.validateEntryPoint(relPath);
        if (fileOrDir.isDirectory()) {
            return ModDiscovery.readDirectoryScript(fileOrDir, validated);
        }
        if (fileOrDir.getName().endsWith(".zip")) {
            return ModDiscovery.readZipScript(fileOrDir, validated);
        }
        return null;
    }
}

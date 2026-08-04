package com.luatweaker.core.mod;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Autonomous LuaMod Manager responsible for scanning, sorting dependencies,
 * mounting assets/data, and executing single entrypoint main.lua for each mod in luamods/.
 */
public class LuaModManager {

    private static final Map<String, LuaMod> LOADED_MODS = new ConcurrentHashMap<>();
    private static final Map<String, String> LOAD_ERRORS = new ConcurrentHashMap<>();

    public static @NotNull Map<String, LuaMod> getLoadedMods() {
        return Collections.unmodifiableMap(LOADED_MODS);
    }

    /** Per-mod entrypoint load errors from the most recent load cycle (empty when all mods loaded cleanly). */
    public static @NotNull Map<String, String> getLoadErrors() {
        return Collections.unmodifiableMap(LOAD_ERRORS);
    }

    /**
     * Scans and loads all autonomous LuaMods from the given luamods directory.
     */
    public static void loadLuaMods(@NotNull File targetDir, @NotNull ILuaEngine engine) {
        fireModTeardownHooks(engine);
        LOADED_MODS.clear();
        LOAD_ERRORS.clear();

        File luamodsDir = "luamods".equalsIgnoreCase(targetDir.getName()) ? targetDir : new File(targetDir, "luamods");
        if (!luamodsDir.exists()) {
            luamodsDir.mkdirs();
            return;
        }

        File[] files = luamodsDir.listFiles();
        if (files == null || files.length == 0) return;

        List<DiscoveredMod> discovered = new ArrayList<>();

        for (File file : files) {
            if (file.isDirectory()) {
                File manifestFile = new File(file, "manifest.json");
                if (manifestFile.exists()) {
                    try {
                        String json = Files.readString(manifestFile.toPath(), StandardCharsets.UTF_8);
                        LuaModManifest manifest = LuaModManifest.parseJson(json);
                        if (manifest != null) {
                            String defaultConfig = null;
                            File cfgFile = new File(file, "default_config.json");
                            if (cfgFile.exists()) defaultConfig = Files.readString(cfgFile.toPath(), StandardCharsets.UTF_8);
                            discovered.add(new DiscoveredMod(manifest, file, defaultConfig));
                        }
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM,
                                "[LuaModManager] Failed to read manifest from directory: " + file.getName() + " - " + e.getMessage());
                    }
                }
            } else if (file.getName().endsWith(".zip")) {
                try (ZipFile zip = new ZipFile(file)) {
                    ZipEntry manifestEntry = zip.getEntry("manifest.json");
                    if (manifestEntry != null) {
                        try (InputStream is = zip.getInputStream(manifestEntry)) {
                            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                            LuaModManifest manifest = LuaModManifest.parseJson(json);
                            if (manifest != null) {
                                String defaultConfig = null;
                                ZipEntry cfgEntry = zip.getEntry("default_config.json");
                                if (cfgEntry != null) {
                                    try (InputStream cfgIs = zip.getInputStream(cfgEntry)) {
                                        defaultConfig = new String(cfgIs.readAllBytes(), StandardCharsets.UTF_8);
                                    }
                                }
                                discovered.add(new DiscoveredMod(manifest, file, defaultConfig));
                            }
                        }
                    }
                } catch (Exception e) {
                    LuaTweakerLog.get().error(LogStage.SYSTEM,
                            "[LuaModManager] Failed to read zip mod: " + file.getName() + " - " + e.getMessage());
                }
            }
        }

        if (discovered.isEmpty()) return;

        // Manage mods_manager.json state
        File modsManagerFile = new File(luamodsDir, "mods_manager.json");
        Map<String, Boolean> modsState = new LinkedHashMap<>();
        com.google.gson.Gson prettyGson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

        if (modsManagerFile.exists()) {
            try {
                String content = Files.readString(modsManagerFile.toPath(), StandardCharsets.UTF_8);
                Map<String, Boolean> parsed = prettyGson.fromJson(content, Map.class);
                if (parsed != null) {
                    for (Map.Entry<String, Boolean> e : parsed.entrySet()) {
                        modsState.put(e.getKey(), Boolean.TRUE.equals(e.getValue()));
                    }
                }
            } catch (Exception ignored) {}
        }

        boolean stateChanged = false;
        for (DiscoveredMod dm : discovered) {
            String modId = dm.manifest().id();
            if (!modsState.containsKey(modId)) {
                modsState.put(modId, true);
                stateChanged = true;
            }
        }

        if (stateChanged || !modsManagerFile.exists()) {
            try {
                Files.writeString(modsManagerFile.toPath(), prettyGson.toJson(modsState), StandardCharsets.UTF_8);
                LuaTweakerLog.get().info(LogStage.SYSTEM, "[LuaModManager] Updated " + modsManagerFile.getAbsolutePath());
            } catch (Exception ignored) {}
        }

        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "========================================================================");
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "                     LUATWEAKER LUAMOD MANAGER                          ");
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "========================================================================");
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "[LuaModManager] Scanning directory: " + luamodsDir.getName() + " (" + luamodsDir.getAbsolutePath() + ")");
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "[LuaModManager] Discovered " + discovered.size() + " Autonomous LuaMod(s):");

        for (DiscoveredMod dm : discovered) {
            String modId = dm.manifest().id();
            boolean isEnabled = !Boolean.FALSE.equals(modsState.get(modId));
            String statusStr = isEnabled ? "[ENABLED]" : "[DISABLED]";
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    String.format("[LuaModManager]   - %s v%s (%s) %s",
                            modId, dm.manifest().version(), dm.manifest().name(), statusStr));
        }
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "------------------------------------------------------------------------");

        // Topological Sort by dependencies
        List<DiscoveredMod> sorted = sortModsByDependencies(discovered);

        for (DiscoveredMod dm : sorted) {
            String modId = dm.manifest().id();
            if (Boolean.FALSE.equals(modsState.get(modId))) {
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                        "[LuaModManager] Mod '" + modId + "' is DISABLED in mods_manager.json - Skipping execution.");
                continue;
            }

            // Initialize fresh dedicated log file for this mod
            com.luatweaker.core.logger.AsyncFileLogger.get().initModLog(modId);

            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "[LuaModManager] Loading active LuaMod: " + modId + " v" + dm.manifest().version() + " by " + dm.manifest().author() + " (" + dm.file().getName() + ")");

            LuaMod mod = new LuaMod(dm.manifest(), dm.file(), LOADED_MODS);
            LOADED_MODS.put(modId, mod);
            mod.ensureConfigFile(dm.defaultConfig());

            ILuaTable modTable = mod.createModTable(engine);
            engine.getGlobalEnvironment().rawset("mod", modTable);
            engine.getGlobalEnvironment().rawset("Mod", modTable);

            // Execute main.lua ONLY
            String mainScriptPath = dm.manifest().main();
            try {
                String mainCode = readScriptContent(dm.file(), mainScriptPath);
                if (mainCode != null) {
                    LuaTweakerLog.get().info(LogStage.SCRIPT_LOAD,
                            "[LuaMod][" + modId + "] Executing entrypoint: " + mainScriptPath);
                    engine.executeString(mainCode, modId + "/" + mainScriptPath);
                    if (engine instanceof com.luatweaker.core.vm.CobaltLuaEngine cobaltEngine) {
                        String loadError = cobaltEngine.getAndClearLastExecutionError();
                        if (loadError != null) {
                            LOAD_ERRORS.put(modId, loadError);
                        }
                    }

                    // Trigger mod.OnEnable() if provided
                    ILuaValue onEnableVal = modTable.rawget("OnEnable");
                    if (onEnableVal == null || onEnableVal.isNil()) {
                        onEnableVal = modTable.rawget("onEnable");
                    }
                    if (onEnableVal != null && onEnableVal.isFunction()) {
                        engine.callFunction(onEnableVal);
                    }
                    LuaTweakerLog.get().info(LogStage.SCRIPT_LOAD,
                            "[LuaMod][" + dm.manifest().id() + "] Successfully loaded v" + dm.manifest().version());
                } else {
                    LuaTweakerLog.get().error(LogStage.SCRIPT_LOAD,
                            "[LuaMod][" + dm.manifest().id() + "] Main entrypoint script not found: " + mainScriptPath);
                }
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SCRIPT_LOAD,
                        "[LuaMod][" + dm.manifest().id() + "] Error executing " + mainScriptPath + ": " + e.getMessage());
            }
        }
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "[LuaModManager] Successfully initialized " + LOADED_MODS.size() + " active LuaMod(s).");
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "========================================================================");
    }

    private static void fireModTeardownHooks(@NotNull ILuaEngine engine) {
        for (Map.Entry<String, LuaMod> entry : LOADED_MODS.entrySet()) {
            String modId = entry.getKey();
            LuaMod mod = entry.getValue();
            ILuaTable modTable = engine.getGlobalEnvironment().rawget("mod") instanceof ILuaTable t ? t : null;
            if (modTable == null) continue;

            ILuaValue onDisable = modTable.rawget("OnDisable");
            if (onDisable == null || onDisable.isNil()) {
                onDisable = modTable.rawget("onDisable");
            }
            if (onDisable != null && onDisable.isFunction()) {
                try {
                    LuaTweakerLog.get().info(LogStage.SYSTEM,
                            "[LuaMod][" + modId + "] Firing OnDisable teardown hook...");
                    engine.callFunction(onDisable);
                } catch (Exception e) {
                    LuaTweakerLog.get().error(LogStage.SYSTEM,
                            "[LuaMod][" + modId + "] Error in OnDisable hook: " + e.getMessage());
                }
            }
        }
    }

    private static String readScriptContent(File fileOrDir, String relPath) {
        if (fileOrDir.isDirectory()) {
            File scriptFile = new File(fileOrDir, relPath);
            if (scriptFile.exists()) {
                try {
                    return Files.readString(scriptFile.toPath(), StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }
        } else if (fileOrDir.getName().endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(fileOrDir)) {
                ZipEntry entry = zip.getEntry(relPath);
                if (entry != null) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static record DiscoveredMod(LuaModManifest manifest, File file, String defaultConfig) {}

    private static List<DiscoveredMod> sortModsByDependencies(List<DiscoveredMod> mods) {
        Map<String, DiscoveredMod> modMap = new HashMap<>();
        for (DiscoveredMod m : mods) modMap.put(m.manifest().id(), m);

        List<DiscoveredMod> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (DiscoveredMod m : mods) {
            visitMod(m, modMap, sorted, visited, visiting);
        }
        return sorted;
    }

    private static void visitMod(DiscoveredMod mod,
                                 Map<String, DiscoveredMod> modMap,
                                 List<DiscoveredMod> sorted,
                                 Set<String> visited,
                                 Set<String> visiting) {
        String id = mod.manifest().id();
        if (visited.contains(id)) return;
        if (visiting.contains(id)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "[LuaModManager] Circular dependency detected involving: " + id);
            return;
        }

        visiting.add(id);
        for (String dep : mod.manifest().dependencies()) {
            DiscoveredMod depMod = modMap.get(dep);
            if (depMod != null) {
                visitMod(depMod, modMap, sorted, visited, visiting);
            }
        }
        visiting.remove(id);
        visited.add(id);
        sorted.add(mod);
    }
}

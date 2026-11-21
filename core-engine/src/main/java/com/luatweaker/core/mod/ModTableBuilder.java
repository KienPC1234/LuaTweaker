package com.luatweaker.core.mod;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.mod.IModExports;
import com.luatweaker.api.mod.IModFiles;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * Binds one mod's {@code mod} table into the Lua environment.
 *
 * <p>Extracted from {@link LuaMod} so the handle stays a small data carrier
 * while the (large but flat) binding surface lives here.</p>
 */
final class ModTableBuilder {
    private final ILuaEngine engine;
    private final LuaModManifest manifest;
    private final File configFile;
    private final Map<String, ILuaTable> exportedApis;
    private final Map<String, LuaMod> loadedModsRegistry;
    private final String environment;
    private final File modFileOrDir;
    private final File exportsRoot;

    ModTableBuilder(@NotNull ILuaEngine engine, @NotNull LuaModManifest manifest,
            @NotNull File configFile, @NotNull Map<String, ILuaTable> exportedApis,
            @NotNull Map<String, LuaMod> loadedModsRegistry, @NotNull String environment,
            @NotNull File modFileOrDir, @NotNull File exportsRoot) {
        this.engine = engine;
        this.manifest = manifest;
        this.configFile = configFile;
        this.exportedApis = exportedApis;
        this.loadedModsRegistry = loadedModsRegistry;
        this.environment = environment;
        this.modFileOrDir = modFileOrDir;
        this.exportsRoot = exportsRoot;
    }

    /** Binds the table; the caller stores it on the mod handle. */
    @NotNull ILuaTable build() {
        ILuaTable table = engine.createTable();
        table.rawset("ID", engine.wrapString(manifest.id()));
        table.rawset("Name", engine.wrapString(manifest.name()));
        table.rawset("Version", engine.wrapString(manifest.version()));
        table.rawset("Author", engine.wrapString(manifest.author()));

        bindConfigApi(table);
        bindExportApi(table);
        bindEnvironmentFlags(table);
        bindUpdateStatus(table);
        bindModQueries(table);
        bindIntegrationQueries(table);
        bindFileHandles(table);
        return table;
    }

    /** Throws a clear error when fewer than {@code count} arguments follow the offset. */
    private static void requireArgs(ILuaValue[] args, int off, int count, String usage) {
        if (args.length - off < count) {
            throw new IllegalArgumentException(usage);
        }
    }

    private void bindConfigApi(@NotNull ILuaTable table) {
        table.rawset("GetConfig", args -> {
            if (configFile.exists()) {
                try {
                    String content = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
                    JsonObject json = LuaMod.GSON.fromJson(content, JsonObject.class);
                    if (json != null) {
                        return engine.toLuaValue(LuaMod.GSON.fromJson(json, Map.class));
                    }
                    LuaTweakerLog.get().warn(LogStage.SYSTEM,
                            LuaMod.LOG_PREFIX + manifest.id() + "] Config parse returned null: "
                                    + configFile.getAbsolutePath());
                } catch (IOException | JsonSyntaxException e) {
                    LuaTweakerLog.get().error(LogStage.SYSTEM,
                            LuaMod.LOG_PREFIX + manifest.id() + "] Failed to read config "
                                    + configFile.getAbsolutePath() + ": " + e.getMessage());
                }
            } else {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        LuaMod.LOG_PREFIX + manifest.id() + "] Config file missing: "
                                + configFile.getAbsolutePath());
            }
            return engine.createTable();
        });
        table.rawset("getConfig", table.rawget("GetConfig"));
    }

    private void bindExportApi(@NotNull ILuaTable table) {
        // mod:ExportAPI("ApiName", apiTable)
        table.rawset("ExportAPI", args -> {
            int off = LuaBinder.getOffset(args);
            requireArgs(args, off, 2, "mod:ExportAPI requires (apiName, apiTable)");
            String apiName = args[off].asString();
            ILuaValue apiVal = args[off + 1];
            if (apiVal instanceof ILuaTable apiTable) {
                exportedApis.put(apiName, apiTable);
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                        LuaMod.LOG_PREFIX + manifest.id() + "] Exported IPC API: " + apiName);
            }
            return table;
        });
        table.rawset("exportAPI", table.rawget("ExportAPI"));

        // mod:ImportAPI("target_mod_id", "ApiName")
        table.rawset("ImportAPI", args -> {
            int off = LuaBinder.getOffset(args);
            requireArgs(args, off, 2, "mod:ImportAPI requires (targetModId, apiName)");
            String targetId = args[off].asString();
            String apiName = args[off + 1].asString();

            LuaMod targetMod = loadedModsRegistry.get(targetId);
            if (targetMod != null) {
                ILuaTable exported = targetMod.getExportedApis().get(apiName);
                if (exported != null) {
                    return exported;
                }
            }
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    LuaMod.LOG_PREFIX + manifest.id() + "] ImportAPI failed: target '" + targetId
                            + "' api '" + apiName + "' not found.");
            return engine.nilValue();
        });
        table.rawset("importAPI", table.rawget("ImportAPI"));
    }

    private void bindEnvironmentFlags(@NotNull ILuaTable table) {
        table.rawset("IsClient", args -> engine.wrapBoolean(
                "client".equalsIgnoreCase(environment) || "universal".equalsIgnoreCase(environment)));
        table.rawset("isClient", table.rawget("IsClient"));

        table.rawset("IsServer", args -> engine.wrapBoolean(
                "server".equalsIgnoreCase(environment) || "universal".equalsIgnoreCase(environment)));
        table.rawset("isServer", table.rawget("IsServer"));
    }

    private void bindUpdateStatus(@NotNull ILuaTable table) {
        // mod:GetUpdateStatus([modId]) - read-only view over the update status
        // cached by the engine's update checker (module-update). The checker runs
        // entirely on the Java side; this method only reads its result table.
        table.rawset("GetUpdateStatus", args -> {
            int off = LuaBinder.getOffset(args);
            String targetId = manifest.id();
            if (args.length - off >= 1 && args[off] != null && !args[off].isNil()) {
                targetId = args[off].asString();
            }
            Object svc = com.luatweaker.core.service.LuaServiceRegistry.get("UpdateServiceImpl");
            if (svc instanceof com.luatweaker.api.update.IUpdateService updateService) {
                ILuaValue status = updateService.GetStatus(targetId);
                return status != null ? status : engine.nilValue();
            }
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    LuaMod.LOG_PREFIX + manifest.id()
                            + "] GetUpdateStatus: UpdateServiceImpl not registered (module-update missing).");
            return engine.nilValue();
        });
        table.rawset("getUpdateStatus", table.rawget("GetUpdateStatus"));
    }

    private void bindModQueries(@NotNull ILuaTable table) {
        // mod:HasMod(modId) - low-level presence check for optional interop.
        table.rawset("HasMod", args -> {
            int off = LuaBinder.getOffset(args);
            requireArgs(args, off, 1, "mod:HasMod requires (modId)");
            return engine.wrapBoolean(loadedModsRegistry.containsKey(args[off].asString()));
        });
        table.rawset("hasMod", table.rawget("HasMod"));

        // mod:GetModVersion(modId) - version string of a loaded LuaMod, nil when absent.
        table.rawset("GetModVersion", args -> {
            int off = LuaBinder.getOffset(args);
            requireArgs(args, off, 1, "mod:GetModVersion requires (modId)");
            LuaMod target = loadedModsRegistry.get(args[off].asString());
            if (target == null) {
                return engine.nilValue();
            }
            return engine.wrapString(target.getManifest().version());
        });
        table.rawset("getModVersion", table.rawget("GetModVersion"));

        // mod:CheckDependency(modId [, minVersion [, maxVersion]]) - low-level
        // runtime gate for optional interop: true when the target LuaMod is loaded
        // AND its version falls inside the inclusive window. Bounds are optional;
        // a missing target is false (not an error) so feature flags stay simple.
        table.rawset("CheckDependency", args -> {
            int off = LuaBinder.getOffset(args);
            requireArgs(args, off, 1, "mod:CheckDependency requires (modId [, minVersion [, maxVersion]])");
            LuaMod target = loadedModsRegistry.get(args[off].asString());
            if (target == null) {
                return engine.wrapBoolean(false);
            }
            String min = args.length - off >= 2 && args[off + 1] != null && !args[off + 1].isNil()
                    ? args[off + 1].asString() : null;
            String max = args.length - off >= 3 && args[off + 2] != null && !args[off + 2].isNil()
                    ? args[off + 2].asString() : null;
            return engine.wrapBoolean(
                    ModVersion.satisfies(target.getManifest().version(), min, max));
        });
        table.rawset("checkDependency", table.rawget("CheckDependency"));
    }

    private void bindIntegrationQueries(@NotNull ILuaTable table) {
        // mod:HasIntegration(targetModId) - true when the declared integration
        // entry loaded this cycle (host mod installed, version accepted, entry ran).
        table.rawset("HasIntegration", args -> {
            int off = LuaBinder.getOffset(args);
            requireArgs(args, off, 1, "mod:HasIntegration requires (targetModId)");
            Map<String, String> statuses = LuaModManager
                    .getIntegrationStatus().getOrDefault(manifest.id(), Map.of());
            String status = statuses.get(args[off].asString());
            return engine.wrapBoolean(status != null && status.startsWith("loaded"));
        });
        table.rawset("hasIntegration", table.rawget("HasIntegration"));

        // mod:GetIntegrationStatus([targetModId]) - per-integration outcome table
        // ({id: "loaded ..."/"skipped: ..."/"error: ..."}), or one entry's string.
        table.rawset("GetIntegrationStatus", args -> {
            int off = LuaBinder.getOffset(args);
            Map<String, String> statuses = LuaModManager
                    .getIntegrationStatus().getOrDefault(manifest.id(), Map.of());
            if (args.length - off >= 1 && args[off] != null && !args[off].isNil()) {
                String single = statuses.get(args[off].asString());
                return single == null ? engine.nilValue() : engine.wrapString(single);
            }
            ILuaTable out = engine.createTable();
            for (Map.Entry<String, String> entry : statuses.entrySet()) {
                out.rawset(entry.getKey(), engine.wrapString(entry.getValue()));
            }
            return out;
        });
        table.rawset("getIntegrationStatus", table.rawget("GetIntegrationStatus"));
    }

    private void bindFileHandles(@NotNull ILuaTable table) {
        // mod:Files()  - read-only access to this mod's own folder / .zip
        // mod:Exports() - writable sandbox at gameDir/luatweaker/exports/<id>
        // Handles are per-mod capability tables bound with closures (the engine is
        // shared by every mod, so a global File service could not attribute calls).
        ModFileSystem fileSystem = new ModFileSystem(engine, manifest.id(), modFileOrDir,
                new File(exportsRoot, manifest.id()));
        ILuaTable filesTable = LuaBinder.bindTable(engine, fileSystem.readHandle(), IModFiles.class);
        ILuaTable exportsTable = LuaBinder.bindTable(engine, fileSystem.exportHandle(), IModExports.class);
        table.rawset("Files", args -> filesTable);
        table.rawset("files", table.rawget("Files"));
        table.rawset("Exports", args -> exportsTable);
        table.rawset("exports", table.rawget("Exports"));
    }
}

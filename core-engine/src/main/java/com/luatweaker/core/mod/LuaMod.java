package com.luatweaker.core.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulates an active, autonomous LuaMod instance.
 */
public class LuaMod {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final LuaModManifest manifest;
    private final File modFileOrDir;
    private final File configFile;
    private final Map<String, ILuaTable> exportedApis = new ConcurrentHashMap<>();
    private final Map<String, LuaMod> loadedModsRegistry;

    private final String environment;

    public LuaMod(@NotNull LuaModManifest manifest,
                  @NotNull File modFileOrDir,
                  @NotNull String environment,
                  @NotNull Map<String, LuaMod> loadedModsRegistry,
                  @NotNull File luamodsDir) {
        this.manifest = manifest;
        this.modFileOrDir = modFileOrDir;
        this.environment = environment;
        this.loadedModsRegistry = loadedModsRegistry;

        // Configs live NEXT TO the luamods dir (run/luaconfig/<mod_id>.json).
        // Resolve from the absolute luamods path so GetConfig works regardless of
        // the process working directory (a relative "luaconfig" silently returned
        // an empty table and every cfg.mana / cfg.skills access crashed).
        File configDir = new File(luamodsDir.getAbsoluteFile().getParentFile(), "luaconfig");
        if (!configDir.exists()) configDir.mkdirs();
        this.configFile = new File(configDir, manifest.id() + ".json");
    }

    public @NotNull LuaModManifest getManifest() {
        return manifest;
    }

    public @NotNull File getModFileOrDir() {
        return modFileOrDir;
    }

    public @NotNull File getConfigFile() {
        return configFile;
    }

    public @NotNull Map<String, ILuaTable> getExportedApis() {
        return exportedApis;
    }

    /**
     * Ensures the mod config exists and contains every key from
     * {@code default_config.json}: the file is created when missing, and new
     * default keys are deep-merged into an existing config (user values win).
     */
    public void ensureConfigFile(@Nullable String defaultConfigJson) {
        if (defaultConfigJson == null || defaultConfigJson.isBlank()) return;
        try {
            if (!configFile.exists()) {
                Files.writeString(configFile.toPath(), defaultConfigJson, StandardCharsets.UTF_8);
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                        "[LuaMod][" + manifest.id() + "] Created default config: " + configFile.getAbsolutePath());
                return;
            }
            String existing = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
            String merged = mergeConfigs(existing, defaultConfigJson);
            if (!merged.equals(existing)) {
                Files.writeString(configFile.toPath(), merged, StandardCharsets.UTF_8);
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                        "[LuaMod][" + manifest.id() + "] Merged new default config keys into: " + configFile.getAbsolutePath());
            }
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[LuaMod][" + manifest.id() + "] Failed to write default config: " + e.getMessage());
        }
    }

    /**
     * Deep-merges the default config JSON into the existing user config JSON.
     * User values always win; keys present only in the defaults are added.
     * Malformed input falls back to the well-formed side.
     */
    public static String mergeConfigs(@NotNull String existingJson, @NotNull String defaultJson) {
        JsonObject existing = tryParse(existingJson);
        JsonObject defaults = tryParse(defaultJson);
        if (existing == null) return defaultJson;
        if (defaults == null) return existingJson;
        deepMerge(defaults, existing);
        return GSON.toJson(defaults);
    }

    private static JsonObject tryParse(String json) {
        try {
            return GSON.fromJson(json, JsonObject.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static void deepMerge(JsonObject base, JsonObject override) {
        for (Map.Entry<String, com.google.gson.JsonElement> entry : override.entrySet()) {
            com.google.gson.JsonElement value = entry.getValue();
            if (value.isJsonObject() && base.has(entry.getKey())
                    && base.get(entry.getKey()).isJsonObject()) {
                deepMerge(base.getAsJsonObject(entry.getKey()), value.getAsJsonObject());
            } else {
                base.add(entry.getKey(), value);
            }
        }
    }

    /**
     * Binds the 'mod' table into the Lua Engine environment for this LuaMod.
     */
    public ILuaTable createModTable(@NotNull ILuaEngine engine) {
        ILuaTable table = engine.createTable();

        table.rawset("ID", engine.wrapString(manifest.id()));
        table.rawset("Name", engine.wrapString(manifest.name()));
        table.rawset("Version", engine.wrapString(manifest.version()));
        table.rawset("Author", engine.wrapString(manifest.author()));

        // mod:GetConfig()
        table.rawset("GetConfig", args -> {
            if (configFile.exists()) {
                try {
                    String content = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
                    JsonObject json = GSON.fromJson(content, JsonObject.class);
                    if (json != null) {
                        return engine.toLuaValue(GSON.fromJson(json, Map.class));
                    }
                    LuaTweakerLog.get().warn(LogStage.SYSTEM,
                            "[LuaMod][" + manifest.id() + "] Config parse returned null: " + configFile.getAbsolutePath());
                } catch (Exception e) {
                    LuaTweakerLog.get().error(LogStage.SYSTEM,
                            "[LuaMod][" + manifest.id() + "] Failed to read config " + configFile.getAbsolutePath() + ": " + e.getMessage());
                }
            } else {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[LuaMod][" + manifest.id() + "] Config file missing: " + configFile.getAbsolutePath());
            }
            return engine.createTable();
        });
        table.rawset("getConfig", table.rawget("GetConfig"));

        // mod:ExportAPI("ApiName", apiTable)
        table.rawset("ExportAPI", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) throw new IllegalArgumentException("mod:ExportAPI requires (apiName, apiTable)");
            String apiName = args[off].asString();
            ILuaValue apiVal = args[off + 1];
            if (apiVal instanceof ILuaTable apiTable) {
                exportedApis.put(apiName, apiTable);
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                        "[LuaMod][" + manifest.id() + "] Exported IPC API: " + apiName);
            }
            return table;
        });
        table.rawset("exportAPI", table.rawget("ExportAPI"));

        // mod:ImportAPI("target_mod_id", "ApiName")
        table.rawset("ImportAPI", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) throw new IllegalArgumentException("mod:ImportAPI requires (targetModId, apiName)");
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
                    "[LuaMod][" + manifest.id() + "] ImportAPI failed: target '" + targetId + "' api '" + apiName + "' not found.");
            return engine.nilValue();
        });
        table.rawset("importAPI", table.rawget("ImportAPI"));

        table.rawset("IsClient", args -> engine.wrapBoolean("client".equalsIgnoreCase(environment) || "universal".equalsIgnoreCase(environment)));
        table.rawset("isClient", table.rawget("IsClient"));

        table.rawset("IsServer", args -> engine.wrapBoolean("server".equalsIgnoreCase(environment) || "universal".equalsIgnoreCase(environment)));
        table.rawset("isServer", table.rawget("IsServer"));

        // mod:GetUpdateStatus([modId]) - read-only view over the update status
        // cached by the engine's update checker (module-update). The checker runs
        // entirely on the Java side; this method only reads its result table.
        table.rawset("GetUpdateStatus", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
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
                    "[LuaMod][" + manifest.id() + "] GetUpdateStatus: UpdateServiceImpl not registered (module-update missing).");
            return engine.nilValue();
        });
        table.rawset("getUpdateStatus", table.rawget("GetUpdateStatus"));

        return table;
    }
}

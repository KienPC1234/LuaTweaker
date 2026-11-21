package com.luatweaker.core.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Encapsulates an active, autonomous LuaMod instance.
 */
public class LuaMod {

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Log prefix shared by every per-mod diagnostic line. */
    static final String LOG_PREFIX = "[LuaMod][";

    private final LuaModManifest manifest;
    private final File modFileOrDir;
    private final File configFile;
    private final File exportsRoot;
    private final Map<String, ILuaTable> exportedApis = new ConcurrentHashMap<>();
    private final Map<String, LuaMod> loadedModsRegistry;

    private final String environment;

    private final AtomicReference<ILuaTable> modTable = new AtomicReference<>();

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
        if (!configDir.exists()) { configDir.mkdirs(); }
        Path configPath = LuaModPathSecurity.resolveContained(configDir.toPath(), manifest.id() + ".json");
        this.configFile = configPath.toFile();

        // Per-mod export sandbox: gameDir/luatweaker/exports/<mod_id>/. Resolved
        // from the luamods path so it works regardless of the process cwd.
        File gameRoot = luamodsDir.getAbsoluteFile().getParentFile();
        if (gameRoot == null) {
            throw new IllegalArgumentException("LuaMods directory has no parent: " + luamodsDir);
        }
        this.exportsRoot = new File(new File(gameRoot, "luatweaker"), "exports");
    }

    public @NotNull LuaModManifest getManifest() {
        return manifest;
    }

    /** Returns the mod table bound into the Lua environment, or null before createModTable(). */
    @Nullable
    public ILuaTable getModTable() {
        return modTable.get();
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
        if (defaultConfigJson == null || defaultConfigJson.isBlank()) {
            return;
        }
        try {
            if (!configFile.exists()) {
                Files.writeString(configFile.toPath(), defaultConfigJson, StandardCharsets.UTF_8);
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                        LOG_PREFIX + manifest.id() + "] Created default config: " + configFile.getAbsolutePath());
                return;
            }
            String existing = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
            String merged = mergeConfigs(existing, defaultConfigJson);
            if (!merged.equals(existing)) {
                Files.writeString(configFile.toPath(), merged, StandardCharsets.UTF_8);
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                        LOG_PREFIX + manifest.id() + "] Merged new default config keys into: " + configFile.getAbsolutePath());
            }
        } catch (IOException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    LOG_PREFIX + manifest.id() + "] Failed to write default config: " + e.getMessage());
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
        if (existing == null) { return defaultJson; }
        if (defaults == null) { return existingJson; }
        deepMerge(defaults, existing);
        return GSON.toJson(defaults);
    }

    private static JsonObject tryParse(String json) {
        try {
            return GSON.fromJson(json, JsonObject.class);
        } catch (JsonSyntaxException e) {
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
        ILuaTable table = new ModTableBuilder(engine, manifest, configFile, exportedApis,
                loadedModsRegistry, environment, modFileOrDir, exportsRoot).build();
        modTable.set(table);
        return table;
    }
}

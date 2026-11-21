package com.luatweaker.core.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads and writes the editable per-mod configs
 * ({@code luaconfig/<mod_id>.json}).
 */
final class ModConfigStore {
    private ModConfigStore() {}

    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Resolves the editable config file for a mod (may not exist yet). */
    static @NotNull File configFileFor(@NotNull File luamodsDir, @NotNull String modId) {
        File configDir = new File(luamodsDir.getAbsoluteFile().getParentFile(), "luaconfig");
        if (!configDir.isDirectory()) {
            configDir.mkdirs();
        }
        Path safe = LuaModPathSecurity.resolveContained(configDir.toPath(), modId + ".json");
        return safe.toFile();
    }

    /**
     * Reads a mod's editable config: the live {@code luaconfig/<id>.json} when
     * present, otherwise the shipped {@code default_config.json} (directory mods
     * only), otherwise {@code null} when the mod has no config at all.
     */
    static @Nullable String readConfigFor(@NotNull File luamodsDir, @NotNull String modId) {
        File live = configFileFor(luamodsDir, modId);
        try {
            if (live.isFile()) {
                return Files.readString(live.toPath(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[LuaModManager] Failed to read config for '" + modId + "': " + e.getMessage());
            return null;
        }
        for (DiscoveredMod found : ModDiscovery.discoverMods(luamodsDir)) {
            if (found.manifest().id().equals(modId)) {
                return found.defaultConfig();
            }
        }
        return null;
    }

    /**
     * Validates and saves a mod's editable config. The content must be a JSON
     * object so a typo cannot brick the mod's next load. The previous live
     * file is kept as {@code <id>.json.bak} (single backup) before overwriting.
     *
     * @return {@code null} on success, otherwise a human-readable error
     */
    static @Nullable String writeConfigFor(@NotNull File luamodsDir, @NotNull String modId,
            @NotNull String json) {
        JsonElement parsed;
        try {
            parsed = GSON.fromJson(json, JsonElement.class);
        } catch (JsonSyntaxException e) {
            return "Invalid JSON: " + e.getMessage();
        }
        if (parsed == null || !parsed.isJsonObject()) {
            return "Config must be a JSON object ({...}).";
        }
        File live = configFileFor(luamodsDir, modId);
        try {
            File parent = live.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            if (live.isFile()) {
                Files.copy(live.toPath(), live.toPath().resolveSibling(live.getName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(live.toPath(), PRETTY_GSON.toJson(parsed), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Failed to save config: " + e.getMessage();
        }
        LuaTweakerLog.get().info(LogStage.SYSTEM, "[LuaModManager] Saved config for '" + modId + "'.");
        return null;
    }
}

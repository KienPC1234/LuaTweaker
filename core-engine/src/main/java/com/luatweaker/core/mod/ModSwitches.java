package com.luatweaker.core.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads, writes and flips the per-mod on/off switches
 * ({@code mods_manager.json}), plus mod deletion.
 *
 * <p>Disabling cascades: every mod that (transitively) requires the target
 * through a non-optional dependency is disabled too, so the next load cannot
 * strand a dependent. Enabling flips only the one mod; missing dependencies
 * are reported at load time instead of being auto-enabled.</p>
 */
final class ModSwitches {
    private ModSwitches() {}

    private static final String MODS_MANAGER_FILE = "mods_manager.json";
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Reads the on/off switches; unknown ids default to enabled. Never writes. */
    static @NotNull Map<String, Boolean> readModsState(@NotNull File luamodsDir) {
        Map<String, Boolean> modsState = Collections.synchronizedMap(new LinkedHashMap<>());
        File modsManagerFile = new File(luamodsDir, MODS_MANAGER_FILE);
        if (!modsManagerFile.exists()) {
            return modsState;
        }
        try {
            String content = Files.readString(modsManagerFile.toPath(), StandardCharsets.UTF_8);
            Map<String, Boolean> parsed = GSON.fromJson(content, Map.class);
            if (parsed != null) {
                for (Map.Entry<String, Boolean> entry : parsed.entrySet()) {
                    modsState.put(entry.getKey(), Boolean.TRUE.equals(entry.getValue()));
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Failed to read mods_manager.json: " + e.getMessage());
        }
        return modsState;
    }

    /** Persists the on/off switches. */
    static void writeModsState(@NotNull File luamodsDir, @NotNull Map<String, Boolean> modsState) {
        try {
            Files.writeString(new File(luamodsDir, MODS_MANAGER_FILE).toPath(),
                    PRETTY_GSON.toJson(modsState), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Failed to write mods_manager.json: " + e.getMessage());
        }
    }

    /**
     * Flips a mod's on/off switch and persists {@code mods_manager.json}.
     *
     * @return ids whose switch actually changed, in discovery order
     */
    static @NotNull List<String> applySwitch(@NotNull File luamodsDir,
            @NotNull String modId, boolean enabled) {
        List<DiscoveredMod> discovered = knownDiscoveredMods(luamodsDir, modId);
        if (discovered.isEmpty()) {
            return List.of();
        }
        Map<String, Boolean> state = readModsState(luamodsDir);
        for (DiscoveredMod known : discovered) {
            state.putIfAbsent(known.manifest().id(), true);
        }
        List<String> changed = enabled ? enableSingleMod(state, modId)
                : disableCascade(discovered, state, modId);
        if (!changed.isEmpty()) {
            writeModsState(luamodsDir, state);
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "[LuaModManager] Switch " + (enabled ? "ON" : "OFF") + ": " + String.join(", ", changed));
        }
        return List.copyOf(changed);
    }

    private static @NotNull List<String> enableSingleMod(@NotNull Map<String, Boolean> state,
            @NotNull String modId) {
        if (Boolean.FALSE.equals(state.get(modId))) {
            state.put(modId, true);
            return List.of(modId);
        }
        return List.of();
    }

    private static @NotNull List<String> disableCascade(@NotNull List<DiscoveredMod> discovered,
            @NotNull Map<String, Boolean> state, @NotNull String modId) {
        Set<String> toDisable = computeDisableClosure(discovered, modId);
        List<String> changed = new ArrayList<>();
        for (DiscoveredMod found : discovered) {
            String id = found.manifest().id();
            if (toDisable.contains(id) && !Boolean.FALSE.equals(state.get(id))) {
                state.put(id, false);
                changed.add(id);
            }
        }
        return changed;
    }

    /**
     * Previews which mods disabling {@code modId} would switch off: the target
     * plus every mod that (transitively) requires it through a non-optional
     * dependency. In discovery order. Empty when the id is unknown.
     */
    static @NotNull List<String> previewDisableCascade(@NotNull File luamodsDir, @NotNull String modId) {
        List<DiscoveredMod> discovered = knownDiscoveredMods(luamodsDir, modId);
        Set<String> closure = computeDisableClosure(discovered, modId);
        List<String> out = new ArrayList<>();
        for (DiscoveredMod found : discovered) {
            if (closure.contains(found.manifest().id())) {
                out.add(found.manifest().id());
            }
        }
        return List.copyOf(out);
    }

    /** Discovery result, or empty when the directory or the mod id is unknown. */
    private static @NotNull List<DiscoveredMod> knownDiscoveredMods(@NotNull File luamodsDir,
            @NotNull String modId) {
        if (!luamodsDir.isDirectory()) {
            return List.of();
        }
        List<DiscoveredMod> discovered = ModDiscovery.discoverMods(luamodsDir);
        for (DiscoveredMod found : discovered) {
            if (found.manifest().id().equals(modId)) {
                return discovered;
            }
        }
        return List.of();
    }

    private static @NotNull Set<String> computeDisableClosure(
            @NotNull List<DiscoveredMod> discovered, @NotNull String rootId) {
        Set<String> closure = new LinkedHashSet<>();
        closure.add(rootId);
        boolean grown = true;
        while (grown) {
            grown = false;
            for (DiscoveredMod found : discovered) {
                String id = found.manifest().id();
                if (closure.contains(id)) {
                    continue;
                }
                for (ModDependency dep : found.manifest().dependencies()) {
                    if (!dep.optional() && closure.contains(dep.id())) {
                        closure.add(id);
                        grown = true;
                        break;
                    }
                }
            }
        }
        return closure;
    }

    /**
     * Deletes a mod from disk (directory mods recursively, zip mods as files)
     * and drops its on/off switch. There is no undo; the UI confirms first.
     *
     * @return {@code null} on success, otherwise a human-readable error
     */
    static String deleteMod(@NotNull File luamodsDir, @NotNull String modId) {
        File found = null;
        for (DiscoveredMod discovered : ModDiscovery.discoverMods(luamodsDir)) {
            if (discovered.manifest().id().equals(modId)) {
                found = discovered.file();
                break;
            }
        }
        if (found == null) {
            return "Mod '" + modId + "' is not installed.";
        }
        try {
            if (found.isDirectory()) {
                deleteRecursively(found.toPath());
            } else if (!found.delete()) {
                return "Failed to delete " + found.getName() + ".";
            }
        } catch (IOException e) {
            return "Failed to delete '" + modId + "': " + e.getMessage();
        }
        Map<String, Boolean> state = readModsState(luamodsDir);
        if (state.remove(modId) != null) {
            writeModsState(luamodsDir, state);
        }
        LuaTweakerLog.get().info(LogStage.SYSTEM, "[LuaModManager] Deleted mod '" + modId + "'.");
        return null;
    }

    private static void deleteRecursively(@NotNull Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.delete(path);
            }
        }
    }
}

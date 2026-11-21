package com.luatweaker.core.mod;

import com.luatweaker.api.vm.ILuaEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Autonomous LuaMod Manager: scanning, sorting dependencies,
 * mounting assets/data, and executing single entrypoint main.lua for each mod in luamods/.
 *
 * <p>Thin facade over focused collaborators: {@link ModDiscovery} scans,
 * {@link ModSwitches} owns the on/off switches and deletion,
 * {@link ModConfigStore} owns editable configs,
 * {@link ModDependencyGates} owns load gates and ordering,
 * {@link ModIntegrationRunner} runs cross-mod entries, and
 * {@link ModLoader} runs the load cycle.</p>
 */
public final class LuaModManager {
    private LuaModManager() {}

    private static final String LUAMODS_DIR_NAME = "luamods";

    private static final Map<String, LuaMod> LOADED_MODS = new ConcurrentHashMap<>();
    private static final Map<String, String> LOAD_ERRORS = new ConcurrentHashMap<>();

    /**
     * Per-mod integration outcomes from the most recent load cycle:
     * {@code modId -> (targetModId -> "loaded ..."/"skipped: ..."/"error: ...")}.
     * Always soft: an integration never fails its owning mod.
     */
    private static final Map<String, Map<String, String>> INTEGRATION_STATUS = new ConcurrentHashMap<>();

    /**
     * Platform environment injected once by the platform layer (NeoForge) before
     * loading. Core-engine stays free of Minecraft/NeoForge imports; when a value
     * is unknown ({@code null}) its gate fails open with a warning instead of
     * skipping every mod that declares the matching constraint.
     */
    private static final AtomicReference<String> ENGINE_VERSION = new AtomicReference<>();
    private static final AtomicReference<String> MINECRAFT_VERSION = new AtomicReference<>();
    private static final AtomicReference<Map<String, String>> INSTALLED_MODS =
            new AtomicReference<>(Collections.emptyMap());

    /** Integration outcomes from the most recent load cycle (empty when nothing declared). */
    public static @NotNull Map<String, Map<String, String>> getIntegrationStatus() {
        Map<String, Map<String, String>> copy =
                Collections.synchronizedMap(new LinkedHashMap<>());
        for (Map.Entry<String, Map<String, String>> entry : INTEGRATION_STATUS.entrySet()) {
            copy.put(entry.getKey(), unmodifiableCopy(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static @NotNull Map<String, String> unmodifiableCopy(@NotNull Map<String, String> inner) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(inner));
    }

    /** Records the running LuaTweaker engine version (from the platform mod container). */
    public static void setEngineVersion(@Nullable String version) {
        ENGINE_VERSION.set(version);
    }

    /** Records the running Minecraft version. */
    public static void setMinecraftVersion(@Nullable String version) {
        MINECRAFT_VERSION.set(version);
    }

    /** Records installed platform mods ({@code modId -> version}) for {@code "mods"} gates. */
    public static void setInstalledMods(@Nullable Map<String, String> mods) {
        INSTALLED_MODS.set(mods == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(mods)));
    }

    /** Clears injected environment state (tests). */
    static void resetEnvironment() {
        ENGINE_VERSION.set(null);
        MINECRAFT_VERSION.set(null);
        INSTALLED_MODS.set(Collections.emptyMap());
    }

    public static @NotNull Map<String, LuaMod> getLoadedMods() {
        return Collections.unmodifiableMap(LOADED_MODS);
    }

    /** Per-mod entrypoint load errors from the most recent load cycle (empty when all mods loaded cleanly). */
    public static @NotNull Map<String, String> getLoadErrors() {
        return Collections.unmodifiableMap(LOAD_ERRORS);
    }

    /** Resolves the editable config file for a mod (may not exist yet). */
    public static @NotNull File configFileFor(@NotNull File targetDir, @NotNull String modId) {
        return ModConfigStore.configFileFor(resolveLuamodsDir(targetDir), modId);
    }

    /**
     * Reads a mod's editable config: the live {@code luaconfig/<id>.json} when
     * present, otherwise the shipped {@code default_config.json} (directory mods
     * only), otherwise {@code null} when the mod has no config at all.
     */
    public static @Nullable String readConfigFor(@NotNull File targetDir, @NotNull String modId) {
        return ModConfigStore.readConfigFor(resolveLuamodsDir(targetDir), modId);
    }

    /**
     * Validates and saves a mod's editable config. The content must be a JSON
     * object so a typo cannot brick the mod's next load. The previous live
     * file is kept as {@code <id>.json.bak} (single backup) before overwriting.
     *
     * @return {@code null} on success, otherwise a human-readable error
     */
    public static @Nullable String writeConfigFor(@NotNull File targetDir, @NotNull String modId,
                                                  @NotNull String json) {
        return ModConfigStore.writeConfigFor(resolveLuamodsDir(targetDir), modId, json);
    }

    /**
     * Lists every discovered mod with its switch, load result, and dependencies.
     * Sorted by id. Safe to call from UIs at any time; triggers no loading.
     */
    public static @NotNull List<ModSummary> listModSummaries(@NotNull File targetDir) {
        File luamodsDir = resolveLuamodsDir(targetDir);
        if (!luamodsDir.isDirectory()) {
            return List.of();
        }
        Map<String, Boolean> state = ModSwitches.readModsState(luamodsDir);
        List<ModSummary> out = new ArrayList<>();
        for (DiscoveredMod found : ModDiscovery.discoverMods(luamodsDir)) {
            LuaModManifest manifest = found.manifest();
            String id = manifest.id();
            out.add(ModSummary.summarize(manifest, !Boolean.FALSE.equals(state.get(id)),
                    LOADED_MODS.containsKey(id), LOAD_ERRORS.get(id),
                    getIntegrationStatus().getOrDefault(id, Map.of())));
        }
        out.sort(Comparator.comparing(ModSummary::id, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(out);
    }

    /**
     * Flips a mod's on/off switch and persists {@code mods_manager.json}.
     *
     * @return ids whose switch actually changed, in discovery order
     */
    public static @NotNull List<String> setModEnabled(@NotNull File targetDir,
                                                      @NotNull String modId, boolean enabled) {
        return ModSwitches.applySwitch(resolveLuamodsDir(targetDir), modId, enabled);
    }

    /**
     * Previews which mods disabling {@code modId} would switch off: the target
     * plus every mod that (transitively) requires it through a non-optional
     * dependency. In discovery order. Empty when the id is unknown.
     */
    public static @NotNull List<String> previewDisableCascade(@NotNull File targetDir,
                                                              @NotNull String modId) {
        return ModSwitches.previewDisableCascade(resolveLuamodsDir(targetDir), modId);
    }

    /**
     * Deletes a mod from disk (directory mods recursively, zip mods as files)
     * and drops its on/off switch. There is no undo; the UI confirms first.
     *
     * @return {@code null} on success, otherwise a human-readable error
     */
    public static @Nullable String deleteMod(@NotNull File targetDir, @NotNull String modId) {
        File luamodsDir = resolveLuamodsDir(targetDir);
        if (!luamodsDir.isDirectory()) {
            return "luamods directory not found.";
        }
        return ModSwitches.deleteMod(luamodsDir, modId);
    }

    /**
     * Resolves the luamods directory from a game/lua target dir: the target
     * itself when it already IS the luamods dir, otherwise its {@code luamods}
     * child. Shared by the loader and the management UI so both read one path.
     */
    public static @NotNull File resolveLuamodsDir(@NotNull File targetDir) {
        return LUAMODS_DIR_NAME.equalsIgnoreCase(targetDir.getName()) ? targetDir : new File(targetDir, LUAMODS_DIR_NAME);
    }

    /** Scans and loads all autonomous LuaMods from the given luamods directory. */
    public static void loadLuaMods(@NotNull File targetDir, @NotNull ILuaEngine engine,
                                   @NotNull String currentEnv) {
        loadLuaMods(targetDir, engine, currentEnv, LuaModLoadObserver.NONE);
    }

    /**
     * Same as {@link #loadLuaMods(File, ILuaEngine, String)} but reports progress to
     * {@code observer}. The platform layer uses it to mirror LuaMod loading on the FML
     * loading screen; core-engine stays free of NeoForge imports.
     */
    public static void loadLuaMods(@NotNull File targetDir, @NotNull ILuaEngine engine,
                                   @NotNull String currentEnv, @NotNull LuaModLoadObserver observer) {
        ModLoader.load(targetDir, engine, currentEnv, observer, LOADED_MODS, LOAD_ERRORS,
                INTEGRATION_STATUS, INSTALLED_MODS.get(), ENGINE_VERSION.get(), MINECRAFT_VERSION.get());
    }

    /**
     * Fires {@code OnDisable} for every loaded mod on the engine that loaded it. Callers must pass
     * the owning engine: firing old-VM closures on a new engine corrupts both VMs. The loaded map
     * is cleared afterwards so a later {@link #loadLuaMods} call does not fire the hooks twice.
     *
     * @param engine engine that loaded the mods
     */
    public static void fireModTeardownHooks(@NotNull ILuaEngine engine) {
        ModLifecycleHooks.fireModTeardownHooks(engine, LOADED_MODS);
    }
}

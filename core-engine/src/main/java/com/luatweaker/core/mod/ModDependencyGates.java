package com.luatweaker.core.mod;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dependency and platform gates for the load cycle, plus dependency-order sorting.
 */
final class ModDependencyGates {
    private ModDependencyGates() {}

    private static final String LOG_MOD = "[LuaModManager] Mod '";

    private static final String ENV_UNIVERSAL = "universal";

    /** True when the mod may load in the current environment. */
    static boolean shouldLoadInEnvironment(@Nullable String modEnv, @Nullable String currentEnv) {
        if (ENV_UNIVERSAL.equals(currentEnv)) {
            return true;
        }
        return ENV_UNIVERSAL.equals(modEnv) || (modEnv != null && modEnv.equals(currentEnv));
    }

    /**
     * Runs every load gate for one mod: required LuaMod dependencies, optional
     * dependency warnings, then platform gates.
     *
     * @return the skip reason when the mod must not load, otherwise {@code null}
     */
    static @Nullable String checkModGates(@NotNull DiscoveredMod found,
            @NotNull Map<String, LuaMod> loadedMods, @NotNull Map<String, String> installedMods,
            @Nullable String engineVersion, @Nullable String minecraftVersion) {
        List<String> missing = new ArrayList<>();
        List<String> missingOptional = new ArrayList<>();
        for (ModDependency dep : found.manifest().dependencies()) {
            String problem = checkLuamodDependency(dep, loadedMods);
            if (problem != null) {
                (dep.optional() ? missingOptional : missing).add(problem);
            }
        }
        for (ModDependency dep : found.manifest().optionalDependencies()) {
            String problem = checkLuamodDependency(dep, loadedMods);
            if (problem != null) {
                missingOptional.add(problem);
            }
        }
        if (!missing.isEmpty()) {
            return "Unsatisfied dependencies: " + String.join("; ", missing);
        }
        if (!missingOptional.isEmpty()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    LOG_MOD + found.manifest().id() + "' optional dependencies not satisfied: "
                            + String.join("; ", missingOptional) + " - continuing.");
        }
        String platformMessage = checkPlatformConstraints(found.manifest(), engineVersion, minecraftVersion);
        if (platformMessage != null) {
            return platformMessage;
        }
        return checkInstalledModDependencies(found.manifest().id(), found.manifest(), installedMods);
    }

    /**
     * Verifies one LuaMod dependency against the already-loaded set.
     *
     * @return {@code null} when satisfied, otherwise a human-readable reason
     *         used both in the skip log and in the load errors
     */
    static @Nullable String checkLuamodDependency(@NotNull ModDependency dep,
            @NotNull Map<String, LuaMod> loadedMods) {
        LuaMod loaded = loadedMods.get(dep.id());
        if (loaded == null) {
            return "LuaMod '" + dep.id() + "' is not loaded";
        }
        String installed = loaded.getManifest().version();
        if (!dep.satisfiedBy(installed)) {
            return "LuaMod '" + dep.describeRequirement() + "' not satisfied (loaded v" + installed + ")";
        }
        return null;
    }

    /**
     * Verifies the manifest {@code luatweaker}/{@code minecraft} condition blocks.
     * Unknown running versions fail open with a warning so version-gated mods keep
     * loading outside the game (tests, datagen) instead of being skipped blindly.
     *
     * @return {@code null} when every declared condition holds, otherwise the reason
     */
    static @Nullable String checkPlatformConstraints(@NotNull LuaModManifest manifest,
            @Nullable String engineVersion, @Nullable String minecraftVersion) {
        String engineProblem = checkEngineGate(manifest, engineVersion);
        if (engineProblem != null) {
            return engineProblem;
        }
        return checkMinecraftGate(manifest, minecraftVersion);
    }

    private static @Nullable String checkEngineGate(@NotNull LuaModManifest manifest,
            @Nullable String engineVersion) {
        VersionConstraint engine = manifest.luatweaker();
        if (engine == null) {
            return null;
        }
        if (engineVersion == null || engineVersion.isBlank()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    LOG_MOD + manifest.id()
                            + "' declares a LuaTweaker version gate but the engine version is unknown - allowing.");
            return null;
        }
        if (!engine.satisfiedBy(engineVersion)) {
            return "Requires LuaTweaker " + engine.describe() + " (running v" + engineVersion + ")";
        }
        return null;
    }

    private static @Nullable String checkMinecraftGate(@NotNull LuaModManifest manifest,
            @Nullable String minecraftVersion) {
        VersionConstraint mc = manifest.minecraft();
        if (mc == null) {
            return null;
        }
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    LOG_MOD + manifest.id()
                            + "' declares a Minecraft version gate but the game version is unknown - allowing.");
            return null;
        }
        if (!mc.satisfiedBy(minecraftVersion)) {
            return "Requires Minecraft " + mc.describe() + " (running " + minecraftVersion + ")";
        }
        return null;
    }

    /**
     * Verifies the manifest {@code mods} block against installed platform mods.
     * Required entries skip the load; optional ones only warn.
     *
     * @return {@code null} when load may proceed, otherwise the skip reason
     */
    static @Nullable String checkInstalledModDependencies(@NotNull String modId,
            @NotNull LuaModManifest manifest, @NotNull Map<String, String> installedMods) {
        if (manifest.modDependencies().isEmpty()) {
            return null;
        }
        List<String> blocking = new ArrayList<>();
        List<String> advisory = new ArrayList<>();
        for (ModDependency dep : manifest.modDependencies()) {
            String installed = installedMods.get(dep.id());
            String problem = platformModProblem(dep, installed);
            if (problem != null) {
                (dep.optional() ? advisory : blocking).add(problem);
            }
        }
        if (!advisory.isEmpty()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    LOG_MOD + modId + "' optional platform mods not satisfied: "
                            + String.join("; ", advisory) + " - continuing.");
        }
        if (!blocking.isEmpty()) {
            return "Unsatisfied platform mods: " + String.join("; ", blocking);
        }
        return null;
    }

    private static @Nullable String platformModProblem(@NotNull ModDependency dep, @Nullable String installed) {
        if (installed == null) {
            return "platform mod '" + dep.id() + "' is not installed";
        }
        if (!dep.satisfiedBy(installed)) {
            return "platform mod '" + dep.describeRequirement() + "' not satisfied (installed " + installed + ")";
        }
        return null;
    }
}

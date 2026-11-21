package com.luatweaker.core.mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Read-only snapshot of one discovered LuaMod for management UIs (list screens,
 * {@code /lt} commands). Combines the manifest, the on/off switch from
 * {@code mods_manager.json}, and the result of the last load cycle.
 */
public record ModSummary(@NotNull String id,
                         @NotNull String name,
                         @NotNull String version,
                         @NotNull String author,
                         @NotNull String environment,
                         boolean enabled,
                         boolean loaded,
                         @Nullable String loadError,
                         @NotNull List<ModDependency> requiredDeps,
                         @NotNull List<ModDependency> optionalDeps,
                         @NotNull List<ModDependency> platformMods,
                         @NotNull Map<String, String> integrations) {

    /** One-line status for list rows, e.g. {@code "ON - loaded v1.2.0"}. */
    public @NotNull String statusLine() {
        if (!enabled) { return "OFF - disabled"; }
        if (loaded) { return "ON - loaded v" + version; }
        if (loadError != null && !loadError.isBlank()) { return "ERROR - " + loadError; }
        return "ON - pending (reload to apply)";
    }

    /** Builds a snapshot, splitting required from optional dependencies. */
    public static @NotNull ModSummary summarize(@NotNull LuaModManifest manifest, boolean enabled,
            boolean loaded, @Nullable String loadError, @NotNull Map<String, String> integrations) {
        List<ModDependency> required = new ArrayList<>();
        List<ModDependency> optional = new ArrayList<>();
        for (ModDependency dep : manifest.dependencies()) {
            (dep.optional() ? optional : required).add(dep);
        }
        for (ModDependency dep : manifest.optionalDependencies()) {
            if (!optional.contains(dep)) {
                optional.add(dep);
            }
        }
        return new ModSummary(manifest.id(), manifest.name(), manifest.version(), manifest.author(),
                manifest.environment(), enabled, loaded, loadError,
                List.copyOf(required), List.copyOf(optional),
                List.copyOf(manifest.modDependencies()), integrations);
    }
}

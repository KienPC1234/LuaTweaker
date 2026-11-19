package com.luatweaker.api.plugin;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static registry for out-of-tree Java plugins.
 *
 * <p>External NeoForge mods call {@link #register} once from their own mod
 * constructor; LuaTweaker then invokes every registered plugin on each engine
 * reload. Intentionally static: the engine is rebuilt on {@code /lt reload},
 * so plugins must survive engine replacement without re-registration.
 */
public final class LuaTweakerPluginRegistry {
    private static final Map<String, ILuaTweakerPlugin> PLUGINS = new ConcurrentHashMap<>();

    private LuaTweakerPluginRegistry() {}

    /** Registers (or replaces) a plugin by {@link ILuaTweakerPlugin#pluginId}. */
    public static void register(@NotNull ILuaTweakerPlugin plugin) {
        if (plugin == null || plugin.pluginId() == null || plugin.pluginId().isBlank()) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                "[PluginRegistry] Refused plugin with blank id.");
            return;
        }
        PLUGINS.put(plugin.pluginId(), plugin);
        LuaTweakerLog.get().info(LogStage.SYSTEM,
            "[PluginRegistry] Registered Java plugin '" + plugin.pluginId()
                + "' v" + plugin.pluginVersion());
    }

    /** Removes a plugin. Used by tests and by mods being unloaded. */
    public static void unregister(@NotNull String pluginId) {
        PLUGINS.remove(pluginId);
    }

    /** Plugins sorted by {@code loadOrder} then id. Deterministic across OSes. */
    public static @NotNull List<ILuaTweakerPlugin> list() {
        List<ILuaTweakerPlugin> result = new ArrayList<>(PLUGINS.values());
        result.sort(Comparator.comparingInt(ILuaTweakerPlugin::loadOrder)
            .thenComparing(ILuaTweakerPlugin::pluginId));
        return Collections.unmodifiableList(result);
    }

    /** Test-only hook to isolate unit tests sharing this static registry. */
    public static void clearForTests() {
        PLUGINS.clear();
    }
}

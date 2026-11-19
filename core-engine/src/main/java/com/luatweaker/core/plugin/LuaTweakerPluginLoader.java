package com.luatweaker.core.plugin;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.plugin.ILuaTweakerPlugin;
import com.luatweaker.api.plugin.LuaTweakerPluginRegistry;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.stub.StubCatalog;
import com.luatweaker.core.vm.NativeModuleResolver;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Discovers and registers out-of-tree Java plugins into a fresh engine.
 *
 * <p>Two discovery sources, explicit registration wins over classpath scanning:
 * <ol>
 *   <li>{@link LuaTweakerPluginRegistry} - an external NeoForge mod calls
 *       {@code register()} once from its own mod constructor.</li>
 *   <li>{@link ServiceLoader} - a plain jar declares
 *       {@code META-INF/services/com.luatweaker.api.plugin.ILuaTweakerPlugin}.</li>
 * </ol>
 * A failing plugin is logged with context and skipped; it never aborts the reload.
 */
public final class LuaTweakerPluginLoader {
    private LuaTweakerPluginLoader() {}

    /** Registers every discovered plugin into the engine, sorted by loadOrder. */
    public static void loadPlugins(@NotNull ILuaEngine engine) {
        for (ILuaTweakerPlugin plugin : discoverPlugins()) {
            try {
                for (Map.Entry<String, String[]> alias : plugin.requireAliases().entrySet()) {
                    NativeModuleResolver.registerAlias(alias.getKey(), alias.getValue());
                }
                for (Map.Entry<String, Class<?>> stub : plugin.stubServices().entrySet()) {
                    if (stub.getKey() != null && stub.getValue() != null) {
                        StubCatalog.register(stub.getKey(), stub.getValue());
                    }
                }
                plugin.registerServices(engine);
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "[PluginLoader] Loaded Java plugin '" + plugin.pluginId()
                        + "' v" + plugin.pluginVersion());
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[PluginLoader] Plugin '" + plugin.pluginId() + "' failed: " + e.getMessage());
            }
        }
    }

    /** Merged, deduplicated, deterministically ordered plugin list. */
    public static @NotNull List<ILuaTweakerPlugin> discoverPlugins() {
        Map<String, ILuaTweakerPlugin> merged = new LinkedHashMap<>();
        for (ILuaTweakerPlugin plugin : discoverServiceLoaderPlugins()) {
            if (plugin != null && plugin.pluginId() != null) {
                merged.putIfAbsent(plugin.pluginId(), plugin);
            }
        }
        for (ILuaTweakerPlugin plugin : LuaTweakerPluginRegistry.list()) {
            if (plugin != null && plugin.pluginId() != null) {
                merged.put(plugin.pluginId(), plugin);
            }
        }
        List<ILuaTweakerPlugin> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparingInt(ILuaTweakerPlugin::loadOrder)
            .thenComparing(ILuaTweakerPlugin::pluginId));
        return result;
    }

    /** Classpath scan only. Never throws: failures are logged loudly. */
    static @NotNull List<ILuaTweakerPlugin> discoverServiceLoaderPlugins() {
        List<ILuaTweakerPlugin> result = new ArrayList<>();
        try {
            for (ILuaTweakerPlugin plugin : ServiceLoader.load(ILuaTweakerPlugin.class)) {
                result.add(plugin);
            }
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                "[PluginLoader] ServiceLoader scan failed: " + e.getMessage());
        }
        return result;
    }
}

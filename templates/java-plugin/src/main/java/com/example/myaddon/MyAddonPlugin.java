package com.example.myaddon;

import com.luatweaker.api.plugin.ILuaTweakerPlugin;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Plugin entrypoint. Discovered via ServiceLoader (Path B) or via
 * {@code LuaTweakerPluginRegistry.register(new MyAddonPlugin())} from a
 * NeoForge mod constructor (Path A). Never patch LuaTweaker core files.
 */
public final class MyAddonPlugin implements ILuaTweakerPlugin {
    @Override
    public @NotNull String pluginId() {
        return "myaddon";
    }

    @Override
    public @NotNull String pluginVersion() {
        return "1.0.0";
    }

    @Override
    public void registerServices(@NotNull ILuaEngine engine) {
        LuaBinder.bind(engine, "MyAddon", new MyAddonApiImpl(), IMyAddonApi.class);
    }

    @Override
    public @NotNull Map<String, String[]> requireAliases() {
        return Map.of("LuaTweaker.MyAddon", new String[]{"MyAddon"});
    }

    @Override
    public @NotNull Map<String, Class<?>> stubServices() {
        return Map.of("MyAddon", IMyAddonApi.class);
    }
}

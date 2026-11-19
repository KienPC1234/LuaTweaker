package com.luatweaker.api.plugin;

import com.luatweaker.api.vm.ILuaEngine;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Extension point for out-of-tree Java plugins.
 *
 * <p>An external NeoForge mod (or any jar on the classpath) implements this interface
 * and either registers the instance via {@link LuaTweakerPluginRegistry#register} from
 * its own mod constructor, or exposes it through {@link java.util.ServiceLoader}
 * ({@code META-INF/services/com.luatweaker.api.plugin.ILuaTweakerPlugin}).
 * LuaTweaker discovers both sources on every engine reload, so plugins never need
 * to patch {@code LuaServiceBootstrap} or fork the repository.
 *
 * <p>Implementations must expose LOW-LEVEL primitives only (GMod/Roblox style:
 * {@code Raycast}, {@code ApplyDamage}, {@code SpawnParticle}, ...), never a
 * high-level one-shot helper that hides gameplay logic from Lua scripts.
 */
public interface ILuaTweakerPlugin {
    /** Stable unique id, e.g. {@code "myaddon"}. Used for dedupe and diagnostics. */
    @NotNull String pluginId();

    /** Human-readable version, e.g. {@code "1.0.0"}. */
    @NotNull String pluginVersion();

    /**
     * Sort key for registration order. Built-in services run first;
     * plugins with a lower value register earlier.
     */
    default int loadOrder() {
        return 1000;
    }

    /**
     * Binds the plugin's Lua API into the engine. Prefer
     * {@code LuaBinder.bind(engine, name, impl, apiClass)} so both
     * {@code camelCase} and {@code PascalCase} names exist and
     * {@code @LuaDefault} keeps trailing args optional.
     */
    void registerServices(@NotNull ILuaEngine engine);

    /**
     * Extra {@code require("LuaTweaker.X")} aliases pointing at globals this
     * plugin registers. Keys are module names, values are ordered global keys
     * (first non-nil wins), mirroring {@code NativeModuleResolver} semantics.
     */
    default @NotNull Map<String, String[]> requireAliases() {
        return Map.of();
    }

    /**
     * API interfaces annotated with {@code @LuaDoc} that should appear in the
     * generated EmmyLua stubs. Keys are stub class names, values are interfaces.
     */
    default @NotNull Map<String, Class<?>> stubServices() {
        return Map.of();
    }
}

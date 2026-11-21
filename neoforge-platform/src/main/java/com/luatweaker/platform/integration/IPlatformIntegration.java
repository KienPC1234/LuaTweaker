package com.luatweaker.platform.integration;

import com.luatweaker.api.vm.ILuaEngine;
import net.neoforged.bus.api.IEventBus;
import org.jetbrains.annotations.NotNull;

/**
 * Standard contract for NeoForge-side third-party mod integrations.
 * Encapsulates mod presence checks and lifecycle hooks without leaking
 * external types into core modules.
 */
public interface IPlatformIntegration {

    /**
     * Unique identifier of the target mod (e.g. "geckolib", "jei", "mekanism").
     */
    @NotNull String modId();

    /**
     * Human-readable display name for status reporting and logs.
     */
    @NotNull String displayName();

    /**
     * Whether the host mod is present and active in the current runtime environment.
     */
    boolean isAvailable();

    /**
     * Hook invoked during mod bus initialization when the host mod is available.
     *
     * @param modBus the NeoForge mod event bus
     */
    default void onModBusSetup(@NotNull IEventBus modBus) {}

    /**
     * Hook invoked when a new Lua engine VM is initialized or reloaded.
     *
     * @param engine the active Lua engine
     */
    default void onEngineSetup(@NotNull ILuaEngine engine) {}

    /**
     * Hook invoked when the server is stopping.
     */
    default void onServerStopping() {}
}

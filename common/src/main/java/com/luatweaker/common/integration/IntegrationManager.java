package com.luatweaker.common.integration;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.common.util.CallbackFirewall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Central manager for third-party platform integrations.
 * Dispatches lifecycle events and logs integration status cleanly.
 */
public final class IntegrationManager {

    private static final Map<String, IPlatformIntegration> INTEGRATIONS = new ConcurrentHashMap<>();
    private static final String CALLBACK_FAILED_MESSAGE = "[IntegrationManager] Callback failed during ";

    private IntegrationManager() {}

    /**
     * Registers a platform integration.
     */
    public static void register(@NotNull IPlatformIntegration integration) {
        INTEGRATIONS.put(integration.modId().toLowerCase(Locale.ROOT), integration);
    }

    /**
     * Looks up an integration by its mod id.
     */
    public static @Nullable IPlatformIntegration get(@NotNull String modId) {
        return INTEGRATIONS.get(modId.toLowerCase(Locale.ROOT));
    }

    /**
     * Checks if a platform integration is registered and its host mod is available.
     */
    public static boolean isAvailable(@NotNull String modId) {
        IPlatformIntegration integration = get(modId);
        return integration != null && integration.isAvailable();
    }

    /**
     * Returns an unmodifiable collection of all registered platform integrations.
     */
    public static Collection<IPlatformIntegration> getAll() {
        return Collections.unmodifiableCollection(INTEGRATIONS.values());
    }

    /**
     * Dispatches platform setup to all available integrations.
     */
    public static void initPlatform() {
        dispatch("onPlatformSetup", IPlatformIntegration::onPlatformSetup);
    }

    /**
     * Dispatches engine setup to all available integrations.
     */
    public static void initEngine(@NotNull ILuaEngine engine) {
        dispatch("onEngineSetup", integration -> integration.onEngineSetup(engine));
    }

    /**
     * Dispatches server stopping notifications to all available integrations.
     */
    public static void onServerStopping() {
        dispatch("onServerStopping", IPlatformIntegration::onServerStopping);
    }

    /**
     * Logs the availability status of all registered integrations with zero obfuscation.
     */
    public static void logStatus() {
        for (IPlatformIntegration integration : INTEGRATIONS.values()) {
            boolean available = integration.isAvailable();
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "[IntegrationManager] " + integration.displayName() + " (" + integration.modId() + "): "
                            + (available ? "ACTIVE" : "DISABLED (host mod absent)"));
        }
    }

    /**
     * Clears all registered integrations (intended for tests).
     */
    public static void clear() {
        INTEGRATIONS.clear();
    }

    private static void dispatch(@NotNull String phase, @NotNull Consumer<IPlatformIntegration> action) {
        for (IPlatformIntegration integration : INTEGRATIONS.values()) {
            if (!integration.isAvailable()) {
                continue;
            }
            CallbackFirewall.run(() -> action.accept(integration),
                    LogStage.SYSTEM,
                    () -> CALLBACK_FAILED_MESSAGE + phase + " for " + integration.displayName());
        }
    }
}

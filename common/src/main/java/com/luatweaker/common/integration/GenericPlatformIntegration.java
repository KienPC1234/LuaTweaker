package com.luatweaker.common.integration;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.common.util.CallbackFirewall;
import dev.architectury.platform.Platform;
import org.jetbrains.annotations.NotNull;

/**
 * Generic soft platform integration checking mod presence via Architectury {@link Platform}.
 */
public record GenericPlatformIntegration(@NotNull String modId, @NotNull String displayName)
        implements IPlatformIntegration {

    private static final String PROBE_FAILED_MESSAGE =
            "[Integration] Mod-presence probe failed (treated as absent): ";

    @Override
    public @NotNull String modId() {
        return modId;
    }

    @Override
    public @NotNull String displayName() {
        return displayName;
    }

    @Override
    public boolean isAvailable() {
        return CallbackFirewall.supply(() -> Platform.isModLoaded(modId),
                Boolean.FALSE, LogStage.SYSTEM, () -> PROBE_FAILED_MESSAGE + modId);
    }
}

package com.luatweaker.platform.integration;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.platform.util.CallbackFirewall;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

/**
 * Generic soft platform integration checking mod presence via {@link ModList}.
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

    /**
     * Isolation firewall: mod metadata comes from other mods' jars, so a half-built or
     * incompatible container can throw anything while it is inspected. An absent mod is
     * reported as {@code false} instead of aborting the caller's integration pass.
     */
    @Override
    public boolean isAvailable() {
        return CallbackFirewall.supply(() -> {
            ModList list = ModList.get();
            return list != null && list.isLoaded(modId);
        }, Boolean.FALSE, LogStage.SYSTEM, () -> PROBE_FAILED_MESSAGE + modId);
    }
}

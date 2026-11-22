package com.luatweaker.common.integration.geckolib;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.common.integration.IPlatformIntegration;
import com.luatweaker.common.util.CallbackFirewall;
import dev.architectury.platform.Platform;
import org.jetbrains.annotations.NotNull;

/**
 * Platform integration hook for GeckoLib animated models, items, and entities.
 */
public class GeckoLibIntegration implements IPlatformIntegration {

    private static final String MOD_ID = "geckolib";
    private static final String ENTRYPOINT_CLASS = "software.bernie.geckolib.GeckoLib";
    private static final String PROBE_FAILED_MESSAGE =
            "[Integration] GeckoLib presence probe failed (treated as absent)";

    @Override
    public @NotNull String modId() {
        return MOD_ID;
    }

    @Override
    public @NotNull String displayName() {
        return "GeckoLib Animated Models";
    }

    @Override
    public boolean isAvailable() {
        return CallbackFirewall.supply(() -> {
            if (Platform.isModLoaded(MOD_ID)) {
                return true;
            }
            try {
                Class.forName(ENTRYPOINT_CLASS);
                return true;
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            }
        }, Boolean.FALSE, LogStage.SYSTEM, () -> PROBE_FAILED_MESSAGE);
    }
}

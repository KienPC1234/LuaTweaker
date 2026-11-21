package com.luatweaker.platform.integration.jei;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.platform.integration.IPlatformIntegration;
import com.luatweaker.platform.util.CallbackFirewall;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

/**
 * Platform integration hook for Just Enough Items (JEI) recipe viewer.
 */
public class JeiIntegration implements IPlatformIntegration {

    private static final String MOD_ID = "jei";
    private static final String PLUGIN_CLASS = "mezz.jei.api.IModPlugin";
    private static final String PROBE_FAILED_MESSAGE =
            "[Integration] JEI presence probe failed (treated as absent)";

    @Override
    public @NotNull String modId() {
        return MOD_ID;
    }

    @Override
    public @NotNull String displayName() {
        return "Just Enough Items (JEI)";
    }

    /**
     * Isolation firewall: the probe reflects over another mod's classes, so an incompatible
     * JEI build surfaces as a linkage failure rather than a clean absence.
     */
    @Override
    public boolean isAvailable() {
        return CallbackFirewall.supply(() -> {
            ModList list = ModList.get();
            if (list != null && list.isLoaded(MOD_ID)) {
                return true;
            }
            try {
                Class.forName(PLUGIN_CLASS);
                return true;
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            }
        }, Boolean.FALSE, LogStage.SYSTEM, () -> PROBE_FAILED_MESSAGE);
    }
}

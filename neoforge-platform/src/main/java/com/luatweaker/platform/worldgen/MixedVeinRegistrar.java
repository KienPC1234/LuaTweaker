package com.luatweaker.platform.worldgen;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Registers the {@code luatweaker:mixed_vein} feature type so datapack
 * configured-features (emitted from {@code Worldgen:AddMixedVein}) can
 * reference it. Hooked from the mod event bus next to
 * {@code NeoForgeDimensionProvider.registerCodecs} (same pattern, no guard:
 * the event delegates to the named registry).
 */
public final class MixedVeinRegistrar {

    public static final String VEIN_TYPE = "luatweaker:mixed_vein";

    private MixedVeinRegistrar() {}

    /** Called from the mod event bus; registers the mixed-vein feature type. */
    public static void registerFeatures(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.FEATURE)) {
            event.register(Registries.FEATURE, ResourceLocation.parse(VEIN_TYPE),
                    () -> new MixedVeinFeature(MixedVeinConfiguration.CODEC));
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "[Worldgen] Registered custom feature type '" + VEIN_TYPE + "'");
        }
    }
}

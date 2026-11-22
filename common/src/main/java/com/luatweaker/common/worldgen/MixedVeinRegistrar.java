package com.luatweaker.common.worldgen;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Registers the {@code luatweaker:mixed_vein} feature type so datapack
 * configured-features (emitted from {@code Worldgen:AddMixedVein}) can
 * reference it.
 */
public final class MixedVeinRegistrar {

    public static final String VEIN_TYPE = "luatweaker:mixed_vein";

    private MixedVeinRegistrar() {}

    /** Registers the mixed-vein feature type. */
    public static void registerFeatures() {
        Registrar<Feature<?>> registrar = RegistrarManager.get("luatweaker").get(Registries.FEATURE);
        registrar.register(ResourceLocation.parse(VEIN_TYPE),
                () -> new MixedVeinFeature(MixedVeinConfiguration.CODEC));
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "[Worldgen] Registered custom feature type '" + VEIN_TYPE + "'");
    }
}

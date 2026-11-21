package com.luatweaker.platform.integration.geckolib;

import com.luatweaker.integration.geo.GeoModelSpec;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Client-side GeckoLib renderer for animated custom entities.
 */
public class LuaGeoEntityRenderer extends GeoEntityRenderer<LuaGeoMob> {
    public LuaGeoEntityRenderer(EntityRendererProvider.Context context, @NotNull GeoModelSpec geoSpec) {
        super(context, new LuaDynamicGeoModel<>(geoSpec, "entity"));
    }
}

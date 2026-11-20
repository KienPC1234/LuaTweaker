package com.luatweaker.platform.gecko;

import com.luatweaker.api.content.GeoModelSpec;
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

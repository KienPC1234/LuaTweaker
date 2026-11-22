package com.luatweaker.common.integration.geckolib;

import com.luatweaker.integration.geo.GeoModelSpec;
import com.luatweaker.common.container.CustomContainerBlockEntity;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Client-side GeckoLib renderer for animated Lua container/machine blocks.
 */
public class LuaGeoBlockRenderer extends GeoBlockRenderer<CustomContainerBlockEntity> {
    public LuaGeoBlockRenderer(@NotNull GeoModelSpec geoSpec) {
        super(new LuaDynamicGeoModel<>(geoSpec, "block"));
    }
}

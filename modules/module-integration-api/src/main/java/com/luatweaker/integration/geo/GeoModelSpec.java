package com.luatweaker.integration.geo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Specification for custom skeletal models and keyframe animations via GeckoLib.
 *
 * <p>Pure data: no GeckoLib/Minecraft imports, so content builders and Lua
 * bindings can carry it while the platform integration applies it.
 *
 * @param modelPath Resource location for the .geo.json model file (e.g. "luatweaker:geo/item/crystal_staff.geo.json"). Required.
 * @param texturePath Resource location for the texture file (e.g. "luatweaker:textures/item/crystal_staff.png").
 *                    Blank/null derives from the model path (same namespace, textures/ + model name + .png).
 * @param animationPath Resource location for the .animation.json file (e.g. "luatweaker:animations/item/crystal_staff.animation.json").
 *                      Blank/null derives from the model path (same namespace, animations/ + model name + .animation.json).
 * @param defaultAnimation Optional default animation name to loop automatically (e.g. "idle"), or null
 */
public record GeoModelSpec(
        @NotNull String modelPath,
        @Nullable String texturePath,
        @Nullable String animationPath,
        @Nullable String defaultAnimation
) {
    public GeoModelSpec {
        if (modelPath == null || modelPath.isBlank()) {
            throw new IllegalArgumentException("modelPath cannot be null or empty");
        }
    }
}

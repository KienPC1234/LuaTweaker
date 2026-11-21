package com.luatweaker.api.particle;

import org.jetbrains.annotations.Nullable;

/**
 * Optional payload for particle types that need parameters (dust color/size, block state, item).
 * Only one payload family is used per call; mixing them is rejected by the platform.
 *
 * @param colorRgb {@code 0xRRGGBB} for {@code minecraft:dust}
 * @param scale    dust scale for {@code minecraft:dust}
 * @param blockId  block id for {@code minecraft:block}/{@code minecraft:falling_dust}
 * @param itemId   item id for {@code minecraft:item}
 */
public record ParticleOptionsSpec(@Nullable Integer colorRgb, @Nullable Double scale,
                                  @Nullable String blockId, @Nullable String itemId) {

    public boolean isEmpty() {
        return colorRgb == null && scale == null && blockId == null && itemId == null;
    }
}

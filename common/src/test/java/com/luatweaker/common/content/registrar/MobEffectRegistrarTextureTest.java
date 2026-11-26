package com.luatweaker.common.content.registrar;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies custom MobEffect icon texture path normalization.
 */
class MobEffectRegistrarTextureTest {

    private static final String DEFAULT_NAMESPACE = "luatweaker";

    @Test
    void resolveTextureLocationNormalizesDefaultAndExplicitPaths() {
        ResourceLocation effectId = ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, "frost_aura");
        assertEquals(
                List.of(
                        ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, "textures/mob_effect/frost_aura.png"),
                        ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, "textures/mob_effect/frost_aura.png"),
                        ResourceLocation.fromNamespaceAndPath("mymod", "gui/frost_icon.png"),
                        ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, "mob_effect/custom.png")),
                List.of(
                        MobEffectRegistrar.resolveTextureLocation(null, effectId),
                        MobEffectRegistrar.resolveTextureLocation("   ", effectId),
                        MobEffectRegistrar.resolveTextureLocation("mymod:gui/frost_icon", effectId),
                        MobEffectRegistrar.resolveTextureLocation("textures/mob_effect/custom.png", effectId)),
                "resolveTextureLocation must derive default mob_effect pngs and normalize explicit paths");
    }
}

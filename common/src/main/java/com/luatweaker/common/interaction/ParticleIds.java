package com.luatweaker.common.interaction;

import net.minecraft.resources.ResourceLocation;

/** Resource-id parsing shared by the particle spawner. */
final class ParticleIds {

    private ParticleIds() {
    }

    static ResourceLocation particle(String particleId) {
        return resource(particleId, "particle");
    }

    static ResourceLocation resource(String raw, String label) {
        ResourceLocation rl = raw.contains(":")
                ? ResourceLocation.tryParse(raw)
                : ResourceLocation.fromNamespaceAndPath("minecraft", raw);
        if (rl == null) {
            throw new IllegalArgumentException("Invalid " + label + " id: '" + raw + "'");
        }
        return rl;
    }
}

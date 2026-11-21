package com.luatweaker.interaction;

import com.luatweaker.api.pal.Platform;
import org.jetbrains.annotations.NotNull;

/** Validation and dispatch shared by every {@link ParticleServiceImpl} shape method. */
final class ParticleSpawnSupport {

    static final int MIN_COUNT = 1;
    static final double ZERO_VELOCITY = 0.0;

    private ParticleSpawnSupport() {
    }

    static void validate(@NotNull String particleId, @NotNull String dimension) {
        if (particleId.isBlank()) {
            throw new IllegalArgumentException("Particle id must not be blank");
        }
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("Particle dimension must not be blank");
        }
    }

    static void requireCount(int count) {
        if (count < MIN_COUNT) {
            throw new IllegalArgumentException("Particle count must be >= " + MIN_COUNT);
        }
    }

    static void spawnAt(String particleId, double[] point, int count, double speed,
                        String dimension) {
        Platform.getInteraction().spawnParticle(dimension, particleId,
                point[0], point[1], point[2],
                ZERO_VELOCITY, ZERO_VELOCITY, ZERO_VELOCITY, count, speed);
    }
}

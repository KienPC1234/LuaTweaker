package com.luatweaker.noise;

import com.luatweaker.noise.internal.DomainWarp;
import com.luatweaker.noise.internal.FBMNoise;
import com.luatweaker.noise.internal.FBMNoise3D;
import com.luatweaker.noise.internal.RidgedNoise;
import com.luatweaker.noise.internal.SimplexNoise;
import com.luatweaker.noise.internal.SimplexNoise3D;
import com.luatweaker.noise.internal.VoronoiNoise;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot of every noise generator derived from one seed. Swapping the whole
 * record keeps a concurrent reader on a single consistent generation.
 */
record NoiseState(
        @NotNull SimplexNoise simplex,
        @NotNull FBMNoise fbm,
        @NotNull RidgedNoise ridged,
        @NotNull DomainWarp domainWarp,
        @NotNull VoronoiNoise voronoi,
        @NotNull SimplexNoise3D simplex3d,
        @NotNull FBMNoise3D fbm3d
) {

    @NotNull
    static NoiseState create(long seed) {
        SimplexNoise simplex = new SimplexNoise(seed);
        SimplexNoise3D simplex3d = new SimplexNoise3D(seed);
        return new NoiseState(
                simplex,
                new FBMNoise(simplex),
                new RidgedNoise(simplex),
                new DomainWarp(simplex),
                new VoronoiNoise(seed),
                simplex3d,
                new FBMNoise3D(simplex3d)
        );
    }
}

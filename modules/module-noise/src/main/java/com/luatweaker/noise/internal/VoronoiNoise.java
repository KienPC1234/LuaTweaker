package com.luatweaker.noise.internal;

/**
 * Voronoi (cellular) noise over a lattice of cells, each cell hosting one
 * jittered point derived from a deterministic hash of the cell coordinates.
 * Immutable and thread-safe.
 */
public final class VoronoiNoise {

    private final long seed;

    public VoronoiNoise(long seed) {
        this.seed = seed;
    }

    /**
     * @param returnType 0 = F1 (nearest point distance), 1 = F2 (second nearest), 2 = F2 - F1
     */
    public double noise(double x, double z, double jitter, int returnType) {
        int xi = (int) Math.floor(x);
        int zi = (int) Math.floor(z);
        double fx = x - xi;
        double fz = z - zi;

        double f1 = Double.MAX_VALUE;
        double f2 = Double.MAX_VALUE;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                double px = dx + hash01(xi + dx, zi + dz) * jitter;
                double pz = dz + hash01(xi + dx + 1000, zi + dz - 500) * jitter;
                double dxd = fx - px;
                double dzd = fz - pz;
                double dist = dxd * dxd + dzd * dzd;
                if (dist < f1) {
                    f2 = f1;
                    f1 = dist;
                } else if (dist < f2) {
                    f2 = dist;
                }
            }
        }
        double result = switch (returnType) {
            case 1 -> Math.sqrt(f2);
            case 2 -> Math.sqrt(f2) - Math.sqrt(f1);
            default -> Math.sqrt(f1);
        };
        return result;
    }

    /** Deterministic hash in [0, 1) derived from seed and integer cell coordinates. */
    private double hash01(int x, int z) {
        long h = seed;
        h ^= (x * 0x9E3779B97F4A7C15L);
        h ^= (z * 0xBF58476D1CE4E5B9L);
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h & 0xFFFFFFFFFFFFFL) / (double) 0x10000000000000L; // 52-bit mantissa -> [0, 1)
    }
}

package com.luatweaker.noise.internal;

/**
 * Deterministic 3D simplex noise (Stefan Gustavson's public-domain reference
 * algorithm: skew factor 1/3, 12-gradient table, permutation shuffle).
 * Pure Java, no mutable state after construction - safe for concurrent
 * chunk-generation threads. Output range: approximately [-1, 1].
 */
public final class SimplexNoise3D {

    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;
    private static final double ZERO = 0.0;

    private static final int PERMUTATION_SIZE = 256;
    private static final int PERMUTATION_MASK = 255;
    private static final int GRADIENT_COUNT = 12;

    private static final int[][] GRAD3 = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };

    private final int[] perm = new int[PERMUTATION_SIZE * 2];

    public SimplexNoise3D(long seed) {
        int[] base = new int[PERMUTATION_SIZE];
        for (int i = 0; i < PERMUTATION_SIZE; i++) {
            base[i] = i;
        }
        java.util.Random random = new java.util.Random(seed);
        for (int i = PERMUTATION_SIZE - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = base[i];
            base[i] = base[j];
            base[j] = swap;
        }
        System.arraycopy(base, 0, perm, 0, PERMUTATION_SIZE);
        System.arraycopy(base, 0, perm, PERMUTATION_SIZE, PERMUTATION_SIZE);
    }

    /** 3D simplex noise. Range approximately [-1, 1]. */
    public double noise(double xin, double yin, double zin) {
        double s = (xin + yin + zin) * F3;
        int i = fastFloor(xin + s);
        int j = fastFloor(yin + s);
        int k = fastFloor(zin + s);
        double t = (i + j + k) * G3;
        double x0 = xin - (i - t);
        double y0 = yin - (j - t);
        double z0 = zin - (k - t);
        int offsets = cornerOffsets(x0, y0, z0);
        int i1 = offsets & 1;
        int j1 = (offsets >> 1) & 1;
        int k1 = (offsets >> 2) & 1;
        int i2 = (offsets >> 3) & 1;
        int j2 = (offsets >> 4) & 1;
        int k2 = (offsets >> 5) & 1;
        double x1 = x0 - i1 + G3;
        double y1 = y0 - j1 + G3;
        double z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2.0 * G3;
        double y2 = y0 - j2 + 2.0 * G3;
        double z2 = z0 - k2 + 2.0 * G3;
        double x3 = x0 - 1.0 + 3.0 * G3;
        double y3 = y0 - 1.0 + 3.0 * G3;
        double z3 = z0 - 1.0 + 3.0 * G3;
        int ii = i & PERMUTATION_MASK;
        int jj = j & PERMUTATION_MASK;
        int kk = k & PERMUTATION_MASK;
        double n0 = corner(ii + perm[jj + perm[kk]], x0, y0, z0);
        double n1 = corner(ii + i1 + perm[jj + j1 + perm[kk + k1]], x1, y1, z1);
        double n2 = corner(ii + i2 + perm[jj + j2 + perm[kk + k2]], x2, y2, z2);
        double n3 = corner(ii + 1 + perm[jj + 1 + perm[kk + 1]], x3, y3, z3);
        return 32.0 * (n0 + n1 + n2 + n3);
    }

    /**
     * Selects the two extra simplex-corner offsets for the point's sub-cell.
     * The six 0/1 offsets are packed into one int (bit 0 = i1 ... bit 5 = k2) so this
     * hot path stays allocation-free.
     */
    private static int cornerOffsets(double x0, double y0, double z0) {
        if (x0 >= y0) {
            if (y0 >= z0) {
                return pack(1, 0, 0, 1, 1, 0);
            }
            if (x0 >= z0) {
                return pack(1, 0, 0, 1, 0, 1);
            }
            return pack(0, 0, 1, 1, 0, 1);
        }
        if (y0 < z0) {
            return pack(0, 0, 1, 0, 1, 1);
        }
        if (x0 < z0) {
            return pack(0, 1, 0, 0, 1, 1);
        }
        return pack(0, 1, 0, 1, 1, 0);
    }

    private static int pack(int i1, int j1, int k1, int i2, int j2, int k2) {
        return i1 | (j1 << 1) | (k1 << 2) | (i2 << 3) | (j2 << 4) | (k2 << 5);
    }

    private double corner(int hash, double x, double y, double z) {
        double t = 0.6 - x * x - y * y - z * z;
        if (t < ZERO) {
            return ZERO;
        }
        t *= t;
        int[] grad = GRAD3[hash % GRADIENT_COUNT];
        return t * t * (grad[0] * x + grad[1] * y + grad[2] * z);
    }

    private static int fastFloor(double x) {
        int i = (int) x;
        return x < i ? i - 1 : i;
    }
}

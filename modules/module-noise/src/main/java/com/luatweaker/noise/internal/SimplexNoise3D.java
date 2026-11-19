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

    private static final int[][] GRAD3 = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };

    private final int[] perm = new int[512];

    public SimplexNoise3D(long seed) {
        int[] base = new int[256];
        for (int i = 0; i < 256; i++) {
            base[i] = i;
        }
        java.util.Random random = new java.util.Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = base[i];
            base[i] = base[j];
            base[j] = swap;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = base[i & 255];
        }
    }

    /** 3D simplex noise. Range approximately [-1, 1]. */
    public double noise(double xin, double yin, double zin) {
        double n0;
        double n1;
        double n2;
        double n3;
        double s = (xin + yin + zin) * F3;
        int i = fastFloor(xin + s);
        int j = fastFloor(yin + s);
        int k = fastFloor(zin + s);
        double t = (i + j + k) * G3;
        double x0 = xin - (i - t);
        double y0 = yin - (j - t);
        double z0 = zin - (k - t);
        int i1;
        int j1;
        int k1;
        int i2;
        int j2;
        int k2;
        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            } else if (x0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1;
            } else {
                i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1;
            }
        } else {
            if (y0 < z0) {
                i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1;
            } else if (x0 < z0) {
                i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1;
            } else {
                i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0;
            }
        }
        double x1 = x0 - i1 + G3;
        double y1 = y0 - j1 + G3;
        double z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2.0 * G3;
        double y2 = y0 - j2 + 2.0 * G3;
        double z2 = z0 - k2 + 2.0 * G3;
        double x3 = x0 - 1.0 + 3.0 * G3;
        double y3 = y0 - 1.0 + 3.0 * G3;
        double z3 = z0 - 1.0 + 3.0 * G3;
        int ii = i & 255;
        int jj = j & 255;
        int kk = k & 255;
        n0 = corner(ii + perm[jj + perm[kk]], x0, y0, z0);
        n1 = corner(ii + i1 + perm[jj + j1 + perm[kk + k1]], x1, y1, z1);
        n2 = corner(ii + i2 + perm[jj + j2 + perm[kk + k2]], x2, y2, z2);
        n3 = corner(ii + 1 + perm[jj + 1 + perm[kk + 1]], x3, y3, z3);
        return 32.0 * (n0 + n1 + n2 + n3);
    }

    private double corner(int hash, double x, double y, double z) {
        double t = 0.6 - x * x - y * y - z * z;
        if (t < 0.0) {
            return 0.0;
        }
        t *= t;
        int[] grad = GRAD3[hash % 12];
        return t * t * (grad[0] * x + grad[1] * y + grad[2] * z);
    }

    private static int fastFloor(double x) {
        int i = (int) x;
        return x < i ? i - 1 : i;
    }
}

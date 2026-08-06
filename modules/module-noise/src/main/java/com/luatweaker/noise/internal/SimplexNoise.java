package com.luatweaker.noise.internal;

/**
 * Deterministic 2D simplex noise (OpenSimplex2S "SuperSimplex", KdotJPG).
 * Pure Java, no mutable state after construction - safe for concurrent
 * chunk-generation threads. Output range: [-1, 1].
 *
 * <p>Faithful port of the public-domain OpenSimplex2S 2D evaluator
 * (standard lattice orientation), seeded through its long-hash scheme so
 * that {@code setSeed()} reproduces identical output.</p>
 */
public final class SimplexNoise {

    private static final long PRIME_X = 0x5205402B9270C86FL;
    private static final long PRIME_Y = 0x598CD327003817B5L;
    private static final long HASH_MULTIPLIER = 0x53A3F72DEEC546F5L;
    private static final double SKEW_2D = 0.366025403784439;
    private static final double UNSKEW_2D = -0.21132486540518713;
    private static final float RSQUARED_2D = 2.0f / 3.0f;
    private static final float NORMALIZER_2D = 0.05481866495625118f;

    private final long seed;
    private static final float[] GRADIENTS_2D = buildGradients2D();

    public SimplexNoise(long seed) {
        this.seed = seed;
    }

    public long getSeed() {
        return seed;
    }

    /** 2D OpenSimplex2S noise, standard lattice orientation. Range [-1, 1]. */
    public double noise(double x, double y) {
        double s = SKEW_2D * (x + y);
        double xs = x + s;
        double ys = y + s;
        return noise2UnskewedBase(xs, ys);
    }

    private double noise2UnskewedBase(double xs, double ys) {
        int xsb = fastFloor(xs);
        int ysb = fastFloor(ys);
        float xi = (float) (xs - xsb);
        float yi = (float) (ys - ysb);

        long xsbp = xsb * PRIME_X;
        long ysbp = ysb * PRIME_Y;

        float t = (xi + yi) * (float) UNSKEW_2D;
        float dx0 = xi + t;
        float dy0 = yi + t;

        float a0 = RSQUARED_2D - dx0 * dx0 - dy0 * dy0;
        float value = (a0 * a0) * (a0 * a0) * grad(seed, xsbp, ysbp, dx0, dy0);

        float a1 = (float) (2 * (1 + 2 * UNSKEW_2D) * (1 / UNSKEW_2D + 2)) * t
                + ((float) (-2 * (1 + 2 * UNSKEW_2D) * (1 + 2 * UNSKEW_2D)) + a0);
        float dx1 = dx0 - (float) (1 + 2 * UNSKEW_2D);
        float dy1 = dy0 - (float) (1 + 2 * UNSKEW_2D);
        value += (a1 * a1) * (a1 * a1) * grad(seed, xsbp + PRIME_X, ysbp + PRIME_Y, dx1, dy1);

        float xmyi = xi - yi;
        if (t < UNSKEW_2D) {
            if (xi + xmyi > 1) {
                float dx2 = dx0 - (float) (3 * UNSKEW_2D + 2);
                float dy2 = dy0 - (float) (3 * UNSKEW_2D + 1);
                float a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp + (PRIME_X << 1), ysbp + PRIME_Y, dx2, dy2);
                }
            } else {
                float dx2 = dx0 - (float) UNSKEW_2D;
                float dy2 = dy0 - (float) (UNSKEW_2D + 1);
                float a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp, ysbp + PRIME_Y, dx2, dy2);
                }
            }
            if (yi - xmyi > 1) {
                float dx3 = dx0 - (float) (3 * UNSKEW_2D + 1);
                float dy3 = dy0 - (float) (3 * UNSKEW_2D + 2);
                float a3 = RSQUARED_2D - dx3 * dx3 - dy3 * dy3;
                if (a3 > 0) {
                    value += (a3 * a3) * (a3 * a3) * grad(seed, xsbp + PRIME_X, ysbp + (PRIME_Y << 1), dx3, dy3);
                }
            } else {
                float dx3 = dx0 - (float) (UNSKEW_2D + 1);
                float dy3 = dy0 - (float) UNSKEW_2D;
                float a3 = RSQUARED_2D - dx3 * dx3 - dy3 * dy3;
                if (a3 > 0) {
                    value += (a3 * a3) * (a3 * a3) * grad(seed, xsbp + PRIME_X, ysbp, dx3, dy3);
                }
            }
        } else {
            if (xi + xmyi < 0) {
                float dx2 = dx0 + (float) (1 + UNSKEW_2D);
                float dy2 = dy0 + (float) UNSKEW_2D;
                float a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp - PRIME_X, ysbp, dx2, dy2);
                }
            } else {
                float dx2 = dx0 - (float) (UNSKEW_2D + 1);
                float dy2 = dy0 - (float) UNSKEW_2D;
                float a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp + PRIME_X, ysbp, dx2, dy2);
                }
            }
            if (yi < xmyi) {
                float dx2 = dx0 + (float) UNSKEW_2D;
                float dy2 = dy0 + (float) (UNSKEW_2D + 1);
                float a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp, ysbp - PRIME_Y, dx2, dy2);
                }
            } else {
                float dx2 = dx0 - (float) UNSKEW_2D;
                float dy2 = dy0 - (float) (UNSKEW_2D + 1);
                float a2 = RSQUARED_2D - dx2 * dx2 - dy2 * dy2;
                if (a2 > 0) {
                    value += (a2 * a2) * (a2 * a2) * grad(seed, xsbp, ysbp + PRIME_Y, dx2, dy2);
                }
            }
        }
        return value;
    }

    private static float grad(long seed, long xsvp, long ysvp, float dx, float dy) {
        long hash = seed ^ xsvp ^ ysvp;
        hash *= HASH_MULTIPLIER;
        hash ^= hash >> (64 - 7 + 1);
        int gi = (int) hash & ((128 - 1) << 1);
        return GRADIENTS_2D[gi] * dx + GRADIENTS_2D[gi | 1] * dy;
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    /** The 128 unit-ish 2D gradients (base 16 cycled), normalized by NORMALIZER_2D. */
    private static float[] buildGradients2D() {
        float[] grad2 = {
                0.38268343236509f, 0.923879532511287f,
                0.923879532511287f, 0.38268343236509f,
                0.923879532511287f, -0.38268343236509f,
                0.38268343236509f, -0.923879532511287f,
                -0.38268343236509f, -0.923879532511287f,
                -0.923879532511287f, -0.38268343236509f,
                -0.923879532511287f, 0.38268343236509f,
                -0.38268343236509f, 0.923879532511287f,
                0.130526192220052f, 0.99144486137381f,
                0.608761429008721f, 0.793353340291235f,
                0.793353340291235f, 0.608761429008721f,
                0.99144486137381f, 0.130526192220051f,
                0.99144486137381f, -0.130526192220051f,
                0.793353340291235f, -0.60876142900872f,
                0.608761429008721f, -0.793353340291235f,
                0.130526192220052f, -0.99144486137381f,
                -0.130526192220052f, -0.99144486137381f,
                -0.608761429008721f, -0.793353340291235f,
                -0.793353340291235f, -0.608761429008721f,
                -0.99144486137381f, -0.130526192220052f,
                -0.99144486137381f, 0.130526192220051f,
                -0.793353340291235f, 0.608761429008721f,
                -0.608761429008721f, 0.793353340291235f,
                -0.130526192220052f, 0.99144486137381f,
        };
        float[] gradients = new float[128 * 2];
        for (int i = 0; i < grad2.length; i++) {
            grad2[i] /= NORMALIZER_2D;
        }
        for (int i = 0, j = 0; i < gradients.length; i++, j++) {
            if (j == grad2.length) j = 0;
            gradients[i] = grad2[j];
        }
        return gradients;
    }
}

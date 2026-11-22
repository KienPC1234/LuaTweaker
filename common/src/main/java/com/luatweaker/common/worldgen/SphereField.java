package com.luatweaker.common.worldgen;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The ellipsoid table used by {@link MixedVeinFeature#doPlace}: four doubles per
 * sphere (center x/y/z, radius), with vanilla OreFeature's overlap merge applied.
 */
final class SphereField {
    static final int STRIDE = 4;
    private static final double ZERO_RADIUS_DELTA = 0.0;
    private static final double NEGATIVE_RADIUS = -1.0;
    private static final double JITTER_DIVISOR = 16.0;
    private static final float HALF = 1.0F;
    private static final float TWO = 2.0F;

    private SphereField() {
    }

    /** Builds the sphere centers/radii exactly like vanilla {@code OreFeature.doPlace}. */
    static double[] build(RandomSource random, MixedVeinConfiguration config,
                          MixedVeinFeature.VeinShape shape) {
        int size = config.size();
        double[] spheres = new double[size * STRIDE];
        for (int index = 0; index < size; index++) {
            float fraction = 1.0f * index / size;
            double centerX = Mth.lerp(fraction, shape.minX(), shape.maxX());
            double centerY = Mth.lerp(fraction, shape.minY(), shape.maxY());
            double centerZ = Mth.lerp(fraction, shape.minZ(), shape.maxZ());
            double jitter = random.nextDouble() * size / JITTER_DIVISOR;
            double radius = ((Mth.sin((float) Math.PI * fraction) + HALF) * jitter + 1.0) / TWO;
            spheres[index * STRIDE] = centerX;
            spheres[index * STRIDE + 1] = centerY;
            spheres[index * STRIDE + 2] = centerZ;
            spheres[index * STRIDE + 3] = radius;
        }
        return spheres;
    }

    /** Vanilla OreFeature's overlap merge: a sphere fully inside another is dropped. */
    static void mergeOverlapping(double[] spheres, int size) {
        for (int left = 0; left < size - 1; left++) {
            if (radius(spheres, left) > ZERO_RADIUS_DELTA) {
                mergeAgainstRemaining(spheres, size, left);
            }
        }
    }

    private static void mergeAgainstRemaining(double[] spheres, int size, int left) {
        for (int right = left + 1; right < size; right++) {
            if (radius(spheres, right) > ZERO_RADIUS_DELTA && contains(spheres, left, right)) {
                dropContained(spheres, left, right);
            }
        }
    }

    private static boolean contains(double[] spheres, int left, int right) {
        double dx = center(spheres, left, 0) - center(spheres, right, 0);
        double dy = center(spheres, left, 1) - center(spheres, right, 1);
        double dz = center(spheres, left, 2) - center(spheres, right, 2);
        double delta = radius(spheres, left) - radius(spheres, right);
        return delta * delta > dx * dx + dy * dy + dz * dz;
    }

    private static void dropContained(double[] spheres, int left, int right) {
        double delta = radius(spheres, left) - radius(spheres, right);
        if (delta > ZERO_RADIUS_DELTA) {
            spheres[right * STRIDE + 3] = NEGATIVE_RADIUS;
        } else {
            spheres[left * STRIDE + 3] = NEGATIVE_RADIUS;
        }
    }

    static double radius(double[] spheres, int index) {
        return spheres[index * STRIDE + 3];
    }

    static double center(double[] spheres, int index, int axis) {
        return spheres[index * STRIDE + axis];
    }
}


package com.luatweaker.interaction;

/** Validation helpers for the particle shape generators. */
final class ShapeGuard {

    static final double MIN_RADIUS = 1.0E-6;
    static final int MAX_POINTS = 4096;

    private ShapeGuard() {
    }

    static void finite(String label, double value) {
        if (Double.isFinite(value)) {
            return;
        }
        throw new IllegalArgumentException(label + " must be a finite number");
    }

    static void positive(String label, double value) {
        if (Double.isFinite(value) && value > 0.0) {
            return;
        }
        throw new IllegalArgumentException(label + " must be a finite number > 0");
    }

    static void nonNegative(String label, double value) {
        if (Double.isFinite(value) && value >= 0.0) {
            return;
        }
        throw new IllegalArgumentException(label + " must be a finite number >= 0");
    }

    static void center(String shape, double x, double y, double z) {
        finite(shape + " centerX", x);
        finite(shape + " centerY", y);
        finite(shape + " centerZ", z);
    }

    static void radius(String shape, double radius) {
        if (Double.isFinite(radius) && radius > MIN_RADIUS) {
            return;
        }
        throw new IllegalArgumentException(
                "Particle:" + shape + " radius must be a finite number > 0");
    }

    static void pointCount(String shape, int points, int minimum) {
        if (points < minimum) {
            throw new IllegalArgumentException("Particle:" + shape + " points must be >= " + minimum);
        }
        if (points > MAX_POINTS) {
            throw new IllegalArgumentException(
                    "Particle:" + shape + " requested " + points + " points (max " + MAX_POINTS + ")");
        }
    }
}

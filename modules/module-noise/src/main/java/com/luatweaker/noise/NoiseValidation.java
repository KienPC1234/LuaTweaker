package com.luatweaker.noise;

/**
 * Argument guards shared by the scalar and batch entry points of the noise service.
 * All failures are {@link IllegalArgumentException} so the Lua binding reports the
 * offending script call instead of a generic runtime error.
 */
final class NoiseValidation {

    /** Hard cap for one batch call: prevents a typo like 100000 x 100000 from exhausting memory. */
    static final int MAX_SAMPLES = 1_048_576;
    private static final int MIN_GRID_SIZE = 1;
    private static final int MIN_OCTAVES = 1;
    private static final int MIN_STEP = 1;

    private NoiseValidation() {}

    static void validateGrid(int sizeX, int sizeY, int sizeZ) {
        if (sizeX < MIN_GRID_SIZE || sizeY < MIN_GRID_SIZE || sizeZ < MIN_GRID_SIZE) {
            throw new IllegalArgumentException("grid sizes must be >= " + MIN_GRID_SIZE + ", got "
                    + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        long samples = (long) sizeX * sizeY * sizeZ;
        if (samples > MAX_SAMPLES) {
            throw new IllegalArgumentException("requested " + samples
                    + " noise samples; the maximum per call is " + MAX_SAMPLES);
        }
    }

    static void validateOctaves(int octaves) {
        if (octaves < MIN_OCTAVES) {
            throw new IllegalArgumentException("octaves must be >= " + MIN_OCTAVES + ", got: " + octaves);
        }
    }

    static void validateStep(String name, int step) {
        if (step < MIN_STEP) {
            throw new IllegalArgumentException(name + " must be >= " + MIN_STEP + ", got: " + step);
        }
    }

    static void validatePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be a positive finite number, got: " + value);
        }
    }
}

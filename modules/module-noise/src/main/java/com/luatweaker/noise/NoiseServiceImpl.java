package com.luatweaker.noise;

import com.luatweaker.api.noise.INoiseService;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.noise.internal.DomainWarp;
import com.luatweaker.noise.internal.FBMNoise;
import com.luatweaker.noise.internal.FBMNoise3D;
import com.luatweaker.noise.internal.RidgedNoise;
import com.luatweaker.noise.internal.SimplexNoise;
import com.luatweaker.noise.internal.SimplexNoise3D;
import com.luatweaker.noise.internal.VoronoiNoise;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Seedable, deterministic noise service.
 *
 * <p>All internal generators are immutable; {@code setSeed} swaps a single
 * volatile snapshot, so concurrent chunk-generation threads read a
 * consistent state without locks.</p>
 */
public class NoiseServiceImpl implements INoiseService {

    private record NoiseState(
            SimplexNoise simplex,
            FBMNoise fbm,
            RidgedNoise ridged,
            DomainWarp domainWarp,
            VoronoiNoise voronoi,
            SimplexNoise3D simplex3d,
            FBMNoise3D fbm3d
    ) {}

    private volatile NoiseState state;

    /** Hard cap for one batch call: prevents a typo like 100000 x 100000 from exhausting memory. */
    private static final int MAX_SAMPLES = 1_048_576;

    public NoiseServiceImpl() {
        setSeed(0L);
    }

    public NoiseServiceImpl(long seed) {
        setSeed(seed);
    }

    @Override
    public double fBm(double x, double z, int octaves, double lacunarity, double gain) {
        validateOctaves(octaves);
        validatePositive("lacunarity", lacunarity);
        validatePositive("gain", gain);
        return state.fbm.noise(x, z, octaves, lacunarity, gain);
    }

    @Override
    public double ridged(double x, double z, int octaves, double frequency, double lacunarity) {
        validateOctaves(octaves);
        validatePositive("frequency", frequency);
        validatePositive("lacunarity", lacunarity);
        return state.ridged.noise(x, z, octaves, frequency, lacunarity);
    }

    @Override
    @NotNull
    public double[] domainWarp(double x, double z, double strength, double frequency) {
        if (!Double.isFinite(strength)) {
            throw new IllegalArgumentException("strength must be finite, got: " + strength);
        }
        validatePositive("frequency", frequency);
        return state.domainWarp.warp(x, z, strength, frequency);
    }

    @Override
    public double voronoi(double x, double z, double jitter, int returnType) {
        if (jitter < 0.0 || jitter > 1.0) {
            throw new IllegalArgumentException("jitter must be between 0.0 and 1.0, got: " + jitter);
        }
        if (returnType < 0 || returnType > 2) {
            throw new IllegalArgumentException("returnType must be 0 (F1), 1 (F2) or 2 (F2-F1), got: " + returnType);
        }
        return state.voronoi.noise(x, z, jitter, returnType);
    }

    @Override
    public double simplex(double x, double z, double frequency) {
        validatePositive("frequency", frequency);
        return state.simplex.noise(x * frequency, z * frequency);
    }

    @Override
    public double simplex3(double x, double y, double z, double frequency) {
        validatePositive("frequency", frequency);
        return state.simplex3d.noise(x * frequency, y * frequency, z * frequency);
    }

    @Override
    public double fbm3(double x, double y, double z, int octaves, double lacunarity, double gain) {
        validateOctaves(octaves);
        validatePositive("lacunarity", lacunarity);
        validatePositive("gain", gain);
        return state.fbm3d.noise(x, y, z, octaves, lacunarity, gain);
    }

    @Override
    @NotNull
    public double[] fill2D(double x0, double z0, int width, int height, @NotNull ILuaValue spec) {
        validateGrid(width, height, 1);
        NoiseLayer cfg = parseSpec(spec, false);
        // One snapshot: setSeed from another thread must not mix two noise states in one field.
        NoiseState snapshot = state;
        double[] out = new double[width * height];
        int index = 0;
        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                out[index++] = sample2D(snapshot, cfg, x0 + x, z0 + z);
            }
        }
        return out;
    }

    @Override
    @NotNull
    public double[] fill3D(double x0, double y0, double z0, int sizeX, int sizeY, int sizeZ,
                           @NotNull ILuaValue spec) {
        validateGrid(sizeX, sizeY, sizeZ);
        NoiseLayer cfg = parseSpec(spec, true);
        NoiseState snapshot = state;
        double[] out = new double[sizeX * sizeY * sizeZ];
        int index = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    out[index++] = sample3D(snapshot, cfg, x0 + x, y0 + y, z0 + z);
                }
            }
        }
        return out;
    }

    private static double sample2D(NoiseState s, NoiseLayer layer, double x, double z) {
        double sx = x * layer.frequency();
        double sz = z * layer.frequency();
        return switch (layer.kind()) {
            case "simplex" -> s.simplex.noise(sx, sz);
            case "fbm" -> s.fbm.noise(sx, sz, layer.octaves(), layer.lacunarity(), layer.gain());
            // Coordinates are already frequency-scaled, so ridged gets a unit frequency.
            case "ridged" -> s.ridged.noise(sx, sz, layer.octaves(), 1.0, layer.lacunarity());
            case "voronoi" -> s.voronoi.noise(sx, sz, layer.jitter(), layer.voronoiType());
            case "warped_fbm" -> {
                // warpStrength is a world-space displacement in blocks; convert it to noise
                // units, otherwise a value like 40 tears the field into one-block spikes.
                double strength = layer.warpStrength() * layer.frequency();
                double[] warped = s.domainWarp.warp(sx, sz, strength, layer.warpFrequency());
                yield s.fbm.noise(warped[0], warped[1], layer.octaves(), layer.lacunarity(), layer.gain());
            }
            default -> throw new IllegalArgumentException("'" + layer.kind()
                    + "' is not a 2D noise kind; expected one of " + NoiseLayer.KINDS_2D);
        };
    }

    private static double sample3D(NoiseState s, NoiseLayer layer, double x, double y, double z) {
        double sx = x * layer.frequency();
        double sy = y * layer.frequency();
        double sz = z * layer.frequency();
        return switch (layer.kind()) {
            case "simplex3" -> s.simplex3d.noise(sx, sy, sz);
            case "fbm3" -> s.fbm3d.noise(sx, sy, sz, layer.octaves(), layer.lacunarity(), layer.gain());
            default -> throw new IllegalArgumentException("'" + layer.kind()
                    + "' is not a 3D noise kind; expected one of " + NoiseLayer.KINDS_3D);
        };
    }

    private static NoiseLayer parseSpec(@NotNull ILuaValue value, boolean threeDimensional) {
        if (value == null || !value.isTable()) {
            throw new IllegalArgumentException("spec must be a table, got "
                    + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        ILuaTable table = value.asTable();
        String kind = stringField(table, "kind",
                threeDimensional ? "fbm3" : "fbm").toLowerCase(Locale.ROOT);
        int octaves = intField(table, "octaves", 4);
        double lacunarity = doubleField(table, "lacunarity", 2.0);
        double gain = doubleField(table, "gain", 0.5);
        double frequency = doubleField(table, "frequency", 1.0);
        double jitter = doubleField(table, "jitter", 0.8);
        int voronoiType = intField(table, "returnType", 0);
        double warpStrength = doubleField(table, "warpStrength", 20.0);
        double warpFrequency = doubleField(table, "warpFrequency", 1.0);
        try {
            return new NoiseLayer(kind, frequency, octaves, lacunarity, gain,
                    jitter, voronoiType, warpStrength, warpFrequency);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid noise spec: " + e.getMessage(), e);
        }
    }

    private static String stringField(ILuaTable table, String key, String fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) return fallback;
        if (!value.isString()) {
            throw new IllegalArgumentException("spec." + key + " must be a string, got "
                    + value.getClass().getSimpleName());
        }
        return value.asString();
    }

    private static int intField(ILuaTable table, String key, int fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) return fallback;
        if (!value.isNumber()) {
            throw new IllegalArgumentException("spec." + key + " must be a number, got "
                    + value.getClass().getSimpleName());
        }
        return (int) Math.floor(value.asDouble());
    }

    private static double doubleField(ILuaTable table, String key, double fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) return fallback;
        if (!value.isNumber()) {
            throw new IllegalArgumentException("spec." + key + " must be a number, got "
                    + value.getClass().getSimpleName());
        }
        return value.asDouble();
    }

    private static void validateGrid(int sizeX, int sizeY, int sizeZ) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("grid sizes must be >= 1, got "
                    + sizeX + "x" + sizeY + "x" + sizeZ);
        }
        long samples = (long) sizeX * sizeY * sizeZ;
        if (samples > MAX_SAMPLES) {
            throw new IllegalArgumentException("requested " + samples
                    + " noise samples; the maximum per call is " + MAX_SAMPLES);
        }
    }

    @Override
    public void setSeed(long seed) {
        SimplexNoise simplex = new SimplexNoise(seed);
        SimplexNoise3D simplex3d = new SimplexNoise3D(seed);
        state = new NoiseState(
                simplex,
                new FBMNoise(simplex),
                new RidgedNoise(simplex),
                new DomainWarp(simplex),
                new VoronoiNoise(seed),
                simplex3d,
                new FBMNoise3D(simplex3d)
        );
    }

    /**
     * Returns an immutable snapshot of the current noise generators for Java-side evaluation.
     * Several samplers may coexist; a later {@link #setSeed(long)} does not change an existing one.
     */
    @NotNull
    public NoiseSampler createSampler() {
        return new NoiseSampler(state);
    }

    /**
     * Thread-safe native sampler used by terrain specs. Delegates to the same generators as the
     * Lua API, so a field evaluated natively matches {@code Noise:Fill2D}/{@code Fill3D}.
     */
    public static final class NoiseSampler {

        private final NoiseState state;

        private NoiseSampler(@NotNull NoiseState state) {
            this.state = state;
        }

        public double sample2D(@NotNull NoiseLayer layer, double x, double z) {
            if (!layer.is2D()) {
                throw new IllegalArgumentException("'" + layer.kind()
                        + "' is not a 2D noise kind; expected one of " + NoiseLayer.KINDS_2D);
            }
            return NoiseServiceImpl.sample2D(state, layer, x, z);
        }

        public double sample3D(@NotNull NoiseLayer layer, double x, double y, double z) {
            if (layer.is2D()) {
                throw new IllegalArgumentException("'" + layer.kind()
                        + "' is not a 3D noise kind; expected one of " + NoiseLayer.KINDS_3D);
            }
            return NoiseServiceImpl.sample3D(state, layer, x, y, z);
        }
    }

    private static void validateOctaves(int octaves) {
        if (octaves < 1) {
            throw new IllegalArgumentException("octaves must be >= 1, got: " + octaves);
        }
    }

    private static void validatePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be a positive finite number, got: " + value);
        }
    }
}

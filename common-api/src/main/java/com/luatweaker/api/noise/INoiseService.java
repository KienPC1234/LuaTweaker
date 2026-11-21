package com.luatweaker.api.noise;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

/**
 * Seedable, deterministic noise functions for Lua terrain generation.
 * All functions return values in a fixed, documented range and are
 * reproducible for a given {@code setSeed(long)} value.
 */
@LuaDoc(description = "Noise functions for terrain generation (fBm, ridged, domain warp, voronoi, simplex).")
public interface INoiseService {

    @LuaDoc(
        description = "Fractional Brownian Motion noise: sum of simplex octaves, normalized to [-1, 1].",
        params = {LuaDocFragments.Geometry.X_NUMBER, LuaDocFragments.Geometry.Z_NUMBER, "octaves: number - number of octaves (>= 1)",
                  "lacunarity: number - frequency multiplier per octave (default 2.0)",
                  "gain: number - amplitude multiplier per octave (default 0.5)"},
        returnType = LuaDocFragments.Types.NUMBER
    )
    double fBm(double x, double z,
               @LuaDefault("4") int octaves,
               @LuaDefault("2.0") double lacunarity,
               @LuaDefault("0.5") double gain);

    @LuaDoc(
        description = "Ridged multifractal noise, good for mountain ridges. Range [0, ~1].",
        params = {LuaDocFragments.Geometry.X_NUMBER, LuaDocFragments.Geometry.Z_NUMBER, "octaves: number - number of octaves (>= 1)",
                  "frequency: number - base frequency (default 1.0)",
                  "lacunarity: number - frequency multiplier per octave (default 2.0)"},
        returnType = LuaDocFragments.Types.NUMBER
    )
    double ridged(double x, double z,
                  @LuaDefault("4") int octaves,
                  @LuaDefault(LuaDocFragments.Defaults.ONE) double frequency,
                  @LuaDefault("2.0") double lacunarity);

    @LuaDoc(
        description = "Domain warping: distorts the input coordinates with fBm before sampling. Returns a table {warpedX, warpedZ}.",
        params = {LuaDocFragments.Geometry.X_NUMBER, LuaDocFragments.Geometry.Z_NUMBER, "strength: number - warp strength (default 20.0)",
                  "frequency: number - warp frequency (default 1.0)"},
        returnType = LuaDocFragments.Types.TABLE
    )
    @NotNull
    double[] domainWarp(double x, double z,
                        @LuaDefault("20.0") double strength,
                        @LuaDefault(LuaDocFragments.Defaults.ONE) double frequency);

    @LuaDoc(
        description = "Voronoi/cellular noise. returnType: 0 = F1 (nearest point distance), 1 = F2 (second-nearest), 2 = F2-F1 (crystal-like edges).",
        params = {LuaDocFragments.Geometry.X_NUMBER, LuaDocFragments.Geometry.Z_NUMBER, "jitter: number - cell jitter 0..1 (default 0.8)",
                  "returnType: number - 0=F1, 1=F2, 2=F2-F1 (default 0)"},
        returnType = LuaDocFragments.Types.NUMBER
    )
    double voronoi(double x, double z,
                   @LuaDefault("0.8") double jitter,
                   @LuaDefault("0") int returnType);

    @LuaDoc(
        description = "Single-octave simplex noise (2D). Range [-1, 1].",
        params = {LuaDocFragments.Geometry.X_NUMBER, LuaDocFragments.Geometry.Z_NUMBER, "frequency: number - input frequency (default 1.0)"},
        returnType = LuaDocFragments.Types.NUMBER
    )
    double simplex(double x, double z,
                   @LuaDefault(LuaDocFragments.Defaults.ONE) double frequency);

    @LuaDoc(
        description = "Seed the noise generator. All functions become reproducible for this seed.",
        params = {"seed: number"}
    )
    void setSeed(long seed);

    @LuaDoc(
        description = "3D single-octave simplex noise for caves, floating islands and overhangs. Range approximately [-1, 1].",
        params = {LuaDocFragments.Geometry.X_NUMBER, "y: number", LuaDocFragments.Geometry.Z_NUMBER, "frequency: number - input frequency (default 1.0)"},
        returnType = LuaDocFragments.Types.NUMBER
    )
    double simplex3(double x, double y, double z,
                    @LuaDefault(LuaDocFragments.Defaults.ONE) double frequency);

    @LuaDoc(
        description = "3D Fractional Brownian Motion noise: sum of simplex octaves, normalized to [-1, 1].",
        params = {LuaDocFragments.Geometry.X_NUMBER, "y: number", LuaDocFragments.Geometry.Z_NUMBER, "octaves: number - number of octaves (>= 1)",
                  "lacunarity: number - frequency multiplier per octave (default 2.0)",
                  "gain: number - amplitude multiplier per octave (default 0.5)"},
        returnType = LuaDocFragments.Types.NUMBER
    )
    double fbm3(double x, double y, double z,
                @LuaDefault("4") int octaves,
                @LuaDefault("2.0") double lacunarity,
                @LuaDefault("0.5") double gain);

    @LuaDoc(
        description = "Sample a 2D noise field over a width x height grid in ONE native call. Returns a "
            + "1-indexed array, index = z * width + x + 1 (x fastest). spec: "
            + "{ kind = 'fbm'|'simplex'|'ridged'|'voronoi'|'warped_fbm', octaves, lacunarity, gain, "
            + "frequency, jitter, returnType, warpStrength (block displacement), warpFrequency }. "
            + "stepX/stepZ space the samples that many blocks apart, so one call can fill a coarse lattice "
            + "(sample width x height at a stride, then interpolate the result in Lua). Use this instead of "
            + "width * height separate calls when building terrain for a chunk.",
        params = {"x0: number - world X of the first sample",
                  "z0: number - world Z of the first sample",
                  "width: number - samples along X (1..1024)",
                  "height: number - samples along Z (1..1024)",
                  "spec: table - noise stack configuration",
                  "stepX: number - block spacing between samples along X (default 1)",
                  "stepZ: number - block spacing between samples along Z (default 1)"},
        returnType = LuaDocFragments.Types.TABLE
    )
    @NotNull
    double[] fill2D(double x0, double z0, int width, int height, @NotNull ILuaValue spec,
                    @LuaDefault("1") int stepX, @LuaDefault("1") int stepZ);

    @LuaDoc(
        description = "Sample a 2D noise field on a coarse lattice and bilinearly expand it to a "
            + "width x height grid in ONE native call. stepX/stepZ are the lattice spacing in blocks and "
            + "must divide width/height; the lattice edge lands on the next chunk's first column, so "
            + "neighbouring chunks join without a seam. Returns a 1-indexed array, "
            + "index = z * width + x + 1 (x fastest). This is Fill2D on the lattice plus interpolation, "
            + "done natively: low-frequency fields (continents, erosion, rivers) keep their shape while "
            + "costing one sample per step cell instead of one per block.",
        params = {"x0: number - world X of the first sample",
                  "z0: number - world Z of the first sample",
                  "width: number - output samples along X (1..1024, multiple of stepX)",
                  "height: number - output samples along Z (1..1024, multiple of stepZ)",
                  "spec: table - noise stack configuration",
                  "stepX: number - lattice spacing along X in blocks (default 4)",
                  "stepZ: number - lattice spacing along Z in blocks (default 4)"},
        returnType = LuaDocFragments.Types.TABLE
    )
    @NotNull
    double[] fill2DSmooth(double x0, double z0, int width, int height, @NotNull ILuaValue spec,
                          @LuaDefault("4") int stepX, @LuaDefault("4") int stepZ);

    @LuaDoc(
        description = "Sample a 3D noise field over a sizeX x sizeY x sizeZ grid in ONE native call. Returns a "
            + "1-indexed array, index = (y * sizeZ + z) * sizeX + x + 1 (x fastest). spec: "
            + "{ kind = 'fbm3'|'simplex3', octaves, lacunarity, gain, frequency }. Use this for caves, "
            + "overhangs and true 3D terrain instead of per-block calls.",
        params = {"x0: number", "y0: number", "z0: number",
                  "sizeX: number - samples along X (1..256)",
                  "sizeY: number - samples along Y (1..256)",
                  "sizeZ: number - samples along Z (1..256)",
                  "spec: table - 3D noise stack configuration"},
        returnType = LuaDocFragments.Types.TABLE
    )
    @NotNull
    double[] fill3D(double x0, double y0, double z0, int sizeX, int sizeY, int sizeZ,
                    @NotNull ILuaValue spec);
}

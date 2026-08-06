package com.luatweaker.api.noise;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
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
        params = {"x: number", "z: number", "octaves: number - number of octaves (>= 1)",
                  "lacunarity: number - frequency multiplier per octave (default 2.0)",
                  "gain: number - amplitude multiplier per octave (default 0.5)"},
        returnType = "number"
    )
    double fBm(double x, double z,
               @LuaDefault("4") int octaves,
               @LuaDefault("2.0") double lacunarity,
               @LuaDefault("0.5") double gain);

    @LuaDoc(
        description = "Ridged multifractal noise, good for mountain ridges. Range [0, ~1].",
        params = {"x: number", "z: number", "octaves: number - number of octaves (>= 1)",
                  "frequency: number - base frequency (default 1.0)",
                  "lacunarity: number - frequency multiplier per octave (default 2.0)"},
        returnType = "number"
    )
    double ridged(double x, double z,
                  @LuaDefault("4") int octaves,
                  @LuaDefault("1.0") double frequency,
                  @LuaDefault("2.0") double lacunarity);

    @LuaDoc(
        description = "Domain warping: distorts the input coordinates with fBm before sampling. Returns a table {warpedX, warpedZ}.",
        params = {"x: number", "z: number", "strength: number - warp strength (default 20.0)",
                  "frequency: number - warp frequency (default 1.0)"},
        returnType = "table"
    )
    @NotNull
    double[] domainWarp(double x, double z,
                        @LuaDefault("20.0") double strength,
                        @LuaDefault("1.0") double frequency);

    @LuaDoc(
        description = "Voronoi/cellular noise. returnType: 0 = F1 (nearest point distance), 1 = F2 (second-nearest), 2 = F2-F1 (crystal-like edges).",
        params = {"x: number", "z: number", "jitter: number - cell jitter 0..1 (default 0.8)",
                  "returnType: number - 0=F1, 1=F2, 2=F2-F1 (default 0)"},
        returnType = "number"
    )
    double voronoi(double x, double z,
                   @LuaDefault("0.8") double jitter,
                   @LuaDefault("0") int returnType);

    @LuaDoc(
        description = "Single-octave simplex noise (2D). Range [-1, 1].",
        params = {"x: number", "z: number", "frequency: number - input frequency (default 1.0)"},
        returnType = "number"
    )
    double simplex(double x, double z,
                   @LuaDefault("1.0") double frequency);

    @LuaDoc(
        description = "Seed the noise generator. All functions become reproducible for this seed.",
        params = {"seed: number"},
        returnType = "void"
    )
    void setSeed(long seed);
}

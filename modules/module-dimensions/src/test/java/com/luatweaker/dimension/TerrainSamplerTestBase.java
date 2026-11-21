package com.luatweaker.dimension;

import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.noise.NoiseLayer;
import com.luatweaker.noise.NoiseServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

/** Shared sampler fixture for {@link TerrainSampler} tests. */
class TerrainSamplerTestBase {

    protected DimensionConfig config;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void createConfig() {
        config = DimensionConfig.defaults("minecraft:test");
    }

    protected TerrainSampler sampler(TerrainSpec spec, long seed) {
        NoiseServiceImpl noise = new NoiseServiceImpl(seed);
        return new TerrainSampler(spec, noise.createSampler(), config);
    }

    protected static TerrainSpec.WeightedLayer layer(String kind, double amplitude, double frequency) {
        NoiseLayer noise = new NoiseLayer(kind, frequency, 4, 2.0, 0.5, 0.8, 0, 20.0, 1.0);
        return new TerrainSpec.WeightedLayer(noise, amplitude);
    }

    protected static TerrainSpec terrain(Integer seaLevel, List<TerrainSpec.WeightedLayer> layers,
                                         List<TerrainSpec.SplinePoint> spline,
                                         List<TerrainSpec.SurfaceRule> rules) {
        return new TerrainSpec(seaLevel, layers, spline, rules, List.of(), null, true);
    }

    protected static TerrainSpec densitySpec(int threshold, List<TerrainSpec.SplinePoint> thresholdSpline) {
        TerrainSpec.Density density = new TerrainSpec.Density(
                List.of(layer("fbm3", 0.0, 0.02)), threshold, 4, 4, thresholdSpline);
        return new TerrainSpec(null, List.of(layer("fbm", 0.0, 0.01)), List.of(), List.of(),
                List.of(), density, true);
    }
}

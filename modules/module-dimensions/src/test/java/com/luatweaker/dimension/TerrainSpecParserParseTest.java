package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that {@link TerrainSpecParser} reads every supported terrain-spec field. */
class TerrainSpecParserParseTest extends TerrainSpecParserTestBase {

    private static final String HEIGHT = "height";
    private static final String FBM = "fbm";
    private static final String FBM3 = "fbm3";

    private record SpecView(int seaLevel, String heightKind, double amplitude, double frequency,
                            int octaves, int splineSize, double firstInput, double firstOutput,
                            double secondOutput, int surfaceRuleCount, String surfaceBlock,
                            int maxDepth, int minY, int maxY, double minSlope,
                            boolean hasDensity, boolean water) {
        static SpecView snapshot(TerrainSpec spec, DimensionConfig config) {
            TerrainSpec.SurfaceRule rule = spec.surfaceRules().get(0);
            return new SpecView(spec.seaLevel(), spec.heightLayers().get(0).noise().kind(),
                    spec.heightLayers().get(0).amplitude(),
                    spec.heightLayers().get(0).noise().frequency(),
                    spec.heightLayers().get(0).noise().octaves(), spec.spline().size(),
                    spec.spline().get(0).input(), spec.spline().get(0).output(),
                    spec.spline().get(1).output(), spec.surfaceRules().size(), rule.block(),
                    rule.maxDepth(), rule.minY(), rule.maxY(), rule.minSlope(),
                    spec.density() != null, spec.water());
        }
    }

    @Test
    void parseReadsEveryField() {
        ILuaTable spec = engine.createTable();
        spec.rawset("seaLevel", 70.0);

        ILuaTable heightLayer = layer(FBM);
        heightLayer.rawset("amp", 40.0);
        heightLayer.rawset("frequency", 0.01);
        heightLayer.rawset("octaves", 6.0);
        spec.rawset(HEIGHT, array(heightLayer));

        ILuaTable firstPoint = engine.createTable();
        firstPoint.rawset("input", -1.0);
        firstPoint.rawset("output", 40.0);
        ILuaTable secondPoint = engine.createTable();
        secondPoint.rawset(1, 1.0);
        secondPoint.rawset(2, 120.0);
        spec.rawset("spline", array(firstPoint, secondPoint));

        ILuaTable rule = engine.createTable();
        rule.rawset("block", "minecraft:stone");
        rule.rawset("maxDepth", 3.0);
        spec.rawset("surface", array(rule));

        TerrainSpec parsed = parse(spec);

        assertEquals(new SpecView(70, FBM, 40.0, 0.01, 6, 2, -1.0, 40.0, 120.0, 1,
                        "minecraft:stone", 3, config.minHeight(), config.maxHeight() - 1, 0.0,
                        false, true),
                SpecView.snapshot(parsed, config),
                "parse must read sea level, height, spline, surface and defaults");
    }

    @Test
    void parseReadsWaterFlag() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, array(layer(FBM)));
        spec.rawset("water", false);

        assertFalse(parse(spec).water(), "parse must honour an explicit water = false flag");
    }

    @Test
    void parseReadsDensitySection() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, array(layer(FBM)));
        ILuaTable densityLayer = layer(FBM3);
        densityLayer.rawset("amp", 1.5);
        ILuaTable density = engine.createTable();
        density.rawset("layers", array(densityLayer));
        density.rawset("threshold", 0.2);
        density.rawset("cellSize", 4.0);
        density.rawset("cellHeight", 8.0);
        ILuaTable point = engine.createTable();
        point.rawset("input", -64.0);
        point.rawset("output", 1.0);
        density.rawset("thresholdSpline", array(point));
        spec.rawset("density", density);

        TerrainSpec parsed = parse(spec);

        assertEquals(List.of(1, FBM3, 1.5, 0.2, 4, 8, 1),
                List.of(parsed.density().layers().size(), parsed.density().layers().get(0).noise().kind(),
                        parsed.density().layers().get(0).amplitude(), parsed.density().threshold(),
                        parsed.density().cellSize(), parsed.density().cellHeight(),
                        parsed.density().thresholdSpline().size()),
                "parse must read every density field");
    }

    @Test
    void parseReadsDensityPlatforms() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, array(layer(FBM)));
        ILuaTable density = engine.createTable();
        density.rawset("layers", array(layer(FBM3)));
        ILuaTable platform = engine.createTable();
        platform.rawset("x", 0.0);
        platform.rawset("z", 8.0);
        platform.rawset("radius", 14.0);
        platform.rawset("bottomY", 168.0);
        platform.rawset("topY", 182.0);
        spec.rawset("platforms", array(platform));
        spec.rawset("density", density);

        TerrainSpec.Platform parsed = parse(spec).platforms().get(0);

        assertEquals(List.of(0, 8, 14, 168, 182),
                List.of(parsed.x(), parsed.z(), parsed.radius(), parsed.bottomY(), parsed.topY()),
                "parse must read every density platform field");
    }

    @Test
    void parseAcceptsDensityOnlySpec() {
        ILuaTable spec = engine.createTable();
        ILuaTable density = engine.createTable();
        density.rawset("layers", array(layer(FBM3)));
        density.rawset("threshold", 0.1);
        spec.rawset("density", density);

        TerrainSpec parsed = parse(spec);

        assertEquals(List.of(true, true),
                List.of(parsed.heightLayers().isEmpty(), parsed.density() != null),
                "a density-only spec must be accepted with no height layers");
    }

    @Test
    void parseKeepsDensityAbsentWhenOmitted() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, array(layer(FBM)));

        assertNull(parse(spec).density(), "omitting the density section must leave density null");
    }

    @Test
    void parseDefaultsWaterToTrue() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, array(layer(FBM)));

        assertTrue(parse(spec).water(), "water must default to true");
    }
}

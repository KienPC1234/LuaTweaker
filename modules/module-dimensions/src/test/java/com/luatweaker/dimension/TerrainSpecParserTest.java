package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TerrainSpecParserTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine engine;
    private DimensionConfig config;

    @BeforeEach
    void setup() {
        engine = LuaEngineFactory.create(false);
        config = DimensionConfig.defaults("minecraft:test");
    }

    private ILuaTable layer(String kind) {
        ILuaTable layer = engine.createTable();
        layer.rawset("kind", kind);
        return layer;
    }

    private ILuaTable array(ILuaTable... entries) {
        ILuaTable table = engine.createTable();
        for (int i = 0; i < entries.length; i++) {
            table.rawset(i + 1, entries[i]);
        }
        return table;
    }

    @Test
    void parse_ReadsEveryField() {
        ILuaTable spec = engine.createTable();
        spec.rawset("seaLevel", 70.0);

        ILuaTable heightLayer = layer("fbm");
        heightLayer.rawset("amp", 40.0);
        heightLayer.rawset("frequency", 0.01);
        heightLayer.rawset("octaves", 6.0);
        spec.rawset("height", array(heightLayer));

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

        TerrainSpec parsed = TerrainSpecParser.parse("minecraft:test", spec, config);

        assertEquals(70, parsed.seaLevel());
        assertEquals(1, parsed.heightLayers().size());
        assertEquals("fbm", parsed.heightLayers().get(0).noise().kind());
        assertEquals(40.0, parsed.heightLayers().get(0).amplitude(), 1e-9);
        assertEquals(0.01, parsed.heightLayers().get(0).noise().frequency(), 1e-9);
        assertEquals(6, parsed.heightLayers().get(0).noise().octaves());
        assertEquals(2, parsed.spline().size());
        assertEquals(-1.0, parsed.spline().get(0).input(), 1e-9);
        assertEquals(40.0, parsed.spline().get(0).output(), 1e-9);
        assertEquals(120.0, parsed.spline().get(1).output(), 1e-9);
        assertEquals(1, parsed.surfaceRules().size());
        assertEquals("minecraft:stone", parsed.surfaceRules().get(0).block());
        assertEquals(3, parsed.surfaceRules().get(0).maxDepth());
        assertEquals(config.minHeight(), parsed.surfaceRules().get(0).minY());
        assertEquals(config.maxHeight() - 1, parsed.surfaceRules().get(0).maxY());
        assertEquals(0.0, parsed.surfaceRules().get(0).minSlope(), 1e-9);
        assertNull(parsed.density());
        assertTrue(parsed.water(), "water must default to true");
    }

    @Test
    void parse_ReadsWaterFlag() {
        ILuaTable spec = engine.createTable();
        spec.rawset("height", array(layer("fbm")));
        spec.rawset("water", false);

        TerrainSpec parsed = TerrainSpecParser.parse("minecraft:test", spec, config);

        assertFalse(parsed.water());
    }

    @Test
    void parse_ReadsDensitySection() {
        ILuaTable spec = engine.createTable();
        spec.rawset("height", array(layer("fbm")));

        ILuaTable densityLayer = layer("fbm3");
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

        TerrainSpec parsed = TerrainSpecParser.parse("minecraft:test", spec, config);

        assertNotNull(parsed.density());
        assertEquals(1, parsed.density().layers().size());
        assertEquals("fbm3", parsed.density().layers().get(0).noise().kind());
        assertEquals(1.5, parsed.density().layers().get(0).amplitude(), 1e-9);
        assertEquals(0.2, parsed.density().threshold(), 1e-9);
        assertEquals(4, parsed.density().cellSize());
        assertEquals(8, parsed.density().cellHeight());
        assertEquals(1, parsed.density().thresholdSpline().size());
    }

    @Test
    void parse_ReadsDensityPlatforms() {
        ILuaTable spec = engine.createTable();
        ILuaTable density = engine.createTable();
        density.rawset("layers", array(layer("fbm3")));
        ILuaTable platform = engine.createTable();
        platform.rawset("x", 0.0);
        platform.rawset("z", 8.0);
        platform.rawset("radius", 14.0);
        platform.rawset("bottomY", 168.0);
        platform.rawset("topY", 182.0);
        spec.rawset("platforms", array(platform));
        spec.rawset("density", density);

        TerrainSpec parsed = TerrainSpecParser.parse("minecraft:test", spec, config);

        assertEquals(1, parsed.platforms().size());
        TerrainSpec.Platform parsedPlatform = parsed.platforms().get(0);
        assertEquals(0, parsedPlatform.x());
        assertEquals(8, parsedPlatform.z());
        assertEquals(14, parsedPlatform.radius());
        assertEquals(168, parsedPlatform.bottomY());
        assertEquals(182, parsedPlatform.topY());
    }

    @Test
    void parse_RejectsPlatformWithoutTopY() {
        ILuaTable spec = engine.createTable();
        ILuaTable density = engine.createTable();
        density.rawset("layers", array(layer("fbm3")));
        ILuaTable platform = engine.createTable();
        platform.rawset("x", 0.0);
        platform.rawset("z", 0.0);
        platform.rawset("radius", 8.0);
        platform.rawset("bottomY", 100.0);
        spec.rawset("platforms", array(platform));
        spec.rawset("density", density);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TerrainSpecParser.parse("minecraft:test", spec, config));
        assertTrue(error.getMessage().contains("topY"), error.getMessage());
    }

    @Test
    void parse_Rejects2DKindInDensity() {
        ILuaTable spec = engine.createTable();
        spec.rawset("height", array(layer("fbm")));
        ILuaTable density = engine.createTable();
        density.rawset("layers", array(layer("fbm")));
        spec.rawset("density", density);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TerrainSpecParser.parse("minecraft:test", spec, config));
        assertTrue(error.getMessage().contains("density.layers[1]"), error.getMessage());
        assertTrue(error.getMessage().contains("3D"), error.getMessage());
    }

    @Test
    void parse_AcceptsDensityOnlySpec() {
        ILuaTable spec = engine.createTable();
        ILuaTable density = engine.createTable();
        density.rawset("layers", array(layer("fbm3")));
        density.rawset("threshold", 0.1);
        spec.rawset("density", density);

        TerrainSpec parsed = TerrainSpecParser.parse("minecraft:test", spec, config);

        assertTrue(parsed.heightLayers().isEmpty());
        assertNotNull(parsed.density());
    }

    @Test
    void parse_RejectsDensityWithoutLayers() {
        ILuaTable spec = engine.createTable();
        spec.rawset("height", array(layer("fbm")));
        ILuaTable density = engine.createTable();
        density.rawset("threshold", 0.1);
        spec.rawset("density", density);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TerrainSpecParser.parse("minecraft:test", spec, config));
        assertTrue(error.getMessage().contains("density.layers"), error.getMessage());
    }

    @Test
    void parse_RejectsDensityCellSizeOutOfRange() {
        ILuaTable spec = engine.createTable();
        spec.rawset("height", array(layer("fbm")));
        ILuaTable density = engine.createTable();
        density.rawset("layers", array(layer("fbm3")));
        density.rawset("cellSize", 20.0);
        spec.rawset("density", density);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TerrainSpecParser.parse("minecraft:test", spec, config));
        assertTrue(error.getMessage().contains("cellSize"), error.getMessage());
    }

    @Test
    void parse_RequiresHeightLayers() {
        ILuaTable spec = engine.createTable();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TerrainSpecParser.parse("minecraft:test", spec, config));
        assertTrue(error.getMessage().contains("height"), error.getMessage());
    }

    @Test
    void parse_RejectsEmptyHeightArray() {
        ILuaTable spec = engine.createTable();
        spec.rawset("height", engine.createTable());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TerrainSpecParser.parse("minecraft:test", spec, config));
        assertTrue(error.getMessage().contains("empty"), error.getMessage());
    }

    @Test
    void parse_Rejects3DKindInHeightLayer() {
        ILuaTable spec = engine.createTable();
        spec.rawset("height", array(layer("fbm3")));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TerrainSpecParser.parse("minecraft:test", spec, config));
        assertTrue(error.getMessage().contains("3D"), error.getMessage());
    }

    @Test
    void parse_RejectsUnknownNoiseKind() {
        ILuaTable spec = engine.createTable();
        spec.rawset("height", array(layer("nope")));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TerrainSpecParser.parse("minecraft:test", spec, config));
        assertTrue(error.getMessage().contains("height[1]"), error.getMessage());
    }

    @Test
    void parse_RejectsNonAscendingSpline() {
        ILuaTable spec = engine.createTable();
        spec.rawset("height", array(layer("fbm")));
        ILuaTable first = engine.createTable();
        first.rawset("input", 1.0);
        first.rawset("output", 10.0);
        ILuaTable second = engine.createTable();
        second.rawset("input", 0.5);
        second.rawset("output", 20.0);
        spec.rawset("spline", array(first, second));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TerrainSpecParser.parse("minecraft:test", spec, config));
        assertTrue(error.getMessage().contains("strictly increasing"), error.getMessage());
    }

    @Test
    void parse_RejectsInvalidSurfaceBlock() {
        ILuaTable spec = engine.createTable();
        spec.rawset("height", array(layer("fbm")));
        ILuaTable rule = engine.createTable();
        rule.rawset("block", "Not A Block");
        spec.rawset("surface", array(rule));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TerrainSpecParser.parse("minecraft:test", spec, config));
        assertTrue(error.getMessage().contains("valid block id"), error.getMessage());
    }

    @Test
    void parse_RejectsSeaLevelOutsideBuildRange() {
        ILuaTable spec = engine.createTable();
        spec.rawset("height", array(layer("fbm")));
        spec.rawset("seaLevel", 4000.0);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TerrainSpecParser.parse("minecraft:test", spec, config));
        assertTrue(error.getMessage().contains("seaLevel"), error.getMessage());
    }

    @Test
    void parse_RejectsNonTableLayerEntry() {
        ILuaTable spec = engine.createTable();
        ILuaTable layers = engine.createTable();
        layers.rawset(1, "fbm");
        spec.rawset("height", layers);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TerrainSpecParser.parse("minecraft:test", spec, config));
        assertTrue(error.getMessage().contains("height[1]"), error.getMessage());
    }

    @Test
    void parse_RejectsNonNumericAmplitude() {
        ILuaTable spec = engine.createTable();
        ILuaTable heightLayer = layer("fbm");
        heightLayer.rawset("amp", "big");
        spec.rawset("height", array(heightLayer));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TerrainSpecParser.parse("minecraft:test", spec, config));
        assertTrue(error.getMessage().contains("height[1].amp"), error.getMessage());
    }
}

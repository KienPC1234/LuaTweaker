package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies terrain-spec rejection for malformed height, density and platform data. */
class TerrainSpecParserValidationTest extends TerrainSpecParserTestBase {

    private static final String HEIGHT = "height";
    private static final String FBM = "fbm";
    private static final String DENSITY = "density";

    @Test
    void parseRejectsPlatformWithoutTopY() {
        ILuaTable spec = engine.createTable();
        ILuaTable density = engine.createTable();
        density.rawset("layers", array(layer("fbm3")));
        ILuaTable platform = engine.createTable();
        platform.rawset("x", 0.0);
        platform.rawset("z", 0.0);
        platform.rawset("radius", 8.0);
        platform.rawset("bottomY", 100.0);
        spec.rawset("platforms", array(platform));
        spec.rawset(DENSITY, density);

        assertTrue(rejection(spec).contains("topY"),
                "a density platform without topY must be rejected");
    }

    @Test
    void parseRejects2DKindInDensity() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, array(layer(FBM)));
        ILuaTable density = engine.createTable();
        density.rawset("layers", array(layer(FBM)));
        spec.rawset(DENSITY, density);

        String message = rejection(spec);

        assertEquals(List.of(true, true),
                List.of(message.contains("density.layers[1]"), message.contains("3D")),
                "a 2D noise kind in density must be rejected with its layer and 3D hint");
    }

    @Test
    void parseRejectsDensityWithoutLayers() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, array(layer(FBM)));
        ILuaTable density = engine.createTable();
        density.rawset("threshold", 0.1);
        spec.rawset(DENSITY, density);

        assertTrue(rejection(spec).contains("density.layers"),
                "a density section without layers must be rejected");
    }

    @Test
    void parseRejectsDensityCellSizeOutOfRange() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, array(layer(FBM)));
        ILuaTable density = engine.createTable();
        density.rawset("layers", array(layer("fbm3")));
        density.rawset("cellSize", 20.0);
        spec.rawset(DENSITY, density);

        assertTrue(rejection(spec).contains("cellSize"),
                "a density cell size outside the supported range must be rejected");
    }

    @Test
    void parseRequiresHeightLayers() {
        ILuaTable spec = engine.createTable();

        assertTrue(rejection(spec).contains(HEIGHT),
                "a terrain spec without height layers or density must be rejected");
    }

    @Test
    void parseRejectsEmptyHeightArray() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, engine.createTable());

        assertTrue(rejection(spec).contains("empty"),
                "an empty height array must be rejected");
    }

    @Test
    void parseRejects3DKindInHeightLayer() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, array(layer("fbm3")));

        assertTrue(rejection(spec).contains("3D"),
                "a 3D noise kind in a height layer must be rejected");
    }

    @Test
    void parseRejectsUnknownNoiseKind() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, array(layer("nope")));

        assertTrue(rejection(spec).contains("height[1]"),
                "an unknown noise kind must be rejected with its layer index");
    }
}

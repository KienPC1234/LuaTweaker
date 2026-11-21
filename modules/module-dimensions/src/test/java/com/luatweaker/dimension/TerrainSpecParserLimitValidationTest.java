package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies terrain-spec rejection for spline, surface, sea level and numeric values. */
class TerrainSpecParserLimitValidationTest extends TerrainSpecParserTestBase {

    private static final String HEIGHT = "height";
    private static final String FBM = "fbm";

    @Test
    void parseRejectsNonAscendingSpline() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, array(layer(FBM)));
        ILuaTable first = engine.createTable();
        first.rawset("input", 1.0);
        first.rawset("output", 10.0);
        ILuaTable second = engine.createTable();
        second.rawset("input", 0.5);
        second.rawset("output", 20.0);
        spec.rawset("spline", array(first, second));

        assertTrue(rejection(spec).contains("strictly increasing"),
                "spline inputs that are not strictly increasing must be rejected");
    }

    @Test
    void parseRejectsInvalidSurfaceBlock() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, array(layer(FBM)));
        ILuaTable rule = engine.createTable();
        rule.rawset("block", "Not A Block");
        spec.rawset("surface", array(rule));

        assertTrue(rejection(spec).contains("valid block id"),
                "a surface rule with an invalid block id must be rejected");
    }

    @Test
    void parseRejectsSeaLevelOutsideBuildRange() {
        ILuaTable spec = engine.createTable();
        spec.rawset(HEIGHT, array(layer(FBM)));
        spec.rawset("seaLevel", 4000.0);

        assertTrue(rejection(spec).contains("seaLevel"),
                "a sea level outside the build range must be rejected");
    }

    @Test
    void parseRejectsNonTableLayerEntry() {
        ILuaTable spec = engine.createTable();
        ILuaTable layers = engine.createTable();
        layers.rawset(1, FBM);
        spec.rawset(HEIGHT, layers);

        assertTrue(rejection(spec).contains("height[1]"),
                "a non-table height layer entry must be rejected with its index");
    }

    @Test
    void parseRejectsNonNumericAmplitude() {
        ILuaTable spec = engine.createTable();
        ILuaTable heightLayer = layer(FBM);
        heightLayer.rawset("amp", "big");
        spec.rawset(HEIGHT, array(heightLayer));

        assertTrue(rejection(spec).contains("height[1].amp"),
                "a non-numeric layer amplitude must be rejected with its field path");
    }
}

package com.luatweaker.math;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies the Lua {@code Curve} table end to end through the real engine binding. */
class CurveLuaBindingTest {

    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(true);
        MathLuaBinding.registerBindings(engine);
    }

    @Test
    void colonCallEvaluatesABezier() {
        engine.executeStringOrThrow("""
                _G._curvePoint = Curve:Bezier(0.5, {
                    { X = 0, Y = 0, Z = 0 },
                    { X = 2, Y = 0, Z = 0 },
                })
                """, "CurveBezierTest");

        assertPoint(1, 0, 0);
    }

    @Test
    void dotCallEvaluatesALine() {
        engine.executeStringOrThrow("""
                _G._curvePoint = Curve.Line(0.25,
                    { X = 0, Y = 0, Z = 0 },
                    { X = 4, Y = 8, Z = 0 })
                """, "CurveLineTest");

        assertPoint(1, 2, 0);
    }

    @Test
    void catmullRomUsesTheFullControlPointList() {
        engine.executeStringOrThrow("""
                _G._curvePoint = Curve:CatmullRom(1.0, {
                    { X = 0, Y = 0, Z = 0 },
                    { X = 1, Y = 0, Z = 0 },
                    { X = 2, Y = 0, Z = 0 },
                })
                """, "CurveCatmullTest");

        assertPoint(2, 0, 0);
    }

    @Test
    void invalidInputFailsLoudly() {
        assertInvalidInputRejected();
    }

    private void assertInvalidInputRejected() {
        assertThrows(IllegalStateException.class,
                () -> engine.executeStringOrThrow(
                        "Curve:Bezier(2.0, { {X=0,Y=0,Z=0}, {X=1,Y=0,Z=0} })",
                        "CurveBadT"),
                "t outside [0,1] must raise through the binding");
        assertThrows(IllegalStateException.class,
                () -> engine.executeStringOrThrow(
                        "Curve:Line(0.5, {X=0,Y=0,Z=0}, {X=1})",
                        "CurveBadPoint"),
                "a point without Z must raise through the binding");
    }

    private void assertPoint(double x, double y, double z) {
        ILuaTable point = engine.getGlobalEnvironment().rawget("_curvePoint").asTable();
        assertEquals(x, point.rawget("X").asDouble(), 1.0E-9, "X mismatch");
        assertEquals(y, point.rawget("Y").asDouble(), 1.0E-9, "Y mismatch");
        assertEquals(z, point.rawget("Z").asDouble(), 1.0E-9, "Z mismatch");
    }
}

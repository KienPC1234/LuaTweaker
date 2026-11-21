package com.luatweaker.math;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Exercises the Lua {@code Region3} table end to end through the real engine binding. */
class Region3LuaBindingTest {

    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(true);
        MathLuaBinding.registerBindings(engine);
    }

    @Test
    void constructsFromVectorsAndAnswersContainsForBothCallStyles() {
        engine.executeStringOrThrow("""
                local region = Region3.new(Vector3.new(0, 60, 0), Vector3.new(16, 80, 16))
                _G._inside = region:Contains(8, 70, 8)
                _G._outside = region.Contains(20, 70, 8)
                _G._corner = region:Contains(Vector3.new(0, 60, 0))
                """, "Region3ContainsTest");

        assertAll("contains must work as a method and as a function",
                () -> Assertions.assertTrue(bool("_inside"), "a point in the middle is inside"),
                () -> Assertions.assertFalse(bool("_outside"), "a point beyond MaxX is outside"),
                () -> Assertions.assertTrue(bool("_corner"), "the min corner is inside"));
    }

    @Test
    void expandReturnsANewRegionAndKeepsTheOriginal() {
        engine.executeStringOrThrow("""
                local region = Region3.FromCoordinates(0, 0, 0, 2, 2, 2)
                local grown = region:Expand(1)
                _G._grownMin = grown.MinX
                _G._originalMin = region.MinX
                _G._volume = region:Volume()
                """, "Region3ExpandTest");

        assertAll("expansion must not mutate the source region",
                () -> assertEquals(-1, number("_grownMin"), 1.0E-9, "expanded region moves its min outward"),
                () -> assertEquals(0, number("_originalMin"), 1.0E-9, "the source region is unchanged"),
                () -> assertEquals(8, number("_volume"), 1.0E-9, "volume of a 2x2x2 box"));
    }

    @Test
    void intersectsUnionAndCenterWork() {
        engine.executeStringOrThrow("""
                local a = Region3.FromCoordinates(0, 0, 0, 10, 10, 10)
                local b = Region3.FromCoordinates(5, 5, 5, 20, 20, 20)
                local far = Region3.FromCoordinates(40, 40, 40, 50, 50, 50)
                _G._overlap = a:Intersects(b)
                _G._apart = a:Intersects(far)
                _G._unionMax = a:Union(b).MaxX
                _G._centerX = a:Center().X
                """, "Region3UnionTest");

        assertAll("set operations must return the expected bounds",
                () -> Assertions.assertTrue(bool("_overlap"), "overlapping regions intersect"),
                () -> Assertions.assertFalse(bool("_apart"), "separated regions do not intersect"),
                () -> assertEquals(20, number("_unionMax"), 1.0E-9, "union takes the larger bound"),
                () -> assertEquals(5, number("_centerX"), 1.0E-9, "center is the midpoint"));
    }

    @Test
    void regionTableIsUsableAsStoredData() {
        engine.executeStringOrThrow("""
                local region = Region3.FromCoordinates(0, 0, 0, 4, 4, 4)
                local copy = Region3.FromCoordinates(region.MinX, region.MinY, region.MinZ,
                    region.MaxX, region.MaxY, region.MaxZ)
                _G._roundTrip = copy:Contains(2, 2, 2)
                """, "Region3RoundTripTest");

        Assertions.assertTrue(bool("_roundTrip"), "a region rebuilt from its fields still contains its middle");
    }

    @Test
    void invalidInputFailsLoudly() {
        assertAll("broken arguments must raise instead of returning a silent default",
                () -> assertThrows(IllegalStateException.class,
                        () -> engine.executeStringOrThrow("Region3.new(Vector3.new(0, 0, 0))",
                                "Region3BadNew"),
                        "a single corner must be rejected"),
                () -> assertThrows(IllegalStateException.class,
                        () -> engine.executeStringOrThrow(
                                "Region3.FromCoordinates(0, 0, 0, 1, 1, 0 / 0)", "Region3BadCoord"),
                        "a non-finite coordinate must be rejected"),
                () -> assertThrows(IllegalStateException.class,
                        () -> engine.executeStringOrThrow(
                                "Region3.FromCoordinates(0, 0, 0, 1, 1, 1):Intersects(Vector3.new(0, 0, 0))",
                                "Region3BadIntersect"),
                        "Intersects requires a Region3 table"));
    }

    private boolean bool(String global) {
        return Boolean.TRUE.equals(engine.getGlobalEnvironment().rawget(global).toJavaObject());
    }

    private double number(String global) {
        return engine.getGlobalEnvironment().rawget(global).asDouble();
    }
}

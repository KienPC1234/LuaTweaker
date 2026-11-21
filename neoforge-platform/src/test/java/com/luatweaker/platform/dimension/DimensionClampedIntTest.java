package com.luatweaker.platform.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Clamped ints must fall back for non-numbers and pin to the range. */
class DimensionClampedIntTest {

    @Test
    void nilFallsBackToDefault() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertEquals(4, DimensionMobSpawner.clampedInt(engine.nilValue(), 4, 1, 8),
                "a nil value must fall back to the default");
    }

    @Test
    void stringFallsBackToDefault() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertEquals(4, DimensionMobSpawner.clampedInt(engine.wrapString("many"), 4, 1, 8),
                "a non-numeric value must fall back to the default");
    }

    @Test
    void belowRangePinsToMinimum() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertEquals(1, DimensionMobSpawner.clampedInt(engine.wrapNumber(-5), 4, 1, 8),
                "a below-range value must pin to the minimum");
    }

    @Test
    void aboveRangePinsToMaximum() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertEquals(8, DimensionMobSpawner.clampedInt(engine.wrapNumber(99), 4, 1, 8),
                "an above-range value must pin to the maximum");
    }
}

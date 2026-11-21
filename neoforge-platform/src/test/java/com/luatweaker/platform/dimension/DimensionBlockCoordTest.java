package com.luatweaker.platform.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Lua numbers must floor to block coordinates and reject non-world values. */
class DimensionBlockCoordTest {

    @Test
    void negativeFractionFloorsDown() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertEquals(-16, DimensionMobSpawner.blockCoord(engine.wrapNumber(-15.1)),
                "negative fractions must floor away from zero");
    }

    @Test
    void wholeNumberIsKept() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertEquals(42, DimensionMobSpawner.blockCoord(engine.wrapNumber(42)),
                "a whole Lua number must map to the same block coordinate");
    }

    @Test
    void positiveFractionFloorsDown() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertEquals(41, DimensionMobSpawner.blockCoord(engine.wrapNumber(41.9)),
                "positive fractions must floor toward zero");
    }

    @Test
    void nilIsRejected() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertNull(DimensionMobSpawner.blockCoord(engine.nilValue()),
                "nil must not map to any world coordinate");
    }

    @Test
    void numericStringIsAccepted() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        // The SPI treats numeric strings as numbers (legacy Lua 5.1 coercion), so "12" is valid.
        assertEquals(12, DimensionMobSpawner.blockCoord(engine.wrapString("12")),
                "a numeric string must coerce to its block coordinate");
    }

    @Test
    void nonNumericStringIsRejected() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertNull(DimensionMobSpawner.blockCoord(engine.wrapString("west")),
                "a non-numeric string must not map to any world coordinate");
    }

    @Test
    void nanIsRejected() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertNull(DimensionMobSpawner.blockCoord(engine.wrapNumber(Double.NaN)),
                "NaN must not map to any world coordinate");
    }

    @Test
    void infinityIsRejected() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertNull(DimensionMobSpawner.blockCoord(engine.wrapNumber(Double.POSITIVE_INFINITY)),
                "infinity must not map to any world coordinate");
    }

    @Test
    void outOfWorldXIsRejected() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertNull(DimensionMobSpawner.blockCoord(engine.wrapNumber(30_000_001)),
                "coordinates past the world border must not map to any world coordinate");
    }
}

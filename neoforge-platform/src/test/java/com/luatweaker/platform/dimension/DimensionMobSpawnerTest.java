package com.luatweaker.platform.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression for the server crash {@code LuaException: number has no integer representation}:
 * spawn handlers return world positions as Lua floats, and {@code ILuaValue#asInt()} rejects
 * non-integral values.
 */
class DimensionMobSpawnerTest {

    @Test
    void blockCoordFloorsLuaNumbers() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertEquals(-16, DimensionMobSpawner.blockCoord(engine.wrapNumber(-15.1)));
        assertEquals(42, DimensionMobSpawner.blockCoord(engine.wrapNumber(42)));
        assertEquals(41, DimensionMobSpawner.blockCoord(engine.wrapNumber(41.9)));
    }

    @Test
    void blockCoordRejectsValuesThatCannotBeWorldCoordinates() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertNull(DimensionMobSpawner.blockCoord(engine.nilValue()));
        // The SPI treats numeric strings as numbers (legacy Lua 5.1 coercion), so "12" is valid.
        assertEquals(12, DimensionMobSpawner.blockCoord(engine.wrapString("12")));
        assertNull(DimensionMobSpawner.blockCoord(engine.wrapString("west")));
        assertNull(DimensionMobSpawner.blockCoord(engine.wrapNumber(Double.NaN)));
        assertNull(DimensionMobSpawner.blockCoord(engine.wrapNumber(Double.POSITIVE_INFINITY)));
        assertNull(DimensionMobSpawner.blockCoord(engine.wrapNumber(30_000_001)));
    }
}

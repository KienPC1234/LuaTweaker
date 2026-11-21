package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuavaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Numeric edge cases: integer precision, signed zero, NaN and infinities. */
class LuaPayloadCodecNumericTest {

    LuavaEngine engine;

    @BeforeEach
    void setup() {
        engine = new LuavaEngine(false);
    }

    @Test
    void integersKeepFullPrecision() {
        int[] values = {0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, 1_234_567_890};

        for (int value : values) {
            ILuaValue decoded = NetworkTestFixture.roundTrip(engine, engine.wrapNumber(value))[0];
            assertEquals(value, decoded.asInt(), "integer " + value + " must not drift");
        }
    }

    @Test
    void negativeZeroKeepsItsSign() {
        ILuaValue decoded = NetworkTestFixture.roundTrip(engine, engine.wrapNumber(-0.0))[0];

        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(decoded.asDouble()),
            "negative zero must keep its sign bit through the round trip");
    }

    @Test
    void nanRoundTrips() {
        ILuaValue decoded = NetworkTestFixture.roundTrip(engine, engine.wrapNumber(Double.NaN))[0];

        assertTrue(Double.isNaN(decoded.asDouble()), "NaN must decode back to NaN");
    }

    @Test
    void positiveInfinityRoundTrips() {
        ILuaValue decoded = NetworkTestFixture.roundTrip(engine, engine.wrapNumber(Double.POSITIVE_INFINITY))[0];

        assertEquals(Double.POSITIVE_INFINITY, decoded.asDouble(), "positive infinity must round-trip");
    }

    @Test
    void negativeInfinityRoundTrips() {
        ILuaValue decoded = NetworkTestFixture.roundTrip(engine, engine.wrapNumber(Double.NEGATIVE_INFINITY))[0];

        assertEquals(Double.NEGATIVE_INFINITY, decoded.asDouble(), "negative infinity must round-trip");
    }

    @Test
    void emptyArgumentListRoundTrips() {
        assertEquals(0, NetworkTestFixture.roundTrip(engine).length,
            "an empty argument list must decode as an empty array");
    }
}

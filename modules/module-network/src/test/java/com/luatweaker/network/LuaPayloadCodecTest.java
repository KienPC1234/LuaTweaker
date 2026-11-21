package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuavaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scalar wire-format regression: nil, booleans, numbers and strings survive the round trip. */
class LuaPayloadCodecTest {

    private static final String ASCII = "crystal";
    private static final String UNICODE = "héllo ☃";

    LuavaEngine engine;
    ILuaValue[] decoded;

    @BeforeEach
    void setup() {
        engine = new LuavaEngine(false);
        decoded = NetworkTestFixture.roundTrip(engine,
            engine.nilValue(),
            engine.wrapBoolean(true),
            engine.wrapBoolean(false),
            engine.wrapNumber(42),
            engine.wrapNumber(-7),
            engine.wrapNumber(3.5),
            engine.wrapString(ASCII),
            engine.wrapString(UNICODE));
    }

    @Test
    void scalarArgumentsKeepTheirCount() {
        assertEquals(8, decoded.length, "every scalar argument must survive the round trip");
    }

    @Test
    void nilRoundTrips() {
        assertTrue(decoded[0].isNil(), "nil must decode back to nil");
    }

    @Test
    void booleansRoundTrip() {
        assertEquals(List.of(true, false),
            List.of(decoded[1].asBoolean(), decoded[2].asBoolean()),
            "true and false must decode in the order they were encoded");
    }

    @Test
    void integersRoundTrip() {
        assertEquals(List.of(42.0, -7.0),
            List.of(decoded[3].asDouble(), decoded[4].asDouble()),
            "integer arguments must decode with their numeric value intact");
    }

    @Test
    void fractionsRoundTrip() {
        assertEquals(3.5, decoded[5].asDouble(), "a fractional argument must keep its value");
    }

    @Test
    void asciiStringsRoundTrip() {
        assertEquals(ASCII, decoded[6].asString(), "an ASCII string must survive unchanged");
    }

    @Test
    void unicodeStringsRoundTrip() {
        assertEquals(UNICODE, decoded[7].asString(), "a non-ASCII string must survive UTF-8 encoding");
    }
}

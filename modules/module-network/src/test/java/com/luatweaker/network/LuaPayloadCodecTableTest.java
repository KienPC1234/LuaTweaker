package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuavaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Arrays stay arrays and maps stay maps across the binary round trip. */
class LuaPayloadCodecTableTest {

    private static final String NAME_KEY = "name";
    private static final String LEVEL_KEY = "level";
    private static final String NAME_VALUE = "crystal";

    LuavaEngine engine;
    ILuaTable decodedArray;
    ILuaTable decodedMap;

    @BeforeEach
    void setup() {
        engine = new LuavaEngine(false);

        ILuaTable list = engine.createTable();
        list.rawset(1, engine.wrapNumber(10));
        list.rawset(2, engine.wrapNumber(20));
        list.rawset(3, engine.wrapString("x"));
        decodedArray = NetworkTestFixture.roundTrip(engine, list)[0].asTable();

        ILuaTable map = engine.createTable();
        map.rawset(NAME_KEY, engine.wrapString(NAME_VALUE));
        map.rawset(LEVEL_KEY, engine.wrapNumber(2));
        decodedMap = NetworkTestFixture.roundTrip(engine, map)[0].asTable();
    }

    @Test
    void denseTableKeepsItsArrayLength() {
        assertEquals(3, decodedArray.length(), "a dense table must still answer #t after decoding");
    }

    @Test
    void denseTableKeepsNumericEntries() {
        assertEquals(List.of(10.0, 20.0, "x"),
            List.of(decodedArray.rawget(1).asDouble(), decodedArray.rawget(2).asDouble(),
                decodedArray.rawget(3).asString()),
            "every dense-table entry must decode at its numeric index");
    }

    @Test
    void stringKeyedTableHasNoArrayLength() {
        assertEquals(0, decodedMap.length(), "a string-keyed map must not masquerade as an array");
    }

    @Test
    void stringKeyedTableKeepsStringValue() {
        assertEquals(NAME_VALUE, decodedMap.rawget(NAME_KEY).asString(),
            "a string-keyed value must survive the round trip");
    }

    @Test
    void stringKeyedTableKeepsNumberValue() {
        assertEquals(2.0, decodedMap.rawget(LEVEL_KEY).asDouble(),
            "a numeric map value must survive the round trip");
    }

    @Test
    void stringKeyedTableKeepsMissingKeysAbsent() {
        ILuaValue missing = decodedMap.rawget("missing");

        assertTrue(missing == null || missing.isNil(), "an absent key must stay absent after decoding");
    }
}

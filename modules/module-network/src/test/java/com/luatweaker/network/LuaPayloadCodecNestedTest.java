package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuavaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Holes, nesting, empty and large tables across the binary round trip. */
class LuaPayloadCodecNestedTest {

    private static final int LARGE_SIZE = 1_000;

    LuavaEngine engine;
    ILuaTable sparseTable;
    ILuaTable nestedTable;
    ILuaTable decodedEmpty;
    ILuaTable decodedLarge;

    @BeforeEach
    void setup() {
        engine = new LuavaEngine(false);

        ILuaTable sparse = engine.createTable();
        sparse.rawset(1, engine.wrapString("a"));
        sparse.rawset(3, engine.wrapString("c"));
        sparseTable = NetworkTestFixture.roundTrip(engine, sparse)[0].asTable();

        ILuaTable inner = engine.createTable();
        inner.rawset("hp", engine.wrapNumber(20));
        ILuaTable outer = engine.createTable();
        outer.rawset(1, engine.wrapString("first"));
        outer.rawset(2, inner);
        nestedTable = NetworkTestFixture.roundTrip(engine, outer)[0].asTable();

        decodedEmpty = NetworkTestFixture.roundTrip(engine, engine.createTable())[0].asTable();

        ILuaTable large = engine.createTable();
        for (int index = 1; index <= LARGE_SIZE; index++) {
            large.rawset(index, engine.wrapNumber(index));
        }
        decodedLarge = NetworkTestFixture.roundTrip(engine, large)[0].asTable();
    }

    @Test
    void sparseTableKeepsStringKeys() {
        assertEquals(List.of("a", "c"),
            List.of(sparseTable.rawget("1").asString(), sparseTable.rawget("3").asString()),
            "a hole cannot be an array, so sparse keys keep their string form");
    }

    @Test
    void nestedTableKeepsOuterValue() {
        assertEquals("first", nestedTable.rawget(1).asString(),
            "the outer array entry must survive alongside a nested table");
    }

    @Test
    void nestedTableKeepsInnerValue() {
        ILuaValue innerValue = nestedTable.rawget(2).asTable().rawget("hp");

        assertEquals(20.0, innerValue.asDouble(), "the nested table must keep its own keys and values");
    }

    @Test
    void emptyTableStaysEmpty() {
        assertEquals(0, decodedEmpty.length(), "an empty table must decode as an empty table");
    }

    @Test
    void largeArrayKeepsItsLength() {
        assertEquals(LARGE_SIZE, decodedLarge.length(), "a large array must keep its full length");
    }

    @Test
    void largeArrayKeepsItsLastEntry() {
        assertEquals((double) LARGE_SIZE, decodedLarge.rawget(LARGE_SIZE).asDouble(),
            "the last entry of a large array must survive the round trip");
    }
}

package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuavaEngine;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Binary wire-format regression tests. They pin the properties the old JSON path provided
 * (arrays stay arrays, maps stay maps, unsupported values fail loudly) plus the guarantees
 * the binary format adds (integer precision, strict bounds, no silent trailing data).
 */
class LuaPayloadCodecTest {

    private static ILuaValue[] roundTrip(ILuaEngine engine, ILuaValue... values) {
        return LuaPayloadCodec.decodeArgs(engine, LuaPayloadCodec.encodeArgs(values));
    }

    @Test
    void scalarsRoundTrip() {
        LuavaEngine engine = new LuavaEngine(false);
        ILuaValue[] decoded = roundTrip(engine,
                engine.nilValue(),
                engine.wrapBoolean(true),
                engine.wrapBoolean(false),
                engine.wrapNumber(42),
                engine.wrapNumber(-7),
                engine.wrapNumber(3.5),
                engine.wrapString("crystal"),
                engine.wrapString("héllo ☃"));

        assertEquals(8, decoded.length);
        assertTrue(decoded[0].isNil());
        assertTrue(decoded[1].asBoolean());
        assertFalse(decoded[2].asBoolean());
        assertEquals(42.0, decoded[3].asDouble());
        assertEquals(-7.0, decoded[4].asDouble());
        assertEquals(3.5, decoded[5].asDouble());
        assertEquals("crystal", decoded[6].asString());
        assertEquals("héllo ☃", decoded[7].asString());
    }

    @Test
    void denseTablesStayArrays() {
        LuavaEngine engine = new LuavaEngine(false);
        ILuaTable list = engine.createTable();
        list.rawset(1, engine.wrapNumber(10));
        list.rawset(2, engine.wrapNumber(20));
        list.rawset(3, engine.wrapString("x"));

        ILuaValue[] decoded = roundTrip(engine, list);

        ILuaTable table = decoded[0].asTable();
        assertEquals(3, table.length(), "a decoded array must still answer #t");
        assertEquals(10.0, table.rawget(1).asDouble());
        assertEquals("x", table.rawget(3).asString());
    }

    @Test
    void stringKeyedTablesStayMaps() {
        LuavaEngine engine = new LuavaEngine(false);
        ILuaTable map = engine.createTable();
        map.rawset("name", engine.wrapString("crystal"));
        map.rawset("level", engine.wrapNumber(2));

        ILuaValue[] decoded = roundTrip(engine, map);

        ILuaTable table = decoded[0].asTable();
        assertEquals(0, table.length(), "a map must not masquerade as an array");
        assertEquals("crystal", table.rawget("name").asString());
        assertEquals(2.0, table.rawget("level").asDouble());
        ILuaValue missing = table.rawget("missing");
        assertTrue(missing == null || missing.isNil(), "absent keys must stay absent");
    }

    @Test
    void sparseTablesBecomeStringKeyedMaps() {
        LuavaEngine engine = new LuavaEngine(false);
        ILuaTable sparse = engine.createTable();
        sparse.rawset(1, engine.wrapString("a"));
        sparse.rawset(3, engine.wrapString("c"));

        ILuaValue[] decoded = roundTrip(engine, sparse);

        ILuaTable table = decoded[0].asTable();
        assertEquals("a", table.rawget("1").asString(),
                "a hole cannot be represented as an array, keys keep their string form");
        assertEquals("c", table.rawget("3").asString());
    }

    @Test
    void nestedTablesRoundTrip() {
        LuavaEngine engine = new LuavaEngine(false);
        ILuaTable inner = engine.createTable();
        inner.rawset("hp", engine.wrapNumber(20));
        ILuaTable list = engine.createTable();
        list.rawset(1, engine.wrapString("first"));
        list.rawset(2, inner);

        ILuaValue[] decoded = roundTrip(engine, list);

        ILuaTable table = decoded[0].asTable();
        assertEquals("first", table.rawget(1).asString());
        assertEquals(20.0, table.rawget(2).asTable().rawget("hp").asDouble());
    }

    @Test
    void integersKeepFullPrecision() {
        LuavaEngine engine = new LuavaEngine(false);
        int[] values = {0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, 1_234_567_890};

        for (int value : values) {
            ILuaValue[] decoded = roundTrip(engine, engine.wrapNumber(value));
            assertEquals(value, decoded[0].asInt(), "integer " + value + " must not drift");
        }
    }

    @Test
    void negativeZeroKeepsItsSign() {
        LuavaEngine engine = new LuavaEngine(false);

        ILuaValue[] decoded = roundTrip(engine, engine.wrapNumber(-0.0));

        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(decoded[0].asDouble()));
    }

    @Test
    void nanAndInfinityRoundTrip() {
        LuavaEngine engine = new LuavaEngine(false);

        ILuaValue[] decoded = roundTrip(engine,
                engine.wrapNumber(Double.NaN),
                engine.wrapNumber(Double.POSITIVE_INFINITY),
                engine.wrapNumber(Double.NEGATIVE_INFINITY));

        assertTrue(Double.isNaN(decoded[0].asDouble()));
        assertEquals(Double.POSITIVE_INFINITY, decoded[1].asDouble());
        assertEquals(Double.NEGATIVE_INFINITY, decoded[2].asDouble());
    }

    @Test
    void emptyArgumentListRoundTrips() {
        LuavaEngine engine = new LuavaEngine(false);
        assertEquals(0, roundTrip(engine).length);
    }

    @Test
    void emptyTableRoundTrips() {
        LuavaEngine engine = new LuavaEngine(false);
        ILuaValue[] decoded = roundTrip(engine, engine.createTable());
        assertEquals(0, decoded[0].asTable().length());
    }

    @Test
    void largeArrayRoundTrips() {
        LuavaEngine engine = new LuavaEngine(false);
        ILuaTable list = engine.createTable();
        for (int i = 1; i <= 1_000; i++) {
            list.rawset(i, engine.wrapNumber(i));
        }

        ILuaValue[] decoded = roundTrip(engine, list);

        ILuaTable table = decoded[0].asTable();
        assertEquals(1_000, table.length());
        assertEquals(1_000.0, table.rawget(1_000).asDouble());
    }

    @Test
    void functionsAreRejected() {
        LuavaEngine engine = new LuavaEngine(false);
        engine.executeString("_payloadFn = function() end", "codec_fn");
        ILuaValue fn = engine.getGlobalEnvironment().rawget("_payloadFn");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> LuaPayloadCodec.encodeArgs(new ILuaValue[]{fn}));
        assertTrue(error.getMessage().contains("only support"),
                "the error must explain the allowed types: " + error.getMessage());
    }

    @Test
    void overlyDeepEncodingIsRejected() {
        LuavaEngine engine = new LuavaEngine(false);
        ILuaTable root = engine.createTable();
        ILuaTable cursor = root;
        for (int i = 0; i < LuaPayloadCodec.MAX_NESTING + 5; i++) {
            ILuaTable next = engine.createTable();
            cursor.rawset(1, next);
            cursor = next;
        }

        assertThrows(IllegalArgumentException.class,
                () -> LuaPayloadCodec.encodeArgs(new ILuaValue[]{root}));
    }

    @Test
    void overlyDeepDecodingIsRejectedBeforeStackOverflow() {
        LuavaEngine engine = new LuavaEngine(false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(LuaPayloadCodec.VERSION);
        out.write(1); // one argument
        for (int i = 0; i < LuaPayloadCodec.MAX_NESTING + 5; i++) {
            out.write(6); // TAG_ARRAY
            out.write(1); // one element
        }
        out.write(0); // TAG_NIL

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> LuaPayloadCodec.decodeArgs(engine, out.toByteArray()));
        assertTrue(error.getMessage().contains("nesting"), error.getMessage());
    }

    @Test
    void oversizedStringIsRejected() {
        LuavaEngine engine = new LuavaEngine(false);
        String big = "x".repeat(LuaPayloadCodec.MAX_STRING_BYTES + 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> LuaPayloadCodec.encodeArgs(new ILuaValue[]{engine.wrapString(big)}));
        assertTrue(error.getMessage().contains("bytes"), error.getMessage());
    }

    @Test
    void truncatedPayloadIsRejected() {
        LuavaEngine engine = new LuavaEngine(false);

        assertThrows(IllegalArgumentException.class,
                () -> LuaPayloadCodec.decodeArgs(engine, new byte[]{LuaPayloadCodec.VERSION, 1, 6}));
        assertThrows(IllegalArgumentException.class,
                () -> LuaPayloadCodec.decodeArgs(engine, new byte[]{LuaPayloadCodec.VERSION}));
        assertThrows(IllegalArgumentException.class,
                () -> LuaPayloadCodec.decodeArgs(engine, new byte[0]));
    }

    @Test
    void trailingBytesAreRejected() {
        LuavaEngine engine = new LuavaEngine(false);
        byte[] valid = LuaPayloadCodec.encodeArgs(new ILuaValue[]{engine.wrapNumber(1)});
        byte[] withGarbage = new byte[valid.length + 1];
        System.arraycopy(valid, 0, withGarbage, 0, valid.length);
        withGarbage[valid.length] = 0x7F;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> LuaPayloadCodec.decodeArgs(engine, withGarbage));
        assertTrue(error.getMessage().contains("trailing"), error.getMessage());
    }

    @Test
    void unknownVersionIsRejected() {
        LuavaEngine engine = new LuavaEngine(false);
        assertThrows(IllegalArgumentException.class,
                () -> LuaPayloadCodec.decodeArgs(engine, new byte[]{2, 0}));
    }

    @Test
    void absurdElementCountIsRejected() {
        LuavaEngine engine = new LuavaEngine(false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(LuaPayloadCodec.VERSION);
        out.write(0x81); // 65_537 as a 3-byte varint
        out.write(0x80);
        out.write(0x04);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> LuaPayloadCodec.decodeArgs(engine, out.toByteArray()));
        assertTrue(error.getMessage().contains("exceeds"), error.getMessage());
    }

    @Test
    void unknownTagIsRejected() {
        LuavaEngine engine = new LuavaEngine(false);
        assertThrows(IllegalArgumentException.class,
                () -> LuaPayloadCodec.decodeArgs(engine, new byte[]{LuaPayloadCodec.VERSION, 1, 99}));
    }

    @Test
    void encodedPayloadStartsWithTheVersionByte() {
        LuavaEngine engine = new LuavaEngine(false);
        byte[] encoded = LuaPayloadCodec.encodeArgs(new ILuaValue[]{engine.wrapBoolean(true)});
        assertEquals(LuaPayloadCodec.VERSION, encoded[0]);
    }
}

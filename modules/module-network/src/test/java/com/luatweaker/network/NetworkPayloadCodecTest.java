package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuavaEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wire-format regression: arrays stay arrays, maps stay maps, bad values fail loudly. */
class NetworkPayloadCodecTest {

    @Test
    void arrayTablesEncodeAsJsonArrays() {
        LuavaEngine engine = new LuavaEngine(false);
        ILuaTable list = engine.createTable();
        list.rawset(1, engine.wrapNumber(10));
        list.rawset(2, engine.wrapNumber(20));
        list.rawset(3, engine.wrapString("x"));

        assertEquals("[[10.0,20.0,\"x\"]]",
                NetworkPayloadCodec.encodeArgs(new ILuaValue[]{list}),
                "an array-like Lua table must not become {\"1\":10,...}");
    }

    @Test
    void mapTablesEncodeAsJsonObjects() {
        LuavaEngine engine = new LuavaEngine(false);
        ILuaTable map = engine.createTable();
        map.rawset("name", engine.wrapString("crystal"));
        map.rawset("level", engine.wrapNumber(2));

        String json = NetworkPayloadCodec.encodeArgs(new ILuaValue[]{map});
        assertTrue(json.contains("\"name\":\"crystal\""), json);
        assertTrue(json.contains("\"level\":2"), json);
        assertTrue(json.startsWith("[{"), "a string-keyed table must stay a JSON object: " + json);
    }

    @Test
    void arraysRoundTripBackToLuaArrays() {
        LuavaEngine engine = new LuavaEngine(false);
        ILuaValue[] decoded = NetworkPayloadCodec.decodeArgs(engine, "[[1.0,2.0,\"x\"]]");

        assertEquals(1, decoded.length);
        assertTrue(decoded[0].isTable());
        ILuaTable table = decoded[0].asTable();
        assertEquals(3, table.length(), "decoded arrays must answer #t");
        assertEquals(1.0, table.rawget(1).asDouble());
        assertEquals("x", table.rawget(3).asString());
    }

    @Test
    void objectsRoundTripBackToLuaTables() {
        LuavaEngine engine = new LuavaEngine(false);
        ILuaValue[] decoded = NetworkPayloadCodec.decodeArgs(engine, "[{\"name\":\"crystal\"}]");

        ILuaTable table = decoded[0].asTable();
        assertEquals("crystal", table.rawget("name").asString());
        ILuaValue missing = table.rawget("missing");
        assertTrue(missing == null || missing.isNil(), "absent keys must stay absent");
    }

    @Test
    void functionsCannotBeSent() {
        LuavaEngine engine = new LuavaEngine(false);
        engine.executeString("_payloadFn = function() end", "codec_fn");
        ILuaValue fn = engine.getGlobalEnvironment().rawget("_payloadFn");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> NetworkPayloadCodec.encodeArgs(new ILuaValue[]{fn}));
        assertTrue(error.getMessage().contains("only support"),
                "the error must explain the allowed types: " + error.getMessage());
    }

    @Test
    void oversizedPayloadsAreRejected() {
        LuavaEngine engine = new LuavaEngine(false);
        String big = "x".repeat(NetworkPayloadCodec.MAX_PAYLOAD_CHARS + 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> NetworkPayloadCodec.encodeArgs(new ILuaValue[]{engine.wrapString(big)}));
        assertTrue(error.getMessage().contains("too large"), error.getMessage());
    }

    @Test
    void deeplyNestedPayloadsAreRejected() {
        LuavaEngine engine = new LuavaEngine(false);
        StringBuilder json = new StringBuilder();
        json.append("[".repeat(NetworkPayloadCodec.MAX_NESTING + 5));
        json.append("]".repeat(NetworkPayloadCodec.MAX_NESTING + 5));

        assertThrows(IllegalArgumentException.class,
                () -> NetworkPayloadCodec.decodeArgs(engine, json.toString()),
                "nesting past the cap must be rejected");
    }

    @Test
    void hostileDeepNestingFailsWithoutStackOverflow() {
        LuavaEngine engine = new LuavaEngine(false);
        String json = "[".repeat(20_000) + "]".repeat(20_000);

        assertThrows(IllegalArgumentException.class,
                () -> NetworkPayloadCodec.decodeArgs(engine, json),
                "the depth guard must run before Gson recurses");
    }

    @Test
    void nonArrayRootIsRejected() {
        LuavaEngine engine = new LuavaEngine(false);
        assertThrows(IllegalArgumentException.class,
                () -> NetworkPayloadCodec.decodeArgs(engine, "{\"a\":1}"));
    }

    @Test
    void malformedJsonIsRejected() {
        LuavaEngine engine = new LuavaEngine(false);
        assertThrows(IllegalArgumentException.class,
                () -> NetworkPayloadCodec.decodeArgs(engine, "[not json"));
    }

    @Test
    void emptyPayloadDecodesToNoArguments() {
        LuavaEngine engine = new LuavaEngine(false);
        assertEquals(0, NetworkPayloadCodec.decodeArgs(engine, "").length);
        assertEquals(0, NetworkPayloadCodec.decodeArgs(engine, "[]").length);
    }
}

package com.luatweaker.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuavaEngine;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wire-format regression: Lua arrays must stay arrays across the JSON payload. */
public class NetworkPayloadFormatTest {

    private static String serialize(NetworkServiceImpl service, ILuaValue... args) throws Exception {
        Method method = NetworkServiceImpl.class.getDeclaredMethod("serializeArgs", ILuaValue[].class);
        method.setAccessible(true);
        return (String) method.invoke(service, (Object) args);
    }

    @Test
    void arrayTablesSerializeAsJsonArrays() throws Exception {
        LuavaEngine engine = new LuavaEngine(false);
        NetworkServiceImpl service = new NetworkServiceImpl(engine);

        var list = engine.createTable();
        list.rawset(1, engine.wrapNumber(10));
        list.rawset(2, engine.wrapNumber(20));
        list.rawset(3, engine.wrapString("x"));

        JsonArray outer = JsonParser.parseString(serialize(service, list)).getAsJsonArray();
        assertEquals(1, outer.size(), "one argument");
        assertTrue(outer.get(0).isJsonArray(),
                "an array-like Lua table must serialize as a JSON array, not an object");
        JsonArray inner = outer.get(0).getAsJsonArray();
        assertEquals(3, inner.size());
        assertEquals(10.0, inner.get(0).getAsDouble());
        assertEquals(20.0, inner.get(1).getAsDouble());
        assertEquals("x", inner.get(2).getAsString());
    }

    @Test
    void mapTablesSerializeAsJsonObjects() throws Exception {
        LuavaEngine engine = new LuavaEngine(false);
        NetworkServiceImpl service = new NetworkServiceImpl(engine);

        var map = engine.createTable();
        map.rawset("name", engine.wrapString("crystal"));
        map.rawset("level", engine.wrapNumber(2));

        JsonArray outer = JsonParser.parseString(serialize(service, map)).getAsJsonArray();
        assertTrue(outer.get(0).isJsonObject(), "a mixed/string-keyed table must stay a JSON object");
        var object = outer.get(0).getAsJsonObject();
        assertEquals("crystal", object.get("name").getAsString());
        assertEquals(2.0, object.get("level").getAsDouble());
    }
}

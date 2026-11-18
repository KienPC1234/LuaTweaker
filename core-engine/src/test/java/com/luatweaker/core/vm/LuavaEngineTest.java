package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end coverage of the luava engine SPI: execution, calls, conversion and errors. */
public class LuavaEngineTest {

    @TempDir
    Path tempDir;

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void executeStringRunsChunksAndTracksSuccess() {
        ILuaEngine engine = new LuavaEngine(false);
        engine.executeString(
            "_engineAnswer = 6 * 7\n" +
            "assert(_engineAnswer == 42)",
            "execute_string_success"
        );

        assertNull(engine.getAndClearLastExecutionError());
        assertEquals(42.0, engine.getGlobalEnvironment().rawget("_engineAnswer").asDouble());
    }

    @Test
    void executeStringReportsRuntimeErrorAndThenClearsIt() {
        ILuaEngine engine = new LuavaEngine(false);
        engine.executeString("error('engine boom')", "execute_string_error");

        String error = engine.getAndClearLastExecutionError();
        assertNotNull(error, "a runtime error must be captured");
        assertTrue(error.contains("engine boom"), "error text must be preserved: " + error);
        assertNull(engine.getAndClearLastExecutionError(), "reading the error must clear it");
    }

    @Test
    void executeScriptLoadsFileAndReportsCompileErrors() throws Exception {
        ILuaEngine engine = new LuavaEngine(false);
        Path good = tempDir.resolve("good.lua");
        Files.writeString(good, "_fromFile = 'loaded'", StandardCharsets.UTF_8);
        engine.executeScript(good.toFile(), "good_script");

        assertNull(engine.getAndClearLastExecutionError());
        assertEquals("loaded", engine.getGlobalEnvironment().rawget("_fromFile").asString());

        Path bad = tempDir.resolve("bad.lua");
        Files.writeString(bad, "this is not valid lua ===", StandardCharsets.UTF_8);
        engine.executeScript(bad.toFile(), "bad_script");

        String error = engine.getAndClearLastExecutionError();
        assertNotNull(error, "a compile error in a script file must be captured");
        assertFalse(error.isBlank(), "compile error text must not be empty");
    }

    @Test
    void callFunctionReturnsFirstResultAndCallFunctionMultiReturnsAll() {
        ILuaEngine engine = new LuavaEngine(false);
        engine.executeString(
            "function _multi() return 'first', 'second', 3 end\n" +
            "function _nothing() end",
            "call_setup"
        );
        assertNull(engine.getAndClearLastExecutionError());

        ILuaValue multi = engine.getGlobalEnvironment().rawget("_multi");
        assertEquals("first", engine.callFunction(multi).asString(),
            "callFunction must return the first result");

        ILuaValue[] results = engine.callFunctionMulti(multi);
        assertEquals(3, results.length, "all return values must be preserved");
        assertEquals("first", results[0].asString());
        assertEquals("second", results[1].asString());
        assertEquals(3.0, results[2].asDouble());

        ILuaValue nothing = engine.getGlobalEnvironment().rawget("_nothing");
        assertEquals(0, engine.callFunctionMulti(nothing).length, "a no-return function yields no values");
        assertEquals(0, engine.callFunctionMulti(engine.nilValue()).length, "a nil function yields no values");
    }

    @Test
    void callFunctionReportsErrorsThroughEngineState() {
        ILuaEngine engine = new LuavaEngine(false);
        engine.executeString("function _badCall() error('call boom') end", "call_error_setup");
        assertNull(engine.getAndClearLastExecutionError());

        ILuaValue result = engine.callFunction(engine.getGlobalEnvironment().rawget("_badCall"));
        assertTrue(result == null || result.isNil(), "a failed call must not return a value");
        String error = engine.getAndClearLastExecutionError();
        assertNotNull(error, "a failed call must be captured");
        assertTrue(error.contains("call boom"), "call error text must be preserved: " + error);
    }

    @Test
    void wrapAndNilHelpersProduceCorrectLuaValues() {
        ILuaEngine engine = new LuavaEngine(false);

        ILuaValue text = engine.wrapString("hello");
        assertTrue(text.isString());
        assertEquals("hello", text.asString());

        ILuaValue number = engine.wrapNumber(12.5);
        assertTrue(number.isNumber());
        assertEquals(12.5, number.asDouble());

        ILuaValue flag = engine.wrapBoolean(true);
        assertTrue(flag.asBoolean());
        assertFalse(engine.wrapBoolean(false).asBoolean());

        ILuaValue nil = engine.nilValue();
        assertTrue(nil.isNil(), "nilValue must be nil");
        assertEquals(0, nil.asInt(), "non-number values convert to 0 like the previous engine");
    }

    @Test
    void toLuaValueConvertsScalarsMapsListsAndNull() {
        ILuaEngine engine = new LuavaEngine(false);

        assertEquals("text", engine.toLuaValue("text").asString());
        assertEquals(7.0, engine.toLuaValue(7).asDouble());
        assertTrue(engine.toLuaValue(Boolean.TRUE).asBoolean());
        assertTrue(engine.toLuaValue(null).isNil(), "null must convert to nil");

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("Key", "value");
        source.put("Count", 2);
        ILuaValue mapValue = engine.toLuaValue(source);
        assertTrue(mapValue.isTable());
        assertEquals("value", mapValue.asTable().rawget("Key").asString());
        assertEquals(2.0, mapValue.asTable().rawget("Count").asDouble());

        ILuaValue listValue = engine.toLuaValue(List.of("alpha", "beta"));
        assertTrue(listValue.isTable());
        assertEquals("alpha", listValue.asTable().rawget(1).asString());
        assertEquals("beta", listValue.asTable().rawget(2).asString());
    }

    @Test
    void createTableIsVisibleFromLuaAndBootstrapGlobalsExist() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable table = engine.createTable();
        table.rawset("Answer", 42.0);
        table.rawset("Label", "created");
        engine.getGlobalEnvironment().rawset("_javaTable", table);

        engine.executeString(
            "assert(_javaTable ~= nil, 'table must be visible in Lua')\n" +
            "assert(_javaTable.Answer == 42)\n" +
            "assert(_javaTable.Label == 'created')\n" +
            "assert(type(Signal) == 'table', 'bootstrap Signal must exist')\n" +
            "assert(type(Task) == 'table', 'bootstrap Task must exist')",
            "create_table_check"
        );
        assertNull(engine.getAndClearLastExecutionError());
    }
}

package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end coverage of the luava engine SPI: execution, calls, conversion and errors. */
class LuavaEngineTest {

    private static final String ANSWER_SCRIPT = """
            _engineAnswer = 6 * 7
            assert(_engineAnswer == 42)
            """;

    private static final String ENGINE_BOOM = "error('engine boom')";
    private static final String ENGINE_BOOM_CHUNK = "execute_string_error";

    private static final String GOOD_LUA = "good.lua";
    private static final String BAD_LUA = "bad.lua";
    private static final String GOOD_SOURCE = "_fromFile = 'loaded'";
    private static final String BAD_SOURCE = "this is not valid lua ===";
    private static final String GOOD_CHUNK = "good_script";
    private static final String BAD_CHUNK = "bad_script";

    private static final String CALL_SETUP_SCRIPT = """
            function _multi() return 'first', 'second', 3 end
            function _nothing() end
            """;
    private static final String CALL_SETUP_CHUNK = "call_setup";
    private static final String MULTI = "_multi";
    private static final String NOTHING = "_nothing";

    private static final String BAD_CALL_SETUP = "function _badCall() error('call boom') end";
    private static final String BAD_CALL_CHUNK = "call_error_setup";
    private static final String BAD_CALL = "_badCall";

    private static final String KEY = "Key";
    private static final String VALUE = "value";
    private static final String COUNT = "Count";
    private static final String ALPHA = "alpha";
    private static final String BETA = "beta";

    @TempDir
    Path tempDir;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Nested
    class ExecuteString {

        private ILuaEngine ranAnswerChunk() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(ANSWER_SCRIPT, "execute_string_success");
            return engine;
        }

        @Test
        void executeStringRunsChunksAndTracksSuccessGetAndClearLastExecutionError() {
            ILuaEngine engine = ranAnswerChunk();
            assertNull(engine.getAndClearLastExecutionError(),
                    "a chunk whose assertions pass must not record an execution error");
        }

        @Test
        void executeStringRunsChunksAndTracksSuccessEngineAnswer() {
            ILuaEngine engine = ranAnswerChunk();
            assertEquals(42.0, engine.getGlobalEnvironment().rawget("_engineAnswer").asDouble(),
                    "the chunk must leave its computed global value at 42");
        }

        @Test
        void executeStringReportsRuntimeErrorAndThenClearsItARuntimeErrorMustBeCaptured() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(ENGINE_BOOM, ENGINE_BOOM_CHUNK);
            String error = engine.getAndClearLastExecutionError();
            assertNotNull(error, "a runtime error must be captured");
        }

        @Test
        void executeStringReportsRuntimeErrorAndThenClearsItEngineBoom() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(ENGINE_BOOM, ENGINE_BOOM_CHUNK);
            String error = engine.getAndClearLastExecutionError();
            assertTrue(error.contains("engine boom"), "error text must be preserved: " + error);
        }

        @Test
        void executeStringReportsRuntimeErrorAndThenClearsItReadingTheErrorMustClearIt() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(ENGINE_BOOM, ENGINE_BOOM_CHUNK);
            engine.getAndClearLastExecutionError();
            assertNull(engine.getAndClearLastExecutionError(), "reading the error must clear it");
        }
    }

    @Nested
    class ExecuteScript {

        private ILuaEngine ranGoodThenBadScript() throws IOException {
            ILuaEngine engine = new LuavaEngine(false);
            Path good = tempDir.resolve(GOOD_LUA);
            Files.writeString(good, GOOD_SOURCE, StandardCharsets.UTF_8);
            engine.executeScript(good.toFile(), GOOD_CHUNK);
            Path bad = tempDir.resolve(BAD_LUA);
            Files.writeString(bad, BAD_SOURCE, StandardCharsets.UTF_8);
            engine.executeScript(bad.toFile(), BAD_CHUNK);
            return engine;
        }

        @Test
        void executeScriptLoadsFileAndReportsCompileErrorsGetAndClearLastExecutionError() throws IOException {
            ILuaEngine engine = ranGoodThenBadScript();
            engine.getAndClearLastExecutionError();
            assertNull(engine.getAndClearLastExecutionError(),
                    "the compile error must have been consumed by the first read");
        }

        @Test
        void executeScriptLoadsFileAndReportsCompileErrorsFromFile() throws IOException {
            ILuaEngine engine = ranGoodThenBadScript();
            engine.getAndClearLastExecutionError();
            assertEquals("loaded", engine.getGlobalEnvironment().rawget("_fromFile").asString(),
                    "the valid script file must have executed and set its global");
        }

        @Test
        void executeScriptLoadsFileAndReportsCompileErrorsACompileErrorInAScriptFileMustBeCaptured() throws IOException {
            ILuaEngine engine = ranGoodThenBadScript();
            String error = engine.getAndClearLastExecutionError();
            assertNotNull(error, "a compile error in a script file must be captured");
        }

        @Test
        void executeScriptLoadsFileAndReportsCompileErrorsCompileErrorTextMustNotBeEmpty() throws IOException {
            ILuaEngine engine = ranGoodThenBadScript();
            String error = engine.getAndClearLastExecutionError();
            assertFalse(error.isBlank(), "compile error text must not be empty");
        }
    }

    @Nested
    class CallFunctionResults {

        private ILuaEngine callSetup() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(CALL_SETUP_SCRIPT, CALL_SETUP_CHUNK);
            return engine;
        }

        @Test
        void callFunctionReturnsFirstResultAndCallFunctionMultiReturnsAllGetAndClearLastExecutionError() {
            ILuaEngine engine = callSetup();
            assertNull(engine.getAndClearLastExecutionError(),
                    "defining the helper functions must not record an execution error");
        }

        @Test
        void callFunctionReturnsFirstResultAndCallFunctionMultiReturnsAllFirst() {
            ILuaEngine engine = callSetup();
            ILuaValue multi = engine.getGlobalEnvironment().rawget(MULTI);
            assertEquals("first", engine.callFunction(multi).asString(),
                    "callFunction must return the first result");
        }

        @Test
        void callFunctionReturnsFirstResultAndCallFunctionMultiReturnsAllAllReturnValuesMustBePreserved() {
            ILuaEngine engine = callSetup();
            ILuaValue multi = engine.getGlobalEnvironment().rawget(MULTI);
            assertEquals(3, engine.callFunctionMulti(multi).length,
                    "all return values must be preserved");
        }

        @Test
        void callFunctionReturnsFirstResultAndCallFunctionMultiReturnsAllFirst2() {
            ILuaEngine engine = callSetup();
            ILuaValue[] results = engine.callFunctionMulti(engine.getGlobalEnvironment().rawget(MULTI));
            assertEquals("first", results[0].asString(),
                    "the first multi-return value must be the string 'first'");
        }

        @Test
        void callFunctionReturnsFirstResultAndCallFunctionMultiReturnsAllSecond() {
            ILuaEngine engine = callSetup();
            ILuaValue[] results = engine.callFunctionMulti(engine.getGlobalEnvironment().rawget(MULTI));
            assertEquals("second", results[1].asString(),
                    "the second multi-return value must be the string 'second'");
        }

        @Test
        void callFunctionReturnsFirstResultAndCallFunctionMultiReturnsAllAsDouble() {
            ILuaEngine engine = callSetup();
            ILuaValue[] results = engine.callFunctionMulti(engine.getGlobalEnvironment().rawget(MULTI));
            assertEquals(3.0, results[2].asDouble(),
                    "the third multi-return value must be the number 3");
        }

        @Test
        void callFunctionReturnsFirstResultAndCallFunctionMultiReturnsAllANoReturnFunctionYieldsNoValues() {
            ILuaEngine engine = callSetup();
            ILuaValue nothing = engine.getGlobalEnvironment().rawget(NOTHING);
            assertEquals(0, engine.callFunctionMulti(nothing).length, "a no-return function yields no values");
        }

        @Test
        void callFunctionReturnsFirstResultAndCallFunctionMultiReturnsAllANilFunctionYieldsNoValues() {
            ILuaEngine engine = callSetup();
            assertEquals(0, engine.callFunctionMulti(engine.nilValue()).length, "a nil function yields no values");
        }
    }

    @Nested
    class CallFunctionErrors {

        private ILuaEngine badCallSetup() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(BAD_CALL_SETUP, BAD_CALL_CHUNK);
            return engine;
        }

        @Test
        void callFunctionReportsErrorsThroughEngineStateGetAndClearLastExecutionError() {
            ILuaEngine engine = badCallSetup();
            assertNull(engine.getAndClearLastExecutionError(),
                    "defining the failing function must not record an execution error");
        }

        @Test
        void callFunctionReportsErrorsThroughEngineStateAFailedCallMustNotReturnAValue() {
            ILuaEngine engine = badCallSetup();
            ILuaValue result = engine.callFunction(engine.getGlobalEnvironment().rawget(BAD_CALL));
            engine.getAndClearLastExecutionError();
            assertTrue(result == null || result.isNil(), "a failed call must not return a value");
        }

        @Test
        void callFunctionReportsErrorsThroughEngineStateAFailedCallMustBeCaptured() {
            ILuaEngine engine = badCallSetup();
            engine.callFunction(engine.getGlobalEnvironment().rawget(BAD_CALL));
            String error = engine.getAndClearLastExecutionError();
            assertNotNull(error, "a failed call must be captured");
        }

        @Test
        void callFunctionReportsErrorsThroughEngineStateCallBoom() {
            ILuaEngine engine = badCallSetup();
            engine.callFunction(engine.getGlobalEnvironment().rawget(BAD_CALL));
            String error = engine.getAndClearLastExecutionError();
            assertTrue(error.contains("call boom"), "call error text must be preserved: " + error);
        }
    }

    @Nested
    class WrapHelpers {

        @Test
        void wrapAndNilHelpersProduceCorrectLuaValuesText() {
            ILuaEngine engine = new LuavaEngine(false);
            assertTrue(engine.wrapString("hello").isString(),
                    "wrapString must produce a Lua string value");
        }

        @Test
        void wrapAndNilHelpersProduceCorrectLuaValuesHello() {
            ILuaEngine engine = new LuavaEngine(false);
            assertEquals("hello", engine.wrapString("hello").asString(),
                    "wrapString must keep the wrapped text");
        }

        @Test
        void wrapAndNilHelpersProduceCorrectLuaValuesNumber() {
            ILuaEngine engine = new LuavaEngine(false);
            assertTrue(engine.wrapNumber(12.5).isNumber(),
                    "wrapNumber must produce a Lua number value");
        }

        @Test
        void wrapAndNilHelpersProduceCorrectLuaValuesNumber2() {
            ILuaEngine engine = new LuavaEngine(false);
            assertEquals(12.5, engine.wrapNumber(12.5).asDouble(),
                    "wrapNumber must keep the wrapped value");
        }

        @Test
        void wrapAndNilHelpersProduceCorrectLuaValuesFlag() {
            ILuaEngine engine = new LuavaEngine(false);
            assertTrue(engine.wrapBoolean(true).asBoolean(),
                    "wrapBoolean(true) must read back as true");
        }

        @Test
        void wrapAndNilHelpersProduceCorrectLuaValuesWrapBoolean() {
            ILuaEngine engine = new LuavaEngine(false);
            assertFalse(engine.wrapBoolean(false).asBoolean(),
                    "wrapBoolean(false) must read back as false");
        }

        @Test
        void wrapAndNilHelpersProduceCorrectLuaValuesNilValueMustBeNil() {
            ILuaEngine engine = new LuavaEngine(false);
            assertTrue(engine.nilValue().isNil(), "nilValue must be nil");
        }

        @Test
        void wrapAndNilHelpersProduceCorrectLuaValuesNonNumberValuesConvertTo0LikeThePrevious() {
            ILuaEngine engine = new LuavaEngine(false);
            assertEquals(0, engine.nilValue().asInt(),
                    "non-number values convert to 0 like the previous engine");
        }
    }

    @Nested
    class ToLuaValueScalars {

        @Test
        void luaValueConvertsScalarsMapsListsAndNullText() {
            ILuaEngine engine = new LuavaEngine(false);
            assertEquals("text", engine.toLuaValue("text").asString(),
                    "toLuaValue must convert a Java String to a Lua string");
        }

        @Test
        void luaValueConvertsScalarsMapsListsAndNullToLuaValue() {
            ILuaEngine engine = new LuavaEngine(false);
            assertEquals(7.0, engine.toLuaValue(7).asDouble(),
                    "toLuaValue must convert a Java Integer to the matching Lua number");
        }

        @Test
        void luaValueConvertsScalarsMapsListsAndNullToLuaValue2() {
            ILuaEngine engine = new LuavaEngine(false);
            assertTrue(engine.toLuaValue(Boolean.TRUE).asBoolean(),
                    "toLuaValue must convert a Java Boolean to a Lua boolean");
        }

        @Test
        void luaValueConvertsScalarsMapsListsAndNullNullMustConvertToNil() {
            ILuaEngine engine = new LuavaEngine(false);
            assertTrue(engine.toLuaValue(null).isNil(), "null must convert to nil");
        }
    }

    @Nested
    class ToLuaValueContainers {

        private ILuaTable mapTable(ILuaEngine engine) {
            Map<String, Object> source = new ConcurrentHashMap<>();
            source.put(KEY, VALUE);
            source.put(COUNT, 2);
            return engine.toLuaValue(source).asTable();
        }

        private ILuaTable listTable(ILuaEngine engine) {
            return engine.toLuaValue(List.of(ALPHA, BETA)).asTable();
        }

        @Test
        void luaValueConvertsScalarsMapsListsAndNullMapValue() {
            ILuaEngine engine = new LuavaEngine(false);
            assertTrue(engine.toLuaValue(mapSource()).isTable(),
                    "toLuaValue must convert a Java Map to a Lua table");
        }

        @Test
        void luaValueConvertsScalarsMapsListsAndNullKey() {
            ILuaEngine engine = new LuavaEngine(false);
            assertEquals(VALUE, mapTable(engine).rawget(KEY).asString(),
                    "the map's string entry must keep its key and value");
        }

        @Test
        void luaValueConvertsScalarsMapsListsAndNullCount() {
            ILuaEngine engine = new LuavaEngine(false);
            assertEquals(2.0, mapTable(engine).rawget(COUNT).asDouble(),
                    "the map's numeric entry must become a Lua number");
        }

        @Test
        void luaValueConvertsScalarsMapsListsAndNullListValue() {
            ILuaEngine engine = new LuavaEngine(false);
            assertTrue(engine.toLuaValue(List.of(ALPHA, BETA)).isTable(),
                    "toLuaValue must convert a Java List to a Lua table");
        }

        @Test
        void luaValueConvertsScalarsMapsListsAndNullAlpha() {
            ILuaEngine engine = new LuavaEngine(false);
            assertEquals(ALPHA, listTable(engine).rawget(1).asString(),
                    "the first list element must land at Lua index 1");
        }

        @Test
        void luaValueConvertsScalarsMapsListsAndNullBeta() {
            ILuaEngine engine = new LuavaEngine(false);
            assertEquals(BETA, listTable(engine).rawget(2).asString(),
                    "the second list element must land at Lua index 2");
        }

        private Map<String, Object> mapSource() {
            Map<String, Object> source = new ConcurrentHashMap<>();
            source.put(KEY, VALUE);
            source.put(COUNT, 2);
            return source;
        }
    }

    @Nested
    class TablesAndNumbers {

        @Test
        void createTableIsVisibleFromLuaAndBootstrapGlobalsExist() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable table = engine.createTable();
            table.rawset("Answer", 42.0);
            table.rawset("Label", "created");
            engine.getGlobalEnvironment().rawset("_javaTable", table);

            engine.executeString("""
                    assert(_javaTable ~= nil, 'table must be visible in Lua')
                    assert(_javaTable.Answer == 42)
                    assert(_javaTable.Label == 'created')
                    assert(type(Signal) == 'table', 'bootstrap Signal must exist')
                    assert(type(Task) == 'table', 'bootstrap Task must exist')
                    """, "create_table_check");
            assertNull(engine.getAndClearLastExecutionError(),
                    "a Java-created table must be readable from Lua with the bootstrap globals present");
        }

        @Test
        void wholeNumbersFromJavaBecomeLuaIntegers() {
            ILuaEngine engine = new LuavaEngine(false);
            // Gson parses JSON integers as Double; the SPI must restore Lua's integer kind so
            // storage/config round-trips keep tostring(42) == "42" and math.type() == "integer".
            engine.getGlobalEnvironment().rawset("_whole", engine.toLuaValue(42.0));
            engine.getGlobalEnvironment().rawset("_fraction", engine.toLuaValue(42.5));

            engine.executeString("""
                    assert(math.type(_whole) == 'integer', 'whole number kind: ' .. math.type(_whole))
                    assert(tostring(_whole) == '42', 'whole number text: ' .. tostring(_whole))
                    assert(math.type(_fraction) == 'float', 'fraction kind: ' .. math.type(_fraction))
                    """, "number_kind_check");
            assertNull(engine.getAndClearLastExecutionError(),
                    "whole doubles must become Lua integers while fractions stay floats");
        }

        @Test
        void finishedDeferredCoroutinesArePrunedASuspendedTaskMustStayTracked() {
            LuavaEngine engine = new LuavaEngine(false);
            // Yields once, so the coroutine is tracked as SUSPENDED when _run_deferred returns.
            engine.executeString("task._run_deferred(function() task.wait(0) end, {})", "coroutine_setup");
            assertEquals(1, engine.liveCoroutineCount(), "a suspended task must stay tracked");
            engine.shutdown();
        }

        @Test
        void finishedDeferredCoroutinesArePrunedLiveCoroutineCount() {
            LuavaEngine engine = new LuavaEngine(false);
            ILuaTable taskTable = engine.getGlobalEnvironment().rawget("task").asTable();
            // Yields once, so the coroutine is tracked as SUSPENDED when _run_deferred returns.
            engine.executeString("task._run_deferred(function() task.wait(0) end, {})", "coroutine_setup");
            engine.callFunction(taskTable.rawget("_tick"));
            assertEquals(1, engine.liveCoroutineCount(),
                    "the resume happens in Lua, so the finished coroutine is still referenced");
            engine.shutdown();
        }

        @Test
        void finishedDeferredCoroutinesArePrunedPruningMustReleaseTheFinishedCoroutine() {
            LuavaEngine engine = new LuavaEngine(false);
            ILuaTable taskTable = engine.getGlobalEnvironment().rawget("task").asTable();
            // Yields once, so the coroutine is tracked as SUSPENDED when _run_deferred returns.
            engine.executeString("task._run_deferred(function() task.wait(0) end, {})", "coroutine_setup");
            engine.callFunction(taskTable.rawget("_tick"));
            engine.pruneFinishedCoroutines();
            assertEquals(0, engine.liveCoroutineCount(), "pruning must release the finished coroutine");
            engine.shutdown();
        }
    }
}

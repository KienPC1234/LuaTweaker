package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** SPI-level coverage of LuavaLuaTable, including metatable and foreign-impl paths. */
class LuavaLuaTableTest {

    private static final String FOREIGN = "foreign";
    private static final String NATIVE = "native";
    private static final String FROM_INDEX = "from-index";
    private static final String MISSING = "Missing";
    private static final String WRITTEN = "written";
    private static final String FALLBACK = "fallback";
    private static final String META_INDEX = "__index";
    private static final String META_NEWINDEX = "__newindex";
    private static final String META_PROBE = "_metaProbe";
    private static final String META_AFTER_FOREIGN = "_metaAfterForeign";
    private static final String NEW_INDEX_KEY = "_newIndexKey";
    private static final String NEW_INDEX_VALUE = "_newIndexValue";
    private static final String ALPHA = "Alpha";
    private static final String BETA = "Beta";
    private static final String PROBE = "Probe";
    private static final String ECHO = "echo";
    private static final String READ = "read";

    private static final String NOTHING_THROWN = "expected IllegalArgumentException but nothing was thrown";

    private static final String METATABLE_SCRIPT = """
            assert(_metaProbe.Missing == 'from-index', '__index must supply missing keys')
            _metaProbe.Missing = 'written'
            assert(_newIndexKey == 'Missing')
            assert(_newIndexValue == 'written')
            """;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    /** A foreign ILuaTable implementation whose methods delegate to a real table. */
    private ILuaTable foreignTable(ILuaEngine engine) {
        ILuaTable delegate = engine.createTable();
        return (ILuaTable) Proxy.newProxyInstance(
                ClassLoader.getSystemClassLoader(),
                new Class<?>[]{ILuaTable.class},
                (proxy, method, args) -> {
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /** A foreign ILuaValue implementation whose methods delegate to a real value. */
    private ILuaValue foreignValue(ILuaEngine engine) {
        ILuaValue delegate = engine.wrapString(FOREIGN);
        return (ILuaValue) Proxy.newProxyInstance(
                ClassLoader.getSystemClassLoader(),
                new Class<?>[]{ILuaValue.class},
                (proxy, method, args) -> {
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Nested
    class RawTableAccess {

        private ILuaTable threeElementTable(ILuaEngine engine) {
            ILuaTable table = engine.createTable();
            table.rawset(1, "first");
            table.rawset(2, 2.5);
            table.rawset(3, true);
            return table;
        }

        @Test
        void rawsetRawgetAndLengthRoundTripTheArrayPartMustBeThreeElementsLong() {
            ILuaEngine engine = new LuavaEngine(false);
            assertEquals(3, threeElementTable(engine).length(),
                    "the array part must be three elements long");
        }

        @Test
        void rawsetRawgetAndLengthRoundTripFirst() {
            ILuaEngine engine = new LuavaEngine(false);
            assertEquals("first", threeElementTable(engine).rawget(1).asString(),
                    "rawget(1) must return the string stored at index 1");
        }

        @Test
        void rawsetRawgetAndLengthRoundTripTable() {
            ILuaEngine engine = new LuavaEngine(false);
            assertEquals(2.5, threeElementTable(engine).rawget(2).asDouble(),
                    "rawget(2) must return the number stored at index 2");
        }

        @Test
        void rawsetRawgetAndLengthRoundTripTable2() {
            ILuaEngine engine = new LuavaEngine(false);
            assertTrue(threeElementTable(engine).rawget(3).asBoolean(),
                    "rawget(3) must return the boolean stored at index 3");
        }

        @Test
        void rawsetRawgetAndLengthRoundTripNamed() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable table = threeElementTable(engine);
            table.rawset("Named", engine.wrapString("value"));
            assertEquals("value", table.rawget("Named").asString(),
                    "a named key must round-trip through rawset/rawget");
        }

        @Test
        void rawsetRawgetAndLengthRoundTripAbsent() {
            ILuaEngine engine = new LuavaEngine(false);
            assertTrue(threeElementTable(engine).rawget("Absent").isNil(),
                    "missing keys must read as nil");
        }
    }

    @Nested
    class MapAndForEach {

        private ILuaTable alphaBeta(ILuaEngine engine) {
            ILuaTable table = engine.createTable();
            table.rawset(ALPHA, 1.0);
            table.rawset(BETA, 2.0);
            return table;
        }

        @Test
        void mapExposesEveryEntry() {
            ILuaEngine engine = new LuavaEngine(false);
            Map<ILuaValue, ILuaValue> map = alphaBeta(engine).asMap();
            assertEquals(2, map.size(), "asMap must expose every entry");
        }

        @Test
        void mapKeysCoverBothEntries() {
            ILuaEngine engine = new LuavaEngine(false);
            Set<String> keys = new HashSet<>();
            for (ILuaValue key : alphaBeta(engine).asMap().keySet()) {
                keys.add(key.asString());
            }
            assertTrue(keys.contains(ALPHA) && keys.contains(BETA),
                    "map keys must cover Alpha and Beta");
        }

        @Test
        void forEachVisitsEntriesWithCorrectTypes() {
            ILuaEngine engine = new LuavaEngine(false);
            List<String> problems = new ArrayList<>();
            alphaBeta(engine).forEach((key, value) -> {
                if (!key.isString()) {
                    problems.add("non-string key: " + key);
                }
                if (!value.isNumber()) {
                    problems.add("non-number value: " + value);
                }
            });
            assertTrue(problems.isEmpty(),
                    "forEach must visit string keys with number values: " + problems);
        }

        @Test
        void forEachVisitsEveryEntry() {
            ILuaEngine engine = new LuavaEngine(false);
            AtomicInteger visited = new AtomicInteger();
            alphaBeta(engine).forEach((key, value) -> visited.incrementAndGet());
            assertEquals(2, visited.get(), "forEach must visit every entry");
        }
    }

    @Nested
    class MetatableBridge {

        /** A probe table whose __index supplies MISSING and whose __newindex records the write. */
        private ILuaEngine metatableProbe() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable meta = engine.createTable();
            meta.rawset(META_INDEX, args -> engine.wrapString(FROM_INDEX));
            meta.rawset(META_NEWINDEX, args -> {
                ILuaTable globals = engine.getGlobalEnvironment();
                globals.rawset(NEW_INDEX_KEY, args[1]);
                globals.rawset(NEW_INDEX_VALUE, args[2]);
                return null;
            });
            ILuaTable table = engine.createTable();
            table.setMetatable(meta);
            engine.getGlobalEnvironment().rawset(META_PROBE, table);
            engine.executeString(METATABLE_SCRIPT, "metatable_check");
            return engine;
        }

        @Test
        void metatableIndexAndNewIndexMetamethodsAreAppliedGetAndClearLastExecutionError() {
            ILuaEngine engine = metatableProbe();
            assertNull(engine.getAndClearLastExecutionError(),
                    "the __index/__newindex metamethod script must run without a Lua error");
        }

        @Test
        void metatableIndexAndNewIndexMetamethodsAreAppliedNewIndexKey() {
            ILuaEngine engine = metatableProbe();
            assertEquals(MISSING, engine.getGlobalEnvironment().rawget(NEW_INDEX_KEY).asString(),
                    "__newindex must receive the written key");
        }

        @Test
        void metatableIndexAndNewIndexMetamethodsAreAppliedNewIndexValue() {
            ILuaEngine engine = metatableProbe();
            assertEquals(WRITTEN, engine.getGlobalEnvironment().rawget(NEW_INDEX_VALUE).asString(),
                    "__newindex must receive the written value");
        }

        @Test
        void metatableIndexAndNewIndexMetamethodsAreAppliedMissing() {
            ILuaEngine engine = metatableProbe();
            assertTrue(engine.getGlobalEnvironment().rawget(META_PROBE).asTable().rawget(MISSING).isNil(),
                    "rawget must bypass __newindex");
        }
    }

    @Nested
    class ForeignValues {

        @Test
        void foreignValueIsRejectedByRawsetRejectsForeign() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable table = engine.createTable();
            assertThrows(IllegalArgumentException.class,
                    () -> table.rawset(FOREIGN, foreignValue(engine)),
                    "a foreign ILuaValue must be rejected loudly, never silently stored");
        }

        @Test
        void foreignValueIsRejectedByRawsetLuavaLuaValue() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable table = engine.createTable();
            IllegalArgumentException error;
            try {
                table.rawset(FOREIGN, foreignValue(engine));

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                error = expectedFailure;
            }
            assertTrue(error.getMessage().contains("LuavaLuaValue"),
                    "the error must name the expected implementation: " + error.getMessage());
        }

        @Test
        void foreignValueIsRejectedByRawsetNative() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable table = engine.createTable();
            assertThrows(IllegalArgumentException.class,
                    () -> table.rawset(FOREIGN, foreignValue(engine)),
                    "a foreign ILuaValue must be rejected loudly, never silently stored");
        }

        @Test
        void nativeValueIsStillAcceptedAfterAForeignRejection() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable table = engine.createTable();
            table.rawset(NATIVE, engine.wrapString("ok"));
            assertEquals("ok", table.rawget(NATIVE).asString(),
                    "a native ILuaValue must still be stored after a foreign value was rejected");
        }
    }

    @Nested
    class ForeignMetatable {

        private ILuaTable fallbackProbe(ILuaEngine engine) {
            ILuaTable meta = engine.createTable();
            meta.rawset(META_INDEX, args -> engine.wrapString(FALLBACK));
            ILuaTable table = engine.createTable();
            table.setMetatable(meta);
            engine.getGlobalEnvironment().rawset(META_AFTER_FOREIGN, table);
            engine.executeString("assert(" + META_AFTER_FOREIGN + ".Missing == '" + FALLBACK + "')",
                    "foreign_meta_before");
            return table;
        }

        /** Probes the original metatable, then asserts that a foreign replacement is rejected. */
        private ILuaEngine rejectForeignReplacement() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable table = fallbackProbe(engine);
            assertThrows(IllegalArgumentException.class,
                    () -> table.setMetatable(foreignTable(engine)),
                    "a foreign ILuaTable metatable must be rejected loudly");
            return engine;
        }

        @Test
        void foreignMetatableIsRejectedGetAndClearLastExecutionError() {
            ILuaEngine engine = new LuavaEngine(false);
            fallbackProbe(engine);
            assertNull(engine.getAndClearLastExecutionError(),
                    "a legitimate __index fallback must work before any rejection");
        }

        @Test
        void foreignMetatableIsRejectedRejectsAForeignILuaTableMetatableMustBeRejected() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable table = fallbackProbe(engine);
            assertThrows(IllegalArgumentException.class,
                    () -> table.setMetatable(foreignTable(engine)),
                    "a foreign ILuaTable metatable must be rejected loudly");
        }

        @Test
        void foreignMetatableIsRejectedLuavaLuaTable() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable table = fallbackProbe(engine);
            engine.executeString("assert(" + META_AFTER_FOREIGN + ".Missing == '" + FALLBACK + "')",
                    "foreign_meta_after");
            IllegalArgumentException error;
            try {
                table.setMetatable(foreignTable(engine));

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                error = expectedFailure;
            }
            assertTrue(error.getMessage().contains("LuavaLuaTable"),
                    "the error must name the expected implementation: " + error.getMessage());
        }

        @Test
        void foreignMetatableIsRejectedTheOriginalMetatableMustSurviveARejected() {
            ILuaEngine engine = rejectForeignReplacement();
            engine.executeString("assert(" + META_AFTER_FOREIGN + ".Missing == '" + FALLBACK + "')",
                    "foreign_meta_after");
            assertNull(engine.getAndClearLastExecutionError(),
                    "the original metatable must survive a rejected replacement");
        }
    }

    @Nested
    class TableCallbacks {

        @Test
        void tableCallbackReceivesTableArguments() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable probe = engine.createTable();
            probe.rawset(READ, args -> {
                ILuaTable input = args[args.length - 1].asTable();
                return engine.wrapString(input.rawget("name").asString());
            });
            probe.rawset(ECHO, args -> args[args.length - 1]);
            engine.getGlobalEnvironment().rawset(PROBE, probe);

            engine.executeString("""
                    local result = Probe:read({ name = 'crystal', levels = { 1, 2, 3 } })
                    assert(result == 'crystal')
                    local echoed = Probe:echo({ name = 'x' })
                    assert(echoed.name == 'x')
                    """, "table_arg_type");
            assertNull(engine.getAndClearLastExecutionError(),
                    "table arguments and table returns must cross the bridge without error");
        }

        @Test
        void tableCallbackArgumentIsAnILuaTable() {
            ILuaEngine engine = new LuavaEngine(false);
            List<ILuaValue> seenArguments = new ArrayList<>();
            ILuaTable probe = engine.createTable();
            probe.rawset(READ, args -> {
                seenArguments.add(args[args.length - 1]);
                return engine.wrapString("crystal");
            });
            engine.getGlobalEnvironment().rawset(PROBE, probe);

            engine.executeString("""
                    local result = Probe:read({ name = 'crystal', levels = { 1, 2, 3 } })
                    assert(result == 'crystal')
                    """, "table_arg_type_callback");

            assertTrue(seenArguments.get(0) instanceof ILuaTable,
                    "table arguments must wrap as ILuaTable, got "
                            + seenArguments.get(0).getClass().getName());
        }

        @Test
        void callFunctionReturnValuesKeepTheirTableType() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable globals = engine.getGlobalEnvironment();

            ILuaTable probe = engine.createTable();
            probe.rawset(ECHO, args -> args[args.length - 1]);
            globals.rawset(PROBE, probe);

            // Function return values wrapped through callFunction must also be ILuaTable.
            ILuaTable argument = engine.createTable();
            ILuaValue returned = engine.callFunction(
                    globals.rawget(PROBE).asTable().rawget(ECHO), argument);
            assertTrue(returned instanceof ILuaTable,
                    "table return values must wrap as ILuaTable, got "
                            + returned.getClass().getName());
        }

        @Test
        void callFunctionReturnValueKeepsTableContents() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable globals = engine.getGlobalEnvironment();

            ILuaTable probe = engine.createTable();
            probe.rawset(ECHO, args -> args[args.length - 1]);
            globals.rawset(PROBE, probe);

            ILuaTable argument = engine.createTable();
            argument.rawset("name", engine.wrapString("returned"));
            ILuaValue returned = engine.callFunction(
                    globals.rawget(PROBE).asTable().rawget(ECHO), argument);
            assertEquals("returned", ((ILuaTable) returned).rawget("name").asString(),
                    "a table returned through callFunction must keep its entries");
        }

        @Test
        void luaValueKeepsTableType() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable inner = engine.createTable();
            ILuaValue wrapped = engine.toLuaValue(inner);
            assertTrue(wrapped instanceof ILuaTable,
                    "tables converted through toLuaValue must stay ILuaTable");
        }
    }
}

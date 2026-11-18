package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** SPI-level coverage of LuavaLuaTable, including metatable and foreign-impl paths. */
public class LuavaLuaTableTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    /** A foreign ILuaTable implementation whose methods delegate to a real table. */
    private static ILuaTable foreignTable(ILuaEngine engine) {
        ILuaTable delegate = engine.createTable();
        return (ILuaTable) Proxy.newProxyInstance(
            ILuaTable.class.getClassLoader(),
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
    private static ILuaValue foreignValue(ILuaEngine engine) {
        ILuaValue delegate = engine.wrapString("foreign");
        return (ILuaValue) Proxy.newProxyInstance(
            ILuaValue.class.getClassLoader(),
            new Class<?>[]{ILuaValue.class},
            (proxy, method, args) -> {
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
    }

    @Test
    void rawsetRawgetAndLengthRoundTrip() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable table = engine.createTable();

        table.rawset(1, "first");
        table.rawset(2, 2.5);
        table.rawset(3, true);
        table.rawset("Named", engine.wrapString("value"));

        assertEquals(3, table.length(), "the array part must be three elements long");
        assertEquals("first", table.rawget(1).asString());
        assertEquals(2.5, table.rawget(2).asDouble());
        assertTrue(table.rawget(3).asBoolean());
        assertEquals("value", table.rawget("Named").asString());
        assertTrue(table.rawget("Absent").isNil(), "missing keys must read as nil");
    }

    @Test
    void asMapAndForEachVisitEveryEntry() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable table = engine.createTable();
        table.rawset("Alpha", 1.0);
        table.rawset("Beta", 2.0);

        Map<ILuaValue, ILuaValue> map = table.asMap();
        assertEquals(2, map.size());
        Set<String> keys = new HashSet<>();
        for (ILuaValue key : map.keySet()) {
            keys.add(key.asString());
        }
        assertTrue(keys.contains("Alpha"));
        assertTrue(keys.contains("Beta"));

        AtomicInteger visited = new AtomicInteger();
        table.forEach((key, value) -> {
            visited.incrementAndGet();
            assertTrue(key.isString());
            assertTrue(value.isNumber());
        });
        assertEquals(2, visited.get(), "forEach must visit every entry");
    }

    @Test
    void metatableIndexAndNewIndexMetamethodsAreApplied() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable table = engine.createTable();
        ILuaTable meta = engine.createTable();

        meta.rawset("__index", args -> engine.wrapString("from-index"));
        meta.rawset("__newindex", args -> {
            ILuaTable globals = engine.getGlobalEnvironment();
            globals.rawset("_newIndexKey", args[1]);
            globals.rawset("_newIndexValue", args[2]);
            return null;
        });
        table.setMetatable(meta);

        ILuaTable globals = engine.getGlobalEnvironment();
        globals.rawset("_metaProbe", table);
        engine.executeString(
            "assert(_metaProbe.Missing == 'from-index', '__index must supply missing keys')\n" +
            "_metaProbe.Missing = 'written'\n" +
            "assert(_newIndexKey == 'Missing')\n" +
            "assert(_newIndexValue == 'written')",
            "metatable_check"
        );

        assertNull(engine.getAndClearLastExecutionError());
        assertEquals("Missing", globals.rawget("_newIndexKey").asString());
        assertEquals("written", globals.rawget("_newIndexValue").asString());
        assertTrue(table.rawget("Missing").isNil(), "rawget must bypass __newindex");
    }

    @Test
    void foreignValueIsRejectedByRawset() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable table = engine.createTable();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> table.rawset("foreign", foreignValue(engine)),
            "a foreign ILuaValue must be rejected loudly, never silently stored");
        assertTrue(error.getMessage().contains("LuavaLuaValue"),
            "the error must name the expected implementation: " + error.getMessage());

        // The table must remain usable after the rejected value.
        table.rawset("native", engine.wrapString("ok"));
        assertEquals("ok", table.rawget("native").asString());
    }

    @Test
    void foreignMetatableIsRejected() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable table = engine.createTable();
        ILuaTable meta = engine.createTable();
        meta.rawset("__index", args -> engine.wrapString("fallback"));
        table.setMetatable(meta);

        ILuaTable globals = engine.getGlobalEnvironment();
        globals.rawset("_metaAfterForeign", table);
        engine.executeString("assert(_metaAfterForeign.Missing == 'fallback')", "foreign_meta_before");
        assertNull(engine.getAndClearLastExecutionError());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> table.setMetatable(foreignTable(engine)),
            "a foreign ILuaTable metatable must be rejected loudly");
        assertTrue(error.getMessage().contains("LuavaLuaTable"),
            "the error must name the expected implementation: " + error.getMessage());

        engine.executeString("assert(_metaAfterForeign.Missing == 'fallback')", "foreign_meta_after");
        assertNull(engine.getAndClearLastExecutionError(),
            "the original metatable must survive a rejected replacement");
    }

    @Test
    void tableArgumentsAndReturnValuesKeepTheirTableType() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        // A Java lambda bound into a table must receive a real ILuaTable for table arguments,
        // not a plain value wrapper, or every `instanceof ILuaTable` check in a binding fails.
        ILuaTable probe = engine.createTable();
        probe.rawset("read", args -> {
            assertTrue(args.length > 0, "expected one argument");
            // Colon calls pass the table itself first; take the last argument.
            ILuaValue argument = args[args.length - 1];
            assertTrue(argument instanceof ILuaTable,
                "table arguments must wrap as ILuaTable, got " + argument.getClass().getName());
            ILuaTable input = (ILuaTable) argument;
            return engine.wrapString(input.rawget("name").asString());
        });
        probe.rawset("echo", args -> args[args.length - 1]);
        globals.rawset("Probe", probe);

        engine.executeString(
            "local result = Probe:read({ name = 'crystal', levels = { 1, 2, 3 } })\n" +
            "assert(result == 'crystal')\n" +
            "local echoed = Probe:echo({ name = 'x' })\n" +
            "assert(echoed.name == 'x')",
            "table_arg_type"
        );
        assertNull(engine.getAndClearLastExecutionError());

        // Function return values wrapped through callFunction must also be ILuaTable.
        ILuaTable argument = engine.createTable();
        argument.rawset("name", engine.wrapString("returned"));
        ILuaValue returned = engine.callFunction(
            globals.rawget("Probe").asTable().rawget("echo"), argument);
        assertTrue(returned instanceof ILuaTable,
            "table return values must wrap as ILuaTable, got " + returned.getClass().getName());
        assertEquals("returned", ((ILuaTable) returned).rawget("name").asString());

        ILuaTable inner = engine.createTable();
        inner.rawset("name", engine.wrapString("nested"));
        ILuaValue wrapped = engine.toLuaValue(inner);
        assertTrue(wrapped instanceof ILuaTable);
    }
}

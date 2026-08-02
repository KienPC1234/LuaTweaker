package com.luatweaker.core.bind;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LuaBinderTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    public interface MockApi {
        String greet(String name);
        double add(double a, double b);
        boolean isActive();
        void logAll(String message, @LuaDefault("3") int times);
        void announce(String title, @LuaDefault("hello") String subtitle);
        IEntity target(IEntity entity);
        Object passthrough(Object value);
    }

    public static class MockImpl implements MockApi {
        final List<String> calls = new ArrayList<>();

        @Override public String greet(String name) { calls.add("greet:" + name); return "hi " + name; }
        @Override public double add(double a, double b) { return a + b; }
        @Override public boolean isActive() { return true; }
        @Override public void logAll(String message, int times) { calls.add("logAll:" + message + ":" + times); }
        @Override public void announce(String title, String subtitle) { calls.add("announce:" + title + ":" + subtitle); }
        @Override public IEntity target(IEntity entity) { calls.add("target:" + (entity != null ? entity.getName() : "null")); return entity; }
        @Override public Object passthrough(Object value) { calls.add("passthrough:" + (value != null ? value.getClass().getSimpleName() : "null")); return value; }
    }

    private static final class MockEntity implements IEntity {
        @Override public String getType() { return "minecraft:zombie"; }
        @Override public String getName() { return "Zed"; }
        @Override public float getHealth() { return 20; }
        @Override public void setHealth(float health) {}
        @Override public float getMaxHealth() { return 20; }
        @Override public boolean isAlive() { return true; }
        @Override public void remove() {}
        @Override public Object getRawEntity() { return this; }
    }

    @Test
    public void bindsMethodsWithPascalCaseAliasesAndConversions() {
        ILuaEngine engine = new CobaltLuaEngine();
        MockImpl impl = new MockImpl();
        LuaBinder.bind(engine, "MockApi", impl, MockApi.class);

        engine.executeString(
            "local result = MockApi:greet('Dev')\n" +
            "assert(result == 'hi Dev', 'greet failed: ' .. tostring(result))\n" +
            "assert(type(MockApi.Greet) == 'function', 'PascalCase alias missing')\n" +
            "assert(MockApi:add(1, 2) == 3, 'add failed')\n" +
            "assert(MockApi:isActive() == true, 'isActive failed')\n" +
            "MockApi:logAll('fire')\n" +
            "MockApi.LogAll('fire2', 9)\n" +
            "MockApi:announce('Title')\n",
            "binder_test"
        );

        assertTrue(impl.calls.contains("greet:Dev"), impl.calls.toString());
        assertTrue(impl.calls.contains("logAll:fire:3"), "default int arg not applied: " + impl.calls);
        assertTrue(impl.calls.contains("logAll:fire2:9"), impl.calls.toString());
        assertTrue(impl.calls.contains("announce:Title:hello"), "default string arg not applied: " + impl.calls);
    }

    @Test
    public void unwrapsEntityTablesAndConvertsEntitiesOnReturn() {
        ILuaEngine engine = new CobaltLuaEngine();
        MockImpl impl = new MockImpl();
        LuaBinder.registerReturnConverter(IEntity.class, (e, value) -> {
            ILuaTable table = e.createTable();
            table.rawset("Name", e.wrapString(value instanceof IEntity i ? i.getName() : "?"));
            return table;
        });
        LuaBinder.bind(engine, "MockApi", impl, MockApi.class);

        ILuaTable entityTable = engine.createTable();
        entityTable.rawset("__entity", engine.wrapUserdata(new MockEntity()));
        engine.getGlobalEnvironment().rawset("_ent", entityTable);

        engine.executeString(
            "local got = MockApi:target(_G._ent)\n" +
            "assert(got ~= nil and got.Name == 'Zed', 'entity return conversion failed: ' .. tostring(got and got.Name))\n",
            "binder_entity_test"
        );

        assertTrue(impl.calls.contains("target:Zed"), impl.calls.toString());
    }

    @Test
    public void missingRequiredArgumentRaisesLuaError() {
        ILuaEngine engine = new CobaltLuaEngine();
        LuaBinder.bind(engine, "MockApi", new MockImpl(), MockApi.class);

        engine.executeString(
            "local ok, err = pcall(function() MockApi:greet() end)\n" +
            "assert(not ok, 'missing required argument must raise a Lua error')\n" +
            "assert(tostring(err):find('argument 1') ~= nil, 'error should name the missing argument: ' .. tostring(err))\n",
            "binder_error_test"
        );
    }

    @Test
    public void objectParameterReceivesOriginalLuaValue() {
        ILuaEngine engine = new CobaltLuaEngine();
        MockImpl impl = new MockImpl();
        LuaBinder.bind(engine, "MockApi", impl, MockApi.class);

        engine.executeString(
            "local fn = function() end\n" +
            "local got = MockApi:passthrough(fn)\n" +
            "assert(type(got) == 'function', 'passthrough should return the ILuaValue, got: ' .. type(got))\n",
            "binder_object_test"
        );

        assertTrue(impl.calls.stream().anyMatch(c -> c.startsWith("passthrough")), impl.calls.toString());
    }
}

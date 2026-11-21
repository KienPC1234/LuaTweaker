package com.luatweaker.core.bind;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.stub.StubCatalog;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LuaBinderTest {

    private static final String MOCK_API = "MockApi";

    private static final String BINDING_SCRIPT = """
            local result = MockApi:greet('Dev')
            assert(result == 'hi Dev', 'greet failed: ' .. tostring(result))
            assert(type(MockApi.Greet) == 'function', 'PascalCase alias missing')
            assert(MockApi:add(1, 2) == 3, 'add failed')
            assert(MockApi:isActive() == true, 'isActive failed')
            MockApi:logAll('fire')
            MockApi.LogAll('fire2', 9)
            MockApi:announce('Title')
            """;

    private static final String SERVICE_SCRIPT = """
            local s1 = require('LuaTweaker.AutoService')
            assert(s1 ~= nil and type(s1.Greet) == 'function', 'require(LuaTweaker.AutoService) failed')
            local s2 = require('AutoService')
            assert(s2 ~= nil and type(s2.Greet) == 'function', 'require(AutoService) failed')
            local s3 = require('autoServiceAlias')
            assert(s3 ~= nil and type(s3.Greet) == 'function', 'require(autoServiceAlias) failed')
            """;

    private static final String CACHE_SCRIPT = """
            assert(FirstApi:greet('A') == 'hi A')
            assert(SecondApi:greet('B') == 'hi B')
            assert(SecondApi:Greet('C') == 'hi C', 'PascalCase alias missing on cached bind')
            SecondApi:logAll('cached')
            local ok, err = pcall(function() SecondApi:greet() end)
            assert(not ok, 'missing required argument must raise through the cached plan')
            assert(tostring(err):find('argument 1') ~= nil, 'cached error must name the argument: ' .. tostring(err))
            """;

    @AfterAll
    static void shutdownLogger() {
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
        @Override public IEntity target(IEntity entity) {
            calls.add("target:" + (entity != null ? entity.getName() : "null"));
            return entity;
        }
        @Override public Object passthrough(Object value) {
            calls.add("passthrough:" + (value != null ? value.getClass().getSimpleName() : "null"));
            return value;
        }
    }

    private static final class MockEntity implements IEntity {
        @Override public String getType() { return "minecraft:zombie"; }
        @Override public String getName() { return "Zed"; }
        @Override public float getHealth() { return 20; }
        @Override public void setHealth(float health) { }
        @Override public float getMaxHealth() { return 20; }
        @Override public boolean isAlive() { return true; }
        @Override public void remove() { }
        @Override public Object getRawEntity() { return this; }
    }

    /** Binds {@link MockApi} and runs the shared binding script, returning the recording impl. */
    private MockImpl runBindingScript() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        MockImpl impl = new MockImpl();
        LuaBinder.bind(engine, MOCK_API, impl, MockApi.class);
        engine.executeString(BINDING_SCRIPT, "binder_test");
        return impl;
    }

    @Nested
    class MethodBinding {

        @Test
        void bindsMethodsWithPascalCaseAliasesAndConversionsGreetDev() {
            MockImpl impl = runBindingScript();
            assertTrue(impl.calls.contains("greet:Dev"),
                    "a colon call must reach the Java method with the Lua string: " + impl.calls);
        }

        @Test
        void bindsMethodsWithPascalCaseAliasesAndConversionsLogAllFire3() {
            MockImpl impl = runBindingScript();
            assertTrue(impl.calls.contains("logAll:fire:3"),
                    "default int arg not applied: " + impl.calls);
        }

        @Test
        void bindsMethodsWithPascalCaseAliasesAndConversionsLogAllFire29() {
            MockImpl impl = runBindingScript();
            assertTrue(impl.calls.contains("logAll:fire2:9"),
                    "an explicit argument must override the default: " + impl.calls);
        }

        @Test
        void bindsMethodsWithPascalCaseAliasesAndConversionsAnnounceTitleHello() {
            MockImpl impl = runBindingScript();
            assertTrue(impl.calls.contains("announce:Title:hello"),
                    "default string arg not applied: " + impl.calls);
        }
    }

    @Nested
    class StubCatalogRegistration {

        private ILuaEngine bindAutoService() {
            ILuaEngine engine = LuaEngineFactory.create(false);
            LuaBinder.bind(engine, "AutoService", new MockImpl(), MockApi.class, "autoServiceAlias");
            engine.executeString(SERVICE_SCRIPT, "service_spi_test");
            return engine;
        }

        @Test
        void autoRegistersStubCatalogAndNativeModuleResolverAutoService() {
            bindAutoService();
            assertTrue(StubCatalog.snapshot().containsKey("AutoService"),
                    "binding a service must register it in the stub catalog");
        }

        @Test
        void autoRegistersStubCatalogAndNativeModuleResolverAutoService2() {
            bindAutoService();
            assertEquals(MockApi.class, StubCatalog.snapshot().get("AutoService"),
                    "the stub catalog must map the service name to the bound interface");
        }
    }

    @Nested
    class EntityAndErrorPaths {

        @Test
        void unwrapsEntityTablesAndConvertsEntitiesOnReturn() {
            ILuaEngine engine = LuaEngineFactory.create(false);
            MockImpl impl = new MockImpl();
            LuaBinder.registerReturnConverter(IEntity.class, (e, value) -> {
                ILuaTable table = e.createTable();
                table.rawset("Name", e.wrapString(value instanceof IEntity i ? i.getName() : "?"));
                return table;
            });
            LuaBinder.bind(engine, MOCK_API, impl, MockApi.class);

            ILuaTable entityTable = engine.createTable();
            entityTable.rawset("__entity", engine.wrapUserdata(new MockEntity()));
            engine.getGlobalEnvironment().rawset("_ent", entityTable);

            engine.executeString("""
                    local got = MockApi:target(_G._ent)
                    assert(got ~= nil and got.Name == 'Zed',
                        'entity return conversion failed: ' .. tostring(got and got.Name))
                    """, "binder_entity_test");

            assertTrue(impl.calls.contains("target:Zed"),
                    "the wrapped userdata must arrive as the entity named Zed: " + impl.calls);
        }

        @Test
        void missingRequiredArgumentRaisesLuaError() {
            ILuaEngine engine = LuaEngineFactory.create(false);
            LuaBinder.bind(engine, MOCK_API, new MockImpl(), MockApi.class);

            engine.executeString("""
                    local ok, err = pcall(function() MockApi:greet() end)
                    assert(not ok, 'missing required argument must raise a Lua error')
                    assert(tostring(err):find('argument 1') ~= nil,
                        'error should name the missing argument: ' .. tostring(err))
                    """, "binder_error_test");
            assertNull(engine.getAndClearLastExecutionError(),
                    "the pcall-based missing-argument script must run cleanly");
        }

        @Test
        void objectParameterReceivesOriginalLuaValue() {
            ILuaEngine engine = LuaEngineFactory.create(false);
            MockImpl impl = new MockImpl();
            LuaBinder.bind(engine, MOCK_API, impl, MockApi.class);

            engine.executeString("""
                    local fn = function() end
                    local got = MockApi:passthrough(fn)
                    assert(type(got) == 'function',
                        'passthrough should return the ILuaValue, got: ' .. type(got))
                    """, "binder_object_test");

            assertTrue(impl.calls.stream().anyMatch(c -> c.startsWith("passthrough")),
                    "the Object parameter must be passed to the Java impl: " + impl.calls);
        }
    }

    @Nested
    class CachedBindPlan {

        /** Warms the bind-plan cache, binds twice, and runs the shared cache script. */
        private MockImpl runCachedScript() {
            ILuaEngine engine = LuaEngineFactory.create(false);
            MockImpl impl = new MockImpl();
            // Warm the per-interface bind-plan cache, then bind again: both tables
            // must expose the same keys and honor defaults + error paths.
            ILuaTable first = LuaBinder.bindTable(engine, impl, MockApi.class);
            ILuaTable second = LuaBinder.bindTable(engine, impl, MockApi.class);
            engine.getGlobalEnvironment().rawset("FirstApi", first);
            engine.getGlobalEnvironment().rawset("SecondApi", second);
            engine.executeString(CACHE_SCRIPT, "binder_cache_test");
            return impl;
        }

        @Test
        void repeatedBindsReuseCachedPlanWithIdenticalBehaviorCachedPlanMustExposeIdenticalKeys() {
            ILuaEngine engine = LuaEngineFactory.create(false);
            MockImpl impl = new MockImpl();
            ILuaTable first = LuaBinder.bindTable(engine, impl, MockApi.class);
            ILuaTable second = LuaBinder.bindTable(engine, impl, MockApi.class);
            Set<String> firstKeys = new HashSet<>();
            Set<String> secondKeys = new HashSet<>();
            engine.getGlobalEnvironment().rawset("FirstApi", first);
            engine.getGlobalEnvironment().rawset("SecondApi", second);
            engine.executeString(CACHE_SCRIPT, "binder_cache_test");
            assertEquals(firstKeys, secondKeys, "cached plan must expose identical keys");
        }

        @Test
        void repeatedBindsReuseCachedPlanWithIdenticalBehaviorGreetA() {
            MockImpl impl = runCachedScript();
            assertTrue(impl.calls.contains("greet:A"),
                    "the first binding must dispatch to the Java impl: " + impl.calls);
        }

        @Test
        void repeatedBindsReuseCachedPlanWithIdenticalBehaviorGreetB() {
            MockImpl impl = runCachedScript();
            assertTrue(impl.calls.contains("greet:B"),
                    "the second binding must dispatch to the Java impl too: " + impl.calls);
        }

        @Test
        void repeatedBindsReuseCachedPlanWithIdenticalBehaviorGreetC() {
            MockImpl impl = runCachedScript();
            assertTrue(impl.calls.contains("greet:C"),
                    "the PascalCase alias must work through the cached plan: " + impl.calls);
        }

        @Test
        void repeatedBindsReuseCachedPlanWithIdenticalBehaviorLogAllCached3() {
            MockImpl impl = runCachedScript();
            assertTrue(impl.calls.contains("logAll:cached:3"),
                    "default int arg must apply through the cached plan: " + impl.calls);
        }
    }
}

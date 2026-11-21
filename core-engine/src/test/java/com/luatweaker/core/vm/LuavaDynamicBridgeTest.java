package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.remap.RuntimeRemapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the luava dynamic bridge: getters, setters, overloads, aliases and the blacklist. */
class LuavaDynamicBridgeTest {

    private static final String PROPERTY_SCRIPT = """
            assert(type(_bridge.Entity) == 'string', 'Entity alias must be a value, not a function')
            assert(_bridge.Entity == 'minecraft:player')
            assert(_bridge:getEntity() == 'minecraft:player', 'method-call syntax must still work')
            assert(_bridge.IsAlive == true, 'IsAlive alias must be boolean')
            assert(_bridge.isAlive == true, 'isAlive getter must be boolean')
            assert(_bridge.Amount == 0, 'Amount must read through its getter')
            _bridge.Amount = 40
            assert(_bridge.Amount == 40, 'setter must mutate the Java object')
            assert(_bridge:getAmount() == 40, 'getter method call must see the mutation')
            """;

    private static final String REMAPPER_SCRIPT = """
            assert(_remapProbe.Entity == 'minecraft:player', 'the bridge must use the remapped getter')
            """;

    private static final String PROBE_GLOBAL = "_bridge";
    private static final String REMAP_GLOBAL = "_remapProbe";
    private static final String OVERLOAD_GLOBAL = "_overloads";
    private static final String BLACKLIST_GLOBAL = "_blacklistProbe";

    public static class BridgeProbe {
        private int amount;

        public String getEntity() { return "minecraft:player"; }
        public boolean isAlive() { return true; }
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
    }

    public static class OverloadProbe {
        public String pick() { return "zero"; }
        public String pick(int value) { return "int:" + value; }
        public String pick(double value) { return "double:" + value; }
        public String pick(String value) { return "string:" + value; }
    }

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private LuavaEngine engineWithBridge(BridgeProbe probe, String globalName) {
        LuavaEngine engine = new LuavaEngine(false);
        engine.getGlobalEnvironment().rawset(globalName, engine.wrapDynamicJavaObject(probe));
        return engine;
    }

    @Nested
    class WrappedValues {

        @Test
        void wrappedValueExposesTheOriginalJavaObjectCheck() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaValue wrapped = engine.wrapDynamicJavaObject(new BridgeProbe());
            assertNotNull(wrapped, "wrapping a Java object must produce a non-null Lua value");
        }

        @Test
        void wrappedValueExposesTheOriginalJavaObjectWrapped() {
            ILuaEngine engine = new LuavaEngine(false);
            BridgeProbe probe = new BridgeProbe();
            ILuaValue wrapped = engine.wrapDynamicJavaObject(probe);
            assertSame(probe, wrapped.toJavaObject(),
                    "the dynamic bridge must keep the original Java instance reachable");
        }
    }

    @Nested
    class PropertyAndOverloads {

        @Test
        void gettersSettersAndPropertyAliasesWorkThroughTheBridgeGetAndClearLastExecutionError() {
            LuavaEngine engine = engineWithBridge(new BridgeProbe(), PROBE_GLOBAL);
            engine.executeString(PROPERTY_SCRIPT, "bridge_properties");
            assertNull(engine.getAndClearLastExecutionError(),
                    "property reads, writes and aliases must all run without a Lua error");
        }

        @Test
        void gettersSettersAndPropertyAliasesWorkThroughTheBridgeTheLuaSetterMustReachTheJavaObject() {
            BridgeProbe probe = new BridgeProbe();
            LuavaEngine engine = engineWithBridge(probe, PROBE_GLOBAL);
            engine.executeString(PROPERTY_SCRIPT, "bridge_properties");
            assertEquals(40, probe.getAmount(), "the Lua setter must reach the Java object");
        }

        @Test
        void overloadResolutionUsesArgumentTypes() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.getGlobalEnvironment().rawset(OVERLOAD_GLOBAL, engine.wrapDynamicJavaObject(new OverloadProbe()));

            engine.executeString("""
                    assert(_overloads:pick() == 'zero')
                    assert(_overloads:pick(42) == 'int:42')
                    assert(_overloads:pick(4.5) == 'double:4.5')
                    assert(_overloads:pick('abc') == 'string:abc')
                    """, "bridge_overloads");
            assertNull(engine.getAndClearLastExecutionError(),
                    "each overload must be selected from the Lua argument types without error");
        }

        @Test
        void blacklistedMethodIsDenied() {
            LuavaEngine engine = engineWithBridge(new BridgeProbe(), BLACKLIST_GLOBAL);

            engine.executeString("""
                    assert(_blacklistProbe.getClass == nil, 'getClass must not be exposed')
                    local ok = pcall(function() return _blacklistProbe:getClass() end)
                    assert(not ok, 'calling a blacklisted method must fail')
                    """, "bridge_blacklist");
            assertNull(engine.getAndClearLastExecutionError(),
                    "reading and calling a blacklisted method must stay silent nil / pcall-failure only");
        }
    }

    @Nested
    class RemapperFallback {

        @Test
        void runtimeRemapperFallbackResolvesPropertyGetterTheRemapperFallbackMustMapAPropertyNameT() {
            Method resolved = RuntimeRemapper.getInstance().resolveMethod(BridgeProbe.class, "Entity");
            LuavaEngine engine = engineWithBridge(new BridgeProbe(), REMAP_GLOBAL);
            engine.executeString(REMAPPER_SCRIPT, "bridge_remapper");
            assertNotNull(resolved, "the remapper fallback must map a property name to a getter");
        }

        @Test
        void runtimeRemapperFallbackResolvesPropertyGetterGetEntity() {
            Method resolved = RuntimeRemapper.getInstance().resolveMethod(BridgeProbe.class, "Entity");
            LuavaEngine engine = engineWithBridge(new BridgeProbe(), REMAP_GLOBAL);
            engine.executeString(REMAPPER_SCRIPT, "bridge_remapper");
            assertEquals("getEntity", resolved.getName(),
                    "the remapper fallback must resolve the property to getEntity");
        }

        @Test
        void runtimeRemapperFallbackResolvesPropertyGetterGetAndClearLastExecutionError() {
            LuavaEngine engine = engineWithBridge(new BridgeProbe(), REMAP_GLOBAL);
            engine.executeString(REMAPPER_SCRIPT, "bridge_remapper");
            assertNull(engine.getAndClearLastExecutionError(),
                    "the remapped property read must not raise a Lua error");
        }
    }
}

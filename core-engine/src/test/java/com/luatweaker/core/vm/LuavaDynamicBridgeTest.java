package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.remap.RuntimeRemapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the luava dynamic bridge: getters, setters, overloads, aliases and the blacklist. */
public class LuavaDynamicBridgeTest {

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
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void wrappedValueExposesTheOriginalJavaObject() {
        ILuaEngine engine = new LuavaEngine(false);
        BridgeProbe probe = new BridgeProbe();

        ILuaValue wrapped = engine.wrapDynamicJavaObject(probe);
        assertNotNull(wrapped);
        assertSame(probe, wrapped.toJavaObject(),
            "the dynamic bridge must keep the original Java instance reachable");
    }

    @Test
    void gettersSettersAndPropertyAliasesWorkThroughTheBridge() {
        ILuaEngine engine = new LuavaEngine(false);
        BridgeProbe probe = new BridgeProbe();
        engine.getGlobalEnvironment().rawset("_bridge", engine.wrapDynamicJavaObject(probe));

        engine.executeString(
            "assert(type(_bridge.Entity) == 'string', 'Entity alias must be a value, not a function')\n" +
            "assert(_bridge.Entity == 'minecraft:player')\n" +
            "assert(_bridge:getEntity() == 'minecraft:player', 'method-call syntax must still work')\n" +
            "assert(_bridge.IsAlive == true, 'IsAlive alias must be boolean')\n" +
            "assert(_bridge.isAlive == true, 'isAlive getter must be boolean')\n" +
            "assert(_bridge.Amount == 0, 'Amount must read through its getter')\n" +
            "_bridge.Amount = 40\n" +
            "assert(_bridge.Amount == 40, 'setter must mutate the Java object')\n" +
            "assert(_bridge:getAmount() == 40, 'getter method call must see the mutation')",
            "bridge_properties"
        );
        assertNull(engine.getAndClearLastExecutionError());
        assertEquals(40, probe.getAmount(), "the Lua setter must reach the Java object");
    }

    @Test
    void overloadResolutionUsesArgumentTypes() {
        ILuaEngine engine = new LuavaEngine(false);
        engine.getGlobalEnvironment().rawset("_overloads", engine.wrapDynamicJavaObject(new OverloadProbe()));

        engine.executeString(
            "assert(_overloads:pick() == 'zero')\n" +
            "assert(_overloads:pick(42) == 'int:42')\n" +
            "assert(_overloads:pick(4.5) == 'double:4.5')\n" +
            "assert(_overloads:pick('abc') == 'string:abc')",
            "bridge_overloads"
        );
        assertNull(engine.getAndClearLastExecutionError());
    }

    @Test
    void blacklistedMethodIsDenied() {
        ILuaEngine engine = new LuavaEngine(false);
        engine.getGlobalEnvironment().rawset("_blacklistProbe", engine.wrapDynamicJavaObject(new BridgeProbe()));

        engine.executeString(
            "assert(_blacklistProbe.getClass == nil, 'getClass must not be exposed')\n" +
            "local ok = pcall(function() return _blacklistProbe:getClass() end)\n" +
            "assert(not ok, 'calling a blacklisted method must fail')",
            "bridge_blacklist"
        );
        assertNull(engine.getAndClearLastExecutionError());
    }

    @Test
    void runtimeRemapperFallbackResolvesPropertyGetter() {
        Method resolved = RuntimeRemapper.getInstance().resolveMethod(BridgeProbe.class, "Entity");
        assertNotNull(resolved, "the remapper fallback must map a property name to a getter");
        assertEquals("getEntity", resolved.getName());

        ILuaEngine engine = new LuavaEngine(false);
        engine.getGlobalEnvironment().rawset("_remapProbe", engine.wrapDynamicJavaObject(new BridgeProbe()));
        engine.executeString(
            "assert(_remapProbe.Entity == 'minecraft:player', 'the bridge must use the remapped getter')",
            "bridge_remapper"
        );
        assertNull(engine.getAndClearLastExecutionError());
    }
}

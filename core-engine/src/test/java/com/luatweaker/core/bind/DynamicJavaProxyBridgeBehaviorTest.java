package com.luatweaker.core.bind;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end bridge behavior through the public API: allowed members stay
 * invocable, blocked members stay silent nil, and property access returns
 * getter values (never wrapped method functions).
 */
class DynamicJavaProxyBridgeBehaviorTest {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    public static class GetterProbe {
        private int amount;

        public String getEntity() {
            return "minecraft:player";
        }

        public boolean isAlive() {
            return true;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }
    }

    public static class OverloadProbe {
        public String pick() {
            return "zero";
        }

        public String pick(int value) {
            return "int:" + value;
        }

        public String pick(double value) {
            return "double:" + value;
        }

        public String pick(String value) {
            return "string:" + value;
        }
    }

    public static class CustomDataProbe {
        private final Map<String, Object> data = new ConcurrentHashMap<>();

        public Object getCustomData(String key) {
            return data.get(key);
        }

        public void setCustomData(String key, Object value) {
            data.put(key, value);
        }
    }

    @Test
    void allowedMembersResolveThroughTheBridge() {
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe("_probe", new GetterProbe());
        engine.executeString(
                "assert(_probe.Entity == 'minecraft:player', 'Entity property must read via getEntity')\n"
                        + "assert(_probe:getEntity() == 'minecraft:player', 'method-call getEntity must work')\n"
                        + "assert(_probe.Amount == 0, 'Amount property must read via getAmount')",
                "bridge_allowed");
        assertNull(engine.getAndClearLastExecutionError(),
                "allowedMembersResolveThroughTheBridge: allowed members must resolve through the bridge");
    }

    @Test
    void blockedMembersStaySilentNil() {
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe("_probe", Runtime.getRuntime());
        engine.executeString(
                "assert(_probe.exec == nil, 'blocked package member must be nil')\n"
                        + "assert(_probe.getClass == nil, 'blacklisted getClass must be nil')\n"
                        + "assert(_probe.wait == nil, 'blacklisted wait must be nil')",
                "bridge_blocked");
        assertNull(engine.getAndClearLastExecutionError(),
                "blockedMembersStaySilentNil: blocked members must stay silent nil through the bridge");
    }

    /**
     * Regression: property access on a proxy must return the GETTER VALUE, not a
     * wrapped method function. The heuristic remapper matches "Entity" -> getEntity(),
     * and returning a function there breaks `event.Entity` (yielding "attempt to index
     * local 'player' (a function value)" downstream). Method-call syntax (obj:getEntity())
     * must keep working.
     */
    @Test
    void propertyAccessReturnsValueNotMethodFunction() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.getGlobalEnvironment().rawset("_proxy", engine.wrapDynamicJavaObject(new GetterProbe()));

        engine.executeString(
                "assert(type(_proxy.Entity) == 'string', 'Entity property must be a string, got ' .. type(_proxy.Entity))\n"
                        + "assert(_proxy.Entity == 'minecraft:player', 'Entity value mismatch')\n"
                        + "local ent = _proxy:getEntity()\n"
                        + "assert(type(ent) == 'string' and ent == 'minecraft:player', 'method-call getEntity must return value')\n"
                        + "assert(_proxy.IsAlive == true, 'IsAlive property must be boolean')\n"
                        + "assert(_proxy.isAlive == true, 'isAlive property must be boolean')\n"
                        + "assert(_proxy.Amount == 0, 'Amount property must read via getter')\n"
                        + "_proxy:setAmount(40)\n"
                        + "assert(_proxy.Amount == 40, 'setAmount must mutate state')\n"
                        + "assert(_proxy:getAmount() == 40, 'getAmount method call must return value')",
                "proxy_getter_repro");
        assertNull(engine.getAndClearLastExecutionError(),
                "propertyAccessReturnsValueNotMethodFunction: proxy property access must return getter values");
    }

    @Test
    void methodCallSelectsOverloadByArguments() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.getGlobalEnvironment().rawset("_proxy", engine.wrapDynamicJavaObject(new OverloadProbe()));

        engine.executeString(
                "assert(_proxy:pick() == 'zero')\n"
                        + "assert(_proxy:pick(42) == 'int:42')\n"
                        + "assert(_proxy.pick(_proxy, 4.5) == 'double:4.5')\n"
                        + "assert(_proxy:pick('abc') == 'string:abc')",
                "proxy_overload_repro");
        assertNull(engine.getAndClearLastExecutionError(),
                "methodCallSelectsOverloadByArguments: overload resolution must follow argument types");
    }

    @Test
    void pascalCaseMethodDispatchOnProxy() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.getGlobalEnvironment().rawset("_item", engine.wrapDynamicJavaObject(new CustomDataProbe()));

        engine.executeString(
                "_item:SetCustomData('Energy', 50)\n"
                        + "local energy = _item:GetCustomData('Energy')\n"
                        + "assert(energy == 50, 'GetCustomData must return 50, got ' .. tostring(energy))",
                "proxy_pascal_case_repro");
        assertNull(engine.getAndClearLastExecutionError(),
                "pascalCaseMethodDispatchOnProxy: PascalCase calls must dispatch to camelCase methods");
    }
}

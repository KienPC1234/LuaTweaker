package com.luatweaker.core.bind;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The method blacklist denies getClass/wait-style escape hatches while
 * ordinary classes (including cross-mod packages) stay fully usable
 * through the public dynamic bridge.
 */
class DynamicJavaProxyMethodBlacklistTest {

    /** Global name the probe userdata is bound to in every case below. */
    private static final String PROBE = "_probe";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void smartBlacklistAllowsMinecraftClasses() {
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe(PROBE, "hello");
        engine.executeString(
                "assert(_probe:length() == 5, 'String.length must stay accessible')",
                "allowlist_string");
        assertNull(engine.getAndClearLastExecutionError(),
                "smartBlacklistAllowsMinecraftClasses: allowed classes must keep working through the bridge");
    }

    @Test
    void smartBlacklistAllowsJavaUtilClasses() {
        List<String> items = new ArrayList<>();
        items.add("first");
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe(PROBE, items);
        engine.executeString(
                "assert(_probe:size() == 1, 'ArrayList.size must stay accessible')\n"
                        + "assert(_probe:get(0) == 'first', 'ArrayList.get must stay accessible')",
                "allowlist_util");
        assertNull(engine.getAndClearLastExecutionError(),
                "smartBlacklistAllowsJavaUtilClasses: java.util classes must keep working through the bridge");
    }

    @Test
    void smartBlacklistAllowsCrossModPackages() {
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe(
                PROBE, Duration.ofSeconds(5));
        engine.executeString(
                "assert(_probe:getSeconds() == 5, 'java.time access must stay accessible')",
                "allowlist_cross_mod");
        assertNull(engine.getAndClearLastExecutionError(),
                "smartBlacklistAllowsCrossModPackages: non-blacklisted packages must keep working through the bridge");
    }

    @Test
    void methodBlacklistBlocksGetClassMethod() {
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe(PROBE, "probe");
        engine.executeString(
                "assert(_probe.getClass == nil, 'getClass must not be exposed')",
                "blocklist_get_class");
        assertNull(engine.getAndClearLastExecutionError(),
                "methodBlacklistBlocksGetClassMethod: blacklisted getClass must stay silent nil");
    }

    @Test
    void methodBlacklistBlocksWaitMethod() {
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe(PROBE, "probe");
        engine.executeString(
                "assert(_probe.wait == nil, 'wait must not be exposed')",
                "blocklist_wait");
        assertNull(engine.getAndClearLastExecutionError(),
                "methodBlacklistBlocksWaitMethod: blacklisted wait must stay silent nil");
    }
}

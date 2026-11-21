package com.luatweaker.core.bind;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;

/** Shared probe setup for the dynamic-proxy test suites. */
final class DynamicJavaProxyTestSupport {

    private DynamicJavaProxyTestSupport() {
    }

    static ILuaEngine engineWithProbe(String global, Object probe) {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.getGlobalEnvironment().rawset(global, engine.wrapDynamicJavaObject(probe));
        return engine;
    }
}

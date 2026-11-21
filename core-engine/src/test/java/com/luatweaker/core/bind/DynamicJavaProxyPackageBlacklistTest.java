package com.luatweaker.core.bind;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The package blacklist must stay silent nil through the public bridge:
 * any property read on a blocked-class instance yields nil instead of
 * leaking a method handle into Lua.
 */
class DynamicJavaProxyPackageBlacklistTest {

    /** Global name the probe userdata is bound to in every case below. */
    private static final String PROBE = "_probe";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void smartBlacklistBlocksRuntimeClass() {
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe(PROBE, Runtime.getRuntime());
        engine.executeString(
                "assert(_probe.exec == nil, 'Runtime.exec must be blocked')",
                "blacklist_runtime");
        assertNull(engine.getAndClearLastExecutionError(),
                "smartBlacklistBlocksRuntimeClass: blocked Runtime access must stay silent nil");
    }

    @Test
    void smartBlacklistBlocksProcessBuilder() {
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe(PROBE, new ProcessBuilder("echo"));
        engine.executeString(
                "assert(_probe.start == nil, 'ProcessBuilder.start must be blocked')",
                "blacklist_process_builder");
        assertNull(engine.getAndClearLastExecutionError(),
                "smartBlacklistBlocksProcessBuilder: blocked ProcessBuilder access must stay silent nil");
    }

    @Test
    void smartBlacklistBlocksIoPackage() {
        // System has no instances, so the adjacent java.io entry proves the same
        // package-prefix code path through a reachable blocked class.
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe(
                PROBE, new java.io.ByteArrayOutputStream());
        engine.executeString(
                "assert(_probe.write == nil, 'java.io writes must be blocked')",
                "blacklist_io");
        assertNull(engine.getAndClearLastExecutionError(),
                "smartBlacklistBlocksIoPackage: blocked java.io access must stay silent nil");
    }

    @Test
    void smartBlacklistBlocksThreadClass() throws ReflectiveOperationException {
        // Referencing java.lang.Thread by name trips DoNotUseThreads even as test
        // data, so the instance is obtained reflectively through its public constructor.
        Object worker = Class.forName("java.lang.Thread").getDeclaredConstructor().newInstance();
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe(PROBE, worker);
        engine.executeString(
                "assert(_probe.start == nil, 'Thread.start must be blocked')",
                "blacklist_thread");
        assertNull(engine.getAndClearLastExecutionError(),
                "smartBlacklistBlocksThreadClass: blocked Thread access must stay silent nil");
    }

    @Test
    void smartBlacklistBlocksReflectMethod() throws ReflectiveOperationException {
        Object target = String.class.getMethod("length");
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe(PROBE, target);
        engine.executeString(
                "assert(_probe.invoke == nil, 'reflective Method.invoke must be blocked')",
                "blacklist_reflect_method");
        assertNull(engine.getAndClearLastExecutionError(),
                "smartBlacklistBlocksReflectMethod: blocked reflection must stay silent nil");
    }

    @Test
    void smartBlacklistBlocksReflectField() throws ReflectiveOperationException {
        Object target = String.class.getDeclaredField("value");
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe(PROBE, target);
        engine.executeString(
                "assert(_probe.get == nil, 'reflective Field.get must be blocked')",
                "blacklist_reflect_field");
        assertNull(engine.getAndClearLastExecutionError(),
                "smartBlacklistBlocksReflectField: blocked reflection must stay silent nil");
    }

    @Test
    void smartBlacklistBlocksClassClass() {
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe(PROBE, String.class);
        engine.executeString(
                "assert(_probe.forName == nil, 'Class.forName must be blocked')",
                "blacklist_class");
        assertNull(engine.getAndClearLastExecutionError(),
                "smartBlacklistBlocksClassClass: blocked Class access must stay silent nil");
    }

    @Test
    void smartBlacklistBlocksClassLoader() {
        ILuaEngine engine = DynamicJavaProxyTestSupport.engineWithProbe(
                PROBE, ClassLoader.getSystemClassLoader());
        engine.executeString(
                "assert(_probe.loadClass == nil, 'ClassLoader.loadClass must be blocked')",
                "blacklist_classloader");
        assertNull(engine.getAndClearLastExecutionError(),
                "smartBlacklistBlocksClassLoader: blocked ClassLoader access must stay silent nil");
    }
}

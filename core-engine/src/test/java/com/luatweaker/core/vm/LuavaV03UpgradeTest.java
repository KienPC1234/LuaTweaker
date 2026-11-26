package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.luava.runtime.concurrency.LuaCoroutine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies luava 0.3.0 integration improvements: state close, stackless coroutines, diagnostics, and compileChunk. */
class LuavaV03UpgradeTest {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void shutdownClosesRawState() {
        LuavaEngine engine = new LuavaEngine(false);
        engine.shutdown();
        assertTrue(engine.rawState().isClosed(),
                "shutdown must close the underlying luava LuaState");
    }

    @Test
    void shutdownIsIdempotent() {
        LuavaEngine engine = new LuavaEngine(false);
        engine.shutdown();
        engine.shutdown();
        assertTrue(engine.rawState().isClosed(),
                "repeated shutdown calls must stay idempotent");
    }

    @Test
    void globalsRemainReadableAfterShutdown() {
        LuavaEngine engine = new LuavaEngine(false);
        engine.executeStringOrThrow("_keptGlobal = 'alive'", "shutdown_setup");
        engine.shutdown();
        assertEquals("alive", engine.getGlobalEnvironment().rawget("_keptGlobal").asString(),
                "globals snapshot must remain readable after shutdown");
    }

    @Test
    void deferredLuaClosureRunsStacklessWithoutVirtualThread() {
        LuavaEngine engine = new LuavaEngine(false);
        engine.executeStringOrThrow(
                "_co = task._run_deferred(function() coroutine.yield('paused') end, {})",
                "stackless_deferred_test");

        ILuaValue rawCo = engine.getGlobalEnvironment().rawget("_co");
        Object javaObj = rawCo.toJavaObject();
        boolean stacklessBeforeShutdown =
                javaObj instanceof LuaCoroutine coroutine && coroutine.isStacklessSuspended();
        engine.shutdown();
        assertTrue(stacklessBeforeShutdown,
                "pure-Lua deferred closure must suspend on luava 0.3.0 stackless fast path");
    }

    @Test
    void describeDiagnosticsReportsVersionAndJitStatus() {
        LuavaEngine engine = new LuavaEngine(false);
        engine.executeStringOrThrow("local x = 1 + 2", "diag_probe");

        String diagnostics = engine.describeDiagnostics();
        engine.shutdown();
        assertTrue(diagnostics != null && diagnostics.contains("luava 0.3.0") && diagnostics.contains("JIT="),
                "diagnostics summary must include engine version and JIT status: " + diagnostics);
    }

    private static String runSyntaxCheck(String chunkName, String source) {
        return LuavaSyntaxChecker.checkSyntax(chunkName, source);
    }

    @Test
    void syntaxCheckerAcceptsValidLua() {
        assertNull(runSyntaxCheck("valid.lua", "local a = 1 + 2\nreturn a"),
                "valid Lua 5.4 chunk must pass syntax check");
    }

    @Test
    void syntaxCheckerReportsInvalidLuaWithChunkName() {
        String error = runSyntaxCheck("broken.lua", "local x =\nfunction(");
        assertTrue(error != null && !error.isBlank() && error.contains("broken.lua"),
                "invalid Lua chunk must fail with chunk name: " + error);
    }
}

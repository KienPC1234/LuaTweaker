package com.luatweaker.core.vm;

import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.ConcurrentEngineSupport.EngineRun;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Two threads invoking Lua functions must serialize without losing increments. */
class ConcurrentCallFunctionTest {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void setupScriptCompilesAndRunsCleanly() {
        EngineRun run = ConcurrentEngineSupport.runSharedCounterScenario();
        assertNull(run.setupError(), "shared-counter setup script must compile and run cleanly");
    }

    @Test
    void incrementFunctionIsRegistered() {
        EngineRun run = ConcurrentEngineSupport.runSharedCounterScenario();
        assertTrue(run.incrementFn().isFunction(), "increment function must be registered");
    }

    @Test
    void bothThreadsFinishWithinTimeout() {
        EngineRun run = ConcurrentEngineSupport.runSharedCounterScenario();
        assertTrue(run.finished(), "both threads must finish within the timeout");
    }

    @Test
    void concurrentCallFunctionThrowsNothing() {
        EngineRun run = ConcurrentEngineSupport.runSharedCounterScenario();
        assertNull(run.failure(), "concurrent callFunction must not throw");
    }

    @Test
    void noIncrementIsLost() {
        EngineRun run = ConcurrentEngineSupport.runSharedCounterScenario();
        assertEquals((double) (2 * ConcurrentEngineSupport.ITERATIONS),
                run.engine().getGlobalEnvironment().rawget("_sharedCounter").asDouble(),
                "synchronized entry points must not lose increments");
    }
}

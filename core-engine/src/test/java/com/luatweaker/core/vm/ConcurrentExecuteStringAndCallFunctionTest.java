package com.luatweaker.core.vm;

import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.ConcurrentEngineSupport.EngineRun;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A script thread and a function-calling thread hammer the same VM: every
 * write and every increment must survive, and no error may surface.
 */
class ConcurrentExecuteStringAndCallFunctionTest {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void setupScriptCompilesAndRunsCleanly() {
        EngineRun run = ConcurrentEngineSupport.runInterleavedScenario();
        assertNull(run.setupError(), "concurrent setup script must compile and run cleanly");
    }

    @Test
    void incrementFunctionIsRegistered() {
        EngineRun run = ConcurrentEngineSupport.runInterleavedScenario();
        assertTrue(run.incrementFn().isFunction(), "increment function must be registered");
    }

    @Test
    void bothThreadsFinishWithinTimeout() {
        EngineRun run = ConcurrentEngineSupport.runInterleavedScenario();
        assertTrue(run.finished(), "both threads must finish within the timeout");
    }

    @Test
    void concurrentAccessThrowsNothing() {
        EngineRun run = ConcurrentEngineSupport.runInterleavedScenario();
        assertNull(run.failure(), "concurrent VM access must not throw");
    }

    @Test
    void allScriptWritesStayVisible() {
        EngineRun run = ConcurrentEngineSupport.runInterleavedScenario();
        assertEquals((double) ConcurrentEngineSupport.ITERATIONS,
                run.engine().getGlobalEnvironment().rawget("_concurrentFromJava").asDouble(),
                "all script writes must be visible");
    }

    @Test
    void allFunctionCallIncrementsAreApplied() {
        EngineRun run = ConcurrentEngineSupport.runInterleavedScenario();
        assertEquals((double) ConcurrentEngineSupport.ITERATIONS,
                run.engine().getGlobalEnvironment().rawget("_concurrentCounter").asDouble(),
                "all function-call increments must be applied");
    }

    @Test
    void noScriptErrorIsRecorded() {
        EngineRun run = ConcurrentEngineSupport.runInterleavedScenario();
        assertNull(run.engine().getAndClearLastExecutionError(), "no script error may be recorded");
    }
}

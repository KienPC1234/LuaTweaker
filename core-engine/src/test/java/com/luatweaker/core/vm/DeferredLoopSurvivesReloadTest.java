package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A deferred loop with Java re-entry must keep running after a reload builds a
 * fresh VM, and must never leak a VM control-flow exception through the SPI.
 */
class DeferredLoopSurvivesReloadTest {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void deferredLoopBodyRunsOnEveryReload() {
        for (int round = 1; round <= 3; round++) {
            ILuaEngine engine = DeferredReloadFixtures.runReloadRound();
            assertTrue(engine.getGlobalEnvironment().rawget("_hudTicks").asDouble() >= 1.0,
                    "round " + round + ": deferred loop body must actually run");
        }
    }

    @Test
    void javaReentryReportsNoErrorOnEveryReload() {
        for (int round = 1; round <= 3; round++) {
            ILuaEngine engine = DeferredReloadFixtures.runReloadRound();
            assertTrue(engine.getGlobalEnvironment().rawget("_hudError").isNil(),
                    "round " + round + ": Java re-entry must not fail");
        }
    }

    @Test
    void reloadLeaksNoControlFlowException() {
        for (int round = 1; round <= 3; round++) {
            ILuaEngine engine = DeferredReloadFixtures.runReloadRound();
            assertNull(engine.getAndClearLastExecutionError(),
                    "round " + round + ": reload must not leak a VM control-flow exception");
        }
    }
}

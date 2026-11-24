package com.luatweaker.core.vm;

import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.ConcurrentEngineSupport.GlobalsAccessRun;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the render/server deadlock: getGlobalEnvironment() is a pure
 * factory and must never block on executionGate.
 */
class GlobalsDuringHeldGateTest {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void globalsAreAvailableWhileGateIsHeld() {
        GlobalsAccessRun result = ConcurrentEngineSupport.runGlobalsDuringHeldGate();

        assertTrue(result.globalsAcquired(),
                "getGlobalEnvironment must stay a non-blocking factory while the VM gate is held");
    }

    @Test
    void gateHolderFinishesAfterRelease() {
        GlobalsAccessRun result = ConcurrentEngineSupport.runGlobalsDuringHeldGate();

        assertTrue(result.holderFinished(),
                "the gated Lua call must complete once its blocker is released");
    }
}

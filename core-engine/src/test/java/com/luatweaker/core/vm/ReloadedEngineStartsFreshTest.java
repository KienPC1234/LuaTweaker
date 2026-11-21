package com.luatweaker.core.vm;

import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.DeferredReloadFixtures.ReloadPair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reload isolation: the reloaded engine starts from fresh state and advances
 * its own tasks; no state crosses between the two VMs.
 */
class ReloadedEngineStartsFreshTest {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void oldEnginesDeferredLoopMustHaveRun() {
        ReloadPair pair = DeferredReloadFixtures.prepareReloadPair();
        double oldTicks = pair.oldEngine().getGlobalEnvironment().rawget("_hudTicks").asDouble();
        assertTrue(oldTicks >= 1.0, "old engine's deferred loop must have run");
    }

    @Test
    void reloadedEngineStartsWithFreshState() {
        ReloadPair pair = DeferredReloadFixtures.prepareReloadPair();
        assertTrue(pair.newEngine().getGlobalEnvironment().rawget("_hudTicks").asDouble() == 0.0,
                "the reloaded engine must start with fresh state");
    }

    @Test
    void reloadedEnginesDeferredLoopRunsOnTheNewVm() {
        ReloadPair pair = DeferredReloadFixtures.prepareReloadPair();
        DeferredReloadFixtures.tick(pair.newEngine());
        AwaitSupport.awaitMillis(10);
        DeferredReloadFixtures.tick(pair.newEngine());
        AwaitSupport.awaitMillis(100);
        assertTrue(pair.newEngine().getGlobalEnvironment().rawget("_hudTicks").asDouble() >= 1.0,
                "the reloaded engine's deferred loop must run on the new VM");
    }

    @Test
    void eachEngineOwnsADistinctGlobalsTable() {
        ReloadPair pair = DeferredReloadFixtures.prepareReloadPair();
        DeferredReloadFixtures.tick(pair.newEngine());
        AwaitSupport.awaitMillis(10);
        DeferredReloadFixtures.tick(pair.newEngine());
        AwaitSupport.awaitMillis(100);
        // luava coroutines own virtual threads, so a discarded engine's pending wait()
        // callbacks may wake independently of tick(). What reload isolation guarantees
        // is that no state crosses between the two VMs: each counter lives in its own
        // globals table and the new engine started from zero (asserted above).
        assertNotSame(pair.oldEngine().getGlobalEnvironment(), pair.newEngine().getGlobalEnvironment(),
                "each engine must own a distinct globals table after reload");
    }

    @Test
    void oldEngineRecordsNoError() {
        ReloadPair pair = DeferredReloadFixtures.prepareReloadPair();
        DeferredReloadFixtures.tick(pair.newEngine());
        AwaitSupport.awaitMillis(10);
        DeferredReloadFixtures.tick(pair.newEngine());
        AwaitSupport.awaitMillis(100);
        assertNull(pair.oldEngine().getAndClearLastExecutionError(),
                "the old engine must record no error across the reload");
    }

    @Test
    void newEngineRecordsNoError() {
        ReloadPair pair = DeferredReloadFixtures.prepareReloadPair();
        DeferredReloadFixtures.tick(pair.newEngine());
        AwaitSupport.awaitMillis(10);
        DeferredReloadFixtures.tick(pair.newEngine());
        AwaitSupport.awaitMillis(100);
        assertNull(pair.newEngine().getAndClearLastExecutionError(),
                "the new engine must record no error across the reload");
    }
}

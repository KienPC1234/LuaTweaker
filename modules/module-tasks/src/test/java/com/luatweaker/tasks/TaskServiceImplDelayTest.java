package com.luatweaker.tasks;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Delay scheduling driven by an injected monotonic clock (no wall-clock sleeps).
 */
class TaskServiceImplDelayTest {

    private static final double DELAY_SECONDS = 0.25;
    private static final long START_NANOS = 5_000_000_000L;
    private static final long PAST_DELAY_NANOS = 300_000_000L;

    private AtomicLong now;
    private ILuaEngine engine;
    private TaskServiceImpl service;

    @BeforeEach
    void setUp() {
        now = new AtomicLong(START_NANOS);
        engine = LuaEngineFactory.create(true);
        service = new TaskServiceImpl(now::get);
        engine.executeStringOrThrow("_delayedRan = function() _delayedFlag = true end", "java_delay_fn");
    }

    @Test
    void delayQueuesOneCallback() {
        service.delay(engine, DELAY_SECONDS, callback(), null);

        Assertions.assertEquals(1, service.getQueuedTaskCount(),
                "delay must queue the callback for a later tick");
    }

    @Test
    void delayDoesNotRunBeforeItsDeadline() {
        service.delay(engine, DELAY_SECONDS, callback(), null);

        service.tick(engine);

        Assertions.assertTrue(isUnset("_delayedFlag"),
                "a delayed callback must not run before its deadline");
    }

    @Test
    void delayRunsWhenTheDeadlinePasses() {
        service.delay(engine, DELAY_SECONDS, callback(), null);
        now.addAndGet(PAST_DELAY_NANOS);

        service.tick(engine);

        Assertions.assertTrue(globals().rawget("_delayedFlag").asBoolean(),
                "the delayed callback must run once the monotonic clock passes its deadline");
    }

    @Test
    void delayDropsTheQueuedCountAfterRunning() {
        service.delay(engine, DELAY_SECONDS, callback(), null);
        now.addAndGet(PAST_DELAY_NANOS);

        service.tick(engine);

        Assertions.assertEquals(0, service.getQueuedTaskCount(),
                "a completed delayed callback must leave the queue empty");
    }

    private ILuaValue callback() {
        return globals().rawget("_delayedRan");
    }

    private ILuaTable globals() {
        return engine.getGlobalEnvironment();
    }

    private boolean isUnset(String name) {
        ILuaValue value = globals().rawget(name);
        return value == null || value.isNil();
    }
}

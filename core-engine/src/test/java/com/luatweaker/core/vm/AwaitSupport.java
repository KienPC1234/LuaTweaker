package com.luatweaker.core.vm;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;

/** Timing helpers for tests that wait on luava coroutine virtual threads. */
final class AwaitSupport {

    private AwaitSupport() {
    }

    static void awaitMillis(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException interrupted) {
            fail("interrupted while waiting for background coroutines: " + interrupted.getMessage());
        }
    }

    static void awaitCondition(BooleanSupplier condition, String what) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                fail("timed out waiting for " + what);
            }
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
        }
    }
}

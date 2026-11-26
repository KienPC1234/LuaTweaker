package com.luatweaker.core.vm;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Resource caps: value validation, state description, and proof that an unset
 * cap leaves the runtime defaults untouched.
 */
class LuaEngineLimitsTest {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void rejectsNegativeMemoryBytes() {
        IllegalArgumentException error;
        try {
            new LuaEngineLimits(-1L, 0, 0);
            throw new AssertionError("expected IllegalArgumentException but nothing was thrown");
        } catch (IllegalArgumentException expected) {
            error = expected;
        }
        assertTrue(error != null && error.getMessage().contains("maxMemoryBytes"),
                "rejectsNegativeMemoryBytes: negative memory must be rejected naming maxMemoryBytes");
    }

    @Test
    void rejectsNegativeCallDepth() {
        IllegalArgumentException error;
        try {
            new LuaEngineLimits(0L, -1, 0);
            throw new AssertionError("expected IllegalArgumentException but nothing was thrown");
        } catch (IllegalArgumentException expected) {
            error = expected;
        }
        assertTrue(error != null && error.getMessage().contains("maxCallDepth"),
                "rejectsNegativeCallDepth: negative depth must be rejected naming maxCallDepth");
    }

    @Test
    void rejectsNegativeCoroutineCap() {
        IllegalArgumentException error;
        try {
            new LuaEngineLimits(0L, 0, -1);
            throw new AssertionError("expected IllegalArgumentException but nothing was thrown");
        } catch (IllegalArgumentException expected) {
            error = expected;
        }
        assertTrue(error != null && error.getMessage().contains("maxCoroutines"),
                "rejectsNegativeCoroutineCap: negative cap must be rejected naming maxCoroutines");
    }

    @Test
    void describeReportsUnlimited() {
        assertEquals("unlimited", LuaEngineLimits.UNLIMITED.describe(),
                "describeReportsUnlimited: unlimited must describe itself");
    }

    @Test
    void describeReportsPartialLimits() {
        LuaEngineLimits partial = new LuaEngineLimits(0L, 250, 0);
        assertEquals("maxMemoryBytes=0, maxCallDepth=250, maxCoroutines=0", partial.describe(),
                "describeReportsPartialLimits: describe must list every cap");
    }

    @Test
    void unlimitedLeavesNoLimitsSet() {
        assertFalse(LuaEngineLimits.UNLIMITED.isAnyLimitSet(),
                "unlimitedLeavesNoLimitsSet: unlimited must report no limits set");
    }

    @Test
    void unlimitedLeavesMaxMemoryBytesUntouched() {
        LuavaEngine engine = new LuavaEngine(false);
        assertEquals(0L, engine.rawState().getMaxMemoryBytes(),
                "unlimitedLeavesMaxMemoryBytesUntouched: default memory cap must be zero");
    }

    @Test
    void unlimitedLeavesMaxCallDepthUntouched() {
        LuavaEngine engine = new LuavaEngine(false);
        assertEquals(0, engine.rawState().getMaxCallDepth(),
                "unlimitedLeavesMaxCallDepthUntouched: default call depth cap must be zero");
    }

    @Test
    void unlimitedLeavesMaxCoroutinesUntouched() {
        LuavaEngine engine = new LuavaEngine(false);
        assertEquals(0, engine.rawState().getMaxCoroutines(),
                "unlimitedLeavesMaxCoroutinesUntouched: default coroutine cap must be zero");
    }

    @Nested
    class GuardedJitLimits {

        private static final String EXPECTED_ILLEGAL_ARGUMENT =
                "expected IllegalArgumentException for negative cap";

        @Test
        void rejectsNegativeInstructionLimit() {
            IllegalArgumentException error;
            try {
                new LuaEngineLimits(0L, 0, 0, -1L, 0L, 0L, false);
                throw new AssertionError(EXPECTED_ILLEGAL_ARGUMENT);
            } catch (IllegalArgumentException expected) {
                error = expected;
            }
            assertTrue(error != null && error.getMessage().contains("instructionLimit"),
                    "error message must name instructionLimit: " + error.getMessage());
        }

        @Test
        void rejectsNegativeTimeoutMillis() {
            IllegalArgumentException error;
            try {
                new LuaEngineLimits(0L, 0, 0, 0L, -5L, 0L, false);
                throw new AssertionError(EXPECTED_ILLEGAL_ARGUMENT);
            } catch (IllegalArgumentException expected) {
                error = expected;
            }
            assertTrue(error != null && error.getMessage().contains("timeoutMillis"),
                    "error message must name timeoutMillis: " + error.getMessage());
        }

        @Test
        void rejectsNegativeMaxAllocationBytes() {
            IllegalArgumentException error;
            try {
                new LuaEngineLimits(0L, 0, 0, 0L, 0L, -10L, false);
                throw new AssertionError(EXPECTED_ILLEGAL_ARGUMENT);
            } catch (IllegalArgumentException expected) {
                error = expected;
            }
            assertTrue(error != null && error.getMessage().contains("maxAllocationBytes"),
                    "error message must name maxAllocationBytes: " + error.getMessage());
        }

        @Test
        void cancellableAloneMarksLimitsSet() {
            LuaEngineLimits limits = new LuaEngineLimits(0L, 0, 0, 0L, 0L, 0L, true);
            assertTrue(limits.isAnyLimitSet(), "cancellable flag must mark limits as active");
        }

        @Test
        void describeIncludesGuardedJitFieldsWhenConfigured() {
            LuaEngineLimits limits = new LuaEngineLimits(0L, 0, 0, 10_000L, 500L, 4096L, true);
            String described = limits.describe();
            assertTrue(described.contains("instructionLimit=10000")
                            && described.contains("timeoutMillis=500")
                            && described.contains("maxAllocationBytes=4096")
                            && described.contains("cancellable=true"),
                    "describe must include configured guarded-JIT limits: " + described);
        }
    }
}

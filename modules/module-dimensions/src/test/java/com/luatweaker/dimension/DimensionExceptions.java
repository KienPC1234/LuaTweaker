package com.luatweaker.dimension;

/**
 * Fixture-style rejection helpers: return the message of the expected exception type
 * so a single assertion can verify it. A different exception propagates, and a call
 * that does not throw fails loudly instead of being swallowed.
 */
final class DimensionExceptions {

    /** Action under test; it throws no checked exceptions, so tests stay terse. */
    @FunctionalInterface
    interface Action {
        void run();
    }

    private DimensionExceptions() {
    }

    static String illegalArgumentMessage(Action action) {
        try {
            action.run();
        } catch (IllegalArgumentException error) {
            return error.getMessage();
        }
        throw new AssertionError("expected IllegalArgumentException but the action succeeded");
    }

    static String illegalStateMessage(Action action) {
        try {
            action.run();
        } catch (IllegalStateException error) {
            return error.getMessage();
        }
        throw new AssertionError("expected IllegalStateException but the action succeeded");
    }
}

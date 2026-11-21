package com.luatweaker.content;

import com.luatweaker.api.vm.ILuaEngine;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Shared helper for tests that verify a Lua script aborts loudly.
 *
 * <p>Kept out of the individual test classes so every test method asserts
 * exactly one thing: either that the script throws, or that its message
 * explains the failure.</p>
 */
final class ScriptFailures {

    private ScriptFailures() {
    }

    /**
     * Runs a script that must abort and returns the engine's error message.
     *
     * @param engine engine bound to the content services under test
     * @param script Lua source that is expected to fail
     * @param chunk  chunk name reported in the engine error
     * @return the message of the thrown {@link IllegalStateException}
     */
    static String message(ILuaEngine engine, String script, String chunk) {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> engine.executeStringOrThrow(script, chunk),
                "the script must fail loudly instead of being silently dropped");
        return error.getMessage();
    }
}

package com.luatweaker.core.engine;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.lib.CoreLibraries;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class LuaEngine {
    private final LuaState state;

    public LuaEngine() {
        this.state = new LuaState();
        try {
            // Load standard libraries: base, table, string, coroutine, math, utf8
            CoreLibraries.standardGlobals(state);
        } catch (LuaError e) {
            AsyncFileLogger.get().error("ENGINE", "Failed to initialize standard globals: " + e.getMessage(), null);
        }
    }

    public LuaState getState() {
        return state;
    }

    /**
     * Check Lua source code for syntax errors WITHOUT executing it.
     *
     * @param chunkName  Display name used in error messages (e.g. file name).
     * @param source     Raw Lua source text.
     * @return           {@code null} if the source is valid; otherwise a human-readable
     *                   error string describing the syntax problem.
     */
    public static String checkSyntax(String chunkName, String source) {
        try {
            LuaState tempState = new LuaState();
            CoreLibraries.standardGlobals(tempState);
            InputStream stream = new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
            LoadState.load(tempState, stream, "@" + chunkName, tempState.globals());
            return null; // no error
        } catch (LuaError e) {
            return e.getMessage();
        } catch (Exception e) {
            return "Internal error: " + e.getMessage();
        }
    }
}


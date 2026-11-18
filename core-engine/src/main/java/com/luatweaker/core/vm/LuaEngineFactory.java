package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Single point of Lua engine construction. All engines are luava-based.
 */
public final class LuaEngineFactory {

    private LuaEngineFactory() {
    }

    /**
     * Creates a fresh luava engine.
     *
     * @param debugMode whether debug logging is enabled
     * @return the new engine instance
     */
    public static @NotNull ILuaEngine create(boolean debugMode) {
        return new LuavaEngine(debugMode);
    }

    /**
     * Checks Lua source syntax with the luava parser.
     *
     * @param chunkName display name used in error messages
     * @param source    raw Lua source text
     * @return {@code null} when the source is valid, otherwise a human-readable error
     */
    public static @Nullable String checkSyntax(String chunkName, String source) {
        return LuavaSyntaxChecker.checkSyntax(chunkName, source);
    }
}

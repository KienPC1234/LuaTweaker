package com.luatweaker.core.vm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.frontend.parser.ParseException;
import org.luava.runtime.LuaException;
import org.luava.runtime.LuaState;

/**
 * Syntax-only Lua checker backed by the luava front-end/compiler.
 *
 * <p>A fresh {@link LuaState} is created for every call; no state is cached statically and
 * {@code sandbox()} is deliberately not applied because scripts rely on {@code require}.
 * Compilation stops before execution, so no script code is run.
 */
public final class LuavaSyntaxChecker {
    private static final String FILE_CHUNK_PREFIX = "@";
    private static final String LITERAL_CHUNK_PREFIX = "=";

    private LuavaSyntaxChecker() {
    }

    /**
     * Converts a caller-supplied display name into a luava chunk name. The {@code @} marker
     * makes luava treat the name as a file, so diagnostics read {@code file.lua:line: message}.
     */
    private static @NotNull String toLuaChunkName(@NotNull String chunkName) {
        if (chunkName.startsWith(FILE_CHUNK_PREFIX) || chunkName.startsWith(LITERAL_CHUNK_PREFIX)) {
            return chunkName;
        }
        return FILE_CHUNK_PREFIX + chunkName;
    }

    /**
     * Checks Lua source code for syntax errors without executing it.
     *
     * @param chunkName display name used in error messages (e.g. file name)
     * @param source    raw Lua source text
     * @return {@code null} if the source is valid; otherwise a human-readable error message
     */
    public static @Nullable String checkSyntax(@NotNull String chunkName, @NotNull String source) {
        String luaChunkName = toLuaChunkName(chunkName);
        try (LuaState state = new LuaState()) {
            state.compile(source, luaChunkName, state.getGlobals());
            return null;
        } catch (LuaException e) {
            String message = e.getMessage();
            return message != null ? message : e.toString();
        } catch (ParseException e) {
            return e.format(luaChunkName);
        }
    }
}

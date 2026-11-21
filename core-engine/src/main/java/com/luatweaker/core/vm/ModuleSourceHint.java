package com.luatweaker.core.vm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaFunction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the owning LuaMod id from a Lua function's source chunk name.
 *
 * <p>Used by the deferred-task runner so a coroutine keeps logging under the mod that
 * created it. Split out of {@link LuavaEngine} to keep the pattern matching (and the
 * regex imports) away from the engine's VM lifecycle code.</p>
 */
final class ModuleSourceHint {
    private ModuleSourceHint() {}

    /** {@code <modId>/<file>.lua} chunk names produced by {@code executeScript}. */
    private static final Pattern MOD_ID_PATTERN =
            Pattern.compile("([A-Za-z0-9_]+)[/\\\\][^/\\\\]+\\.lua");
    /** {@code luamods/<modId>/...} chunk names produced by the module loader. */
    private static final Pattern LUAMODS_SOURCE_PATTERN = Pattern.compile("luamods[/\\\\](.+)");

    /**
     * @param function Lua function whose chunk name is inspected
     * @return the mod id, or {@code null} when the source carries no LuaMod path
     */
    static @Nullable String from(@NotNull LuaFunction function) {
        String source = function.getSource();
        if (source == null) {
            return null;
        }
        String luamods = firstMatch(LUAMODS_SOURCE_PATTERN, source, 1);
        return luamods != null ? luamods : firstMatch(MOD_ID_PATTERN, source, 0);
    }

    /** @return group {@code group} of the first match, or {@code null} when there is none */
    private static @Nullable String firstMatch(@NotNull Pattern pattern, @NotNull String source, int group) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(group) : null;
    }
}

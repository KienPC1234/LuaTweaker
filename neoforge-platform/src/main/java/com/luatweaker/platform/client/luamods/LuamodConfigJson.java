package com.luatweaker.platform.client.luamods;

import org.jetbrains.annotations.NotNull;

/** Shared JSON text helpers for the config editor. */
final class LuamodConfigJson {

    private LuamodConfigJson() {
    }

    /** Normalises CRLF/CR so the raw editor and the pretty-printed form share one representation. */
    static String normalizeLineEndings(@NotNull String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
}

package com.luatweaker.core.mod;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Error message construction and path validation for one mod's file sandbox.
 *
 * <p>Shared by the read and write collaborators so every failure carries the
 * same {@code [modId] op('path'): reason} shape.</p>
 */
final class ModFileErrors {
    private final String modId;

    ModFileErrors(@NotNull String modId) {
        this.modId = modId;
    }

    /** Validates a Lua-supplied path, wrapping rejections with the operation label. */
    @NotNull String relative(@NotNull String path, boolean forWrite, @NotNull String op) {
        try {
            return LuaModPathSecurity.validateRelativePath(path, forWrite);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label(op, path) + ": " + e.getMessage(), e);
        }
    }

    @NotNull String label(@NotNull String op, @NotNull String path) {
        return "[" + modId + "] " + op + "('" + path + "')";
    }

    @NotNull IllegalArgumentException notFound(@NotNull String op, @NotNull String rel) {
        return new IllegalArgumentException(label(op, rel) + ": not found");
    }

    /** Logs an I/O failure and converts it to an unchecked Lua-facing error. */
    @NotNull UncheckedIOException fail(@NotNull String op, @NotNull String rel, @NotNull IOException cause) {
        String message = label(op, rel) + ": " + cause.getMessage();
        LuaTweakerLog.get().error(LogStage.SYSTEM, message);
        return new UncheckedIOException(message, cause);
    }

    /** Guards directory listings: fail loudly instead of silently truncating. */
    void checkEntryCap(int size, @NotNull String op, @NotNull String rel) {
        if (size > ModFileSystem.MAX_LIST_ENTRIES) {
            throw new IllegalArgumentException(label(op, rel) + ": directory has more than "
                    + ModFileSystem.MAX_LIST_ENTRIES + " entries");
        }
    }
}

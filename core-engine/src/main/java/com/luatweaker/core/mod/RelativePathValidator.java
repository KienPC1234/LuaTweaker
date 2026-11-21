package com.luatweaker.core.mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shape validation for Lua-supplied relative paths (mod file access).
 *
 * <p>Rejects absolute paths, {@code ..} segments, empty segments, drive letters /
 * alternate data streams ({@code :}) and control characters. Write targets are
 * additionally checked against Windows quirks (trailing dot/space, reserved
 * device names) so a script gets a clear error instead of an OS failure.</p>
 */
final class RelativePathValidator {
    private RelativePathValidator() {}

    private static final int MAX_RELATIVE_PATH_LENGTH = 512;
    private static final int MAX_SEGMENT_LENGTH = 255;

    /**
     * @param path     raw Lua path; backslashes are normalised to {@code /}
     * @param forWrite true when the path is a write target
     * @return the normalised relative path ({@code ""} for the root)
     */
    static @NotNull String validate(@Nullable String path, boolean forWrite) {
        if (path == null) {
            throw new IllegalArgumentException("path is required");
        }
        String normalised = stripTrailingSlashes(path.replace('\\', '/'));
        if (normalised.isEmpty() || ".".equals(normalised)) {
            return "";
        }
        requirePathLength(normalised, path);
        if (normalised.startsWith("/")) {
            throw new IllegalArgumentException("relative path must not be absolute: " + path);
        }
        for (String segment : normalised.split("/", -1)) {
            checkSegment(segment, forWrite, path);
        }
        return normalised;
    }

    private static void requirePathLength(@NotNull String normalised, @NotNull String path) {
        if (normalised.length() > MAX_RELATIVE_PATH_LENGTH) {
            throw new IllegalArgumentException("relative path is longer than "
                    + MAX_RELATIVE_PATH_LENGTH + " characters: " + path);
        }
    }

    private static @NotNull String stripTrailingSlashes(@NotNull String normalised) {
        String stripped = normalised;
        while (stripped.length() > 1 && stripped.endsWith("/")) {
            stripped = stripped.substring(0, stripped.length() - 1);
        }
        return stripped;
    }

    private static void checkSegment(@NotNull String segment, boolean forWrite, @NotNull String path) {
        if (segment.isEmpty()) {
            throw new IllegalArgumentException("relative path contains an empty segment: " + path);
        }
        if (".".equals(segment) || "..".equals(segment)) {
            throw new IllegalArgumentException("relative path contains an unsafe '" + segment
                    + "' segment: " + path);
        }
        if (segment.length() > MAX_SEGMENT_LENGTH) {
            throw new IllegalArgumentException("path segment is longer than "
                    + MAX_SEGMENT_LENGTH + " characters: " + path);
        }
        requirePrintableChars(segment, path);
        if (forWrite) {
            checkWritableName(segment, path);
        }
    }

    private static void requirePrintableChars(@NotNull String segment, @NotNull String path) {
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c < 0x20 || c == 0x7F || c == ':') {
                throw new IllegalArgumentException(
                        "relative path contains a control or reserved character: " + path);
            }
        }
    }

    private static void checkWritableName(@NotNull String segment, @NotNull String path) {
        char last = segment.charAt(segment.length() - 1);
        if (last == '.' || last == ' ') {
            throw new IllegalArgumentException("write target must not end with '.' or ' ': " + path);
        }
        if (LuaModPathSecurity.isReservedWindowsName(segment)) {
            throw new IllegalArgumentException("write target uses a reserved filename: " + path);
        }
    }
}

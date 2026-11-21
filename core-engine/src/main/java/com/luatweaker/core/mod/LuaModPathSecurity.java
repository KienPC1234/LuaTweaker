package com.luatweaker.core.mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared validation for LuaMod-controlled filesystem names.
 */
public final class LuaModPathSecurity {
    private static final Pattern MOD_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");
    private static final Set<String> RESERVED_WINDOWS_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private LuaModPathSecurity() {
    }

    public static @NotNull String validateModId(@NotNull String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("LuaMod manifest requires non-empty 'id'");
        }
        String trimmed = id.trim();
        if (!MOD_ID.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("LuaMod manifest 'id' may only contain letters, numbers, '_' and '-': " + id);
        }
        if (RESERVED_WINDOWS_NAMES.contains(trimmed.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("LuaMod manifest 'id' uses a reserved filename: " + id);
        }
        return trimmed;
    }

    public static @NotNull String validateEntryPoint(@NotNull String entryPoint) {
        if (entryPoint == null || entryPoint.isBlank()) {
            throw new IllegalArgumentException("LuaMod manifest requires non-empty 'main'");
        }
        String trimmed = entryPoint.trim();
        if (trimmed.indexOf('\\') >= 0 || trimmed.indexOf(':') >= 0 || trimmed.startsWith("/")) {
            throw new IllegalArgumentException("LuaMod manifest 'main' must be a relative Lua path: " + entryPoint);
        }
        for (String part : trimmed.split("/", -1)) {
            checkEntrySegment(part, entryPoint);
        }
        return trimmed;
    }

    private static void checkEntrySegment(@NotNull String part, @NotNull String entryPoint) {
        if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
            throw new IllegalArgumentException(
                    "LuaMod manifest 'main' contains an unsafe path segment: " + entryPoint);
        }
    }

    /**
     * Validates a Lua-supplied relative path for mod file access.
     *
     * <p>Rejects absolute paths, {@code ..} segments, empty segments, drive letters /
     * alternate data streams ({@code :}) and control characters. Write targets are
     * additionally checked against Windows quirks (trailing dot/space, reserved
     * device names) so a script gets a clear error instead of an OS failure.</p>
     *
     * @param path     raw Lua path; backslashes are normalised to {@code /}
     * @param forWrite true when the path is a write target
     * @return the normalised relative path ({@code ""} for the root)
     */
    public static @NotNull String validateRelativePath(@Nullable String path, boolean forWrite) {
        return RelativePathValidator.validate(path, forWrite);
    }

    /** True for Windows device names ({@code CON}, {@code NUL}, {@code COM1}, ...), with or without an extension. */
    public static boolean isReservedWindowsName(@NotNull String name) {
        int dot = name.indexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return RESERVED_WINDOWS_NAMES.contains(base.toUpperCase(Locale.ROOT));
    }

    public static @NotNull Path resolveContained(@NotNull Path root, @NotNull String relativePath) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path candidate = normalizedRoot.resolve(relativePath).normalize();
        if (!candidate.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("LuaMod path escapes its root: " + relativePath);
        }
        checkNoSymlinkEscape(normalizedRoot, candidate, relativePath);
        return candidate;
    }

    /**
     * Lexical normalization does not stop a mod from placing a symlink inside
     * its directory that points outside. Check the real path when the target
     * exists, and otherwise check the nearest existing parent.
     */
    private static void checkNoSymlinkEscape(@NotNull Path normalizedRoot, @NotNull Path candidate,
            @NotNull String relativePath) {
        try {
            Path realRoot = normalizedRoot.toRealPath();
            Path existing = candidate;
            while (existing != null && !Files.exists(existing)) {
                existing = existing.getParent();
            }
            if (existing != null && !existing.toRealPath().startsWith(realRoot)) {
                throw new IllegalArgumentException(
                        "LuaMod path follows a symlink outside its root: " + relativePath);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to validate LuaMod path: " + relativePath, e);
        }
    }
}

package com.luatweaker.core.mod;

import org.jetbrains.annotations.NotNull;

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
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("LuaMod manifest 'main' contains an unsafe path segment: " + entryPoint);
            }
        }
        return trimmed;
    }

    public static @NotNull Path resolveContained(@NotNull Path root, @NotNull String relativePath) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path candidate = normalizedRoot.resolve(relativePath).normalize();
        if (!candidate.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("LuaMod path escapes its root: " + relativePath);
        }
        // Lexical normalization does not stop a mod from placing a symlink inside
        // its directory that points outside. Check the real path when the target
        // exists, and otherwise check the nearest existing parent.
        try {
            Path realRoot = normalizedRoot.toRealPath();
            Path existing = candidate;
            while (existing != null && !Files.exists(existing)) {
                existing = existing.getParent();
            }
            if (existing != null && !existing.toRealPath().startsWith(realRoot)) {
                throw new IllegalArgumentException("LuaMod path follows a symlink outside its root: " + relativePath);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to validate LuaMod path: " + relativePath, e);
        }
        return candidate;
    }
}

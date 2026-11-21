package com.luatweaker.command;

import com.luatweaker.api.command.CoreCommandNames;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Path rules for Lua-registered commands: slash-separated {@code [a-z0-9_:]+}
 * segments (max depth 4) plus reservation checks against the core {@code /lt}
 * tree and vanilla commands. Also hosts the definition field readers and the
 * stable primary-plus-aliases path listing used for conflict checks.
 */
final class CommandPathValidator {

    static final int NAME_MAX_LENGTH = 32;
    static final int PATH_MAX_DEPTH = 4;
    static final int PATH_MAX_LENGTH = 64;

    private CommandPathValidator() {}

    /** Validates a slash-separated command path: {@code [a-z0-9_:]+} segments, max depth 4. */
    static boolean isValidPath(@NotNull String path) {
        if (path.isEmpty() || path.length() > PATH_MAX_LENGTH) {
            return false;
        }
        if (hasSplitArtifacts(path)) {
            return false;
        }
        String[] segments = path.split("/");
        if (segments.length == 0 || segments.length > PATH_MAX_DEPTH) {
            return false;
        }
        for (String segment : segments) {
            if (!isValidSegment(segment)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Java's String.split drops trailing empty segments, so leading, trailing
     * and doubled slashes are rejected explicitly.
     */
    private static boolean hasSplitArtifacts(@NotNull String path) {
        return path.startsWith("/") || path.endsWith("/") || path.contains("//");
    }

    private static boolean isValidSegment(@NotNull String segment) {
        if (segment.isEmpty() || segment.length() > NAME_MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            if (!isCommandChar(segment.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCommandChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == ':';
    }

    /** Reserved: core /lt roots as first segment, or the whole path matching a reserved name. */
    static boolean isReservedPath(@NotNull String path) {
        String firstSegment = path.split("/")[0];
        if ("lt".equals(firstSegment) || "luatweaker".equals(firstSegment)) {
            return true;
        }
        return CoreCommandNames.RESERVED.contains(path);
    }

    /** Reads a definition field accepting PascalCase and camelCase aliases. */
    static @Nullable Object readField(@NotNull ILuaTable definition, @NotNull String pascalKey,
                                      @NotNull String camelKey) {
        ILuaValue value = definition.rawget(pascalKey);
        if (value == null || value.isNil()) {
            value = definition.rawget(camelKey);
        }
        if (value == null || value.isNil()) {
            return null;
        }
        return value.toJavaObject();
    }

    /** All paths (primary + aliases) a definition claims, in a stable order. */
    static @NotNull List<String> allPaths(@NotNull CommandDefinition def) {
        List<String> paths = new ArrayList<>();
        paths.add(def.name());
        paths.addAll(def.aliases());
        return paths;
    }
}

package com.luatweaker.core.remap;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Name and type heuristics for matching obfuscated (production) method names
 * against the deobfuscated names Lua scripts use.
 */
final class ObfuscationHeuristics {
    private ObfuscationHeuristics() {}

    /** Obfuscated member-name shapes (Mojmap SRG + Yarn intermediary). */
    static final Pattern OBFUSCATED_ANY =
            Pattern.compile("^(m_\\d+_$|method_\\d+|f_\\d+_$|field_\\d+)$");

    /** Length of the {@code get}/{@code set}/{@code is} accessor prefixes. */
    private static final int ACCESSOR_PREFIX_LENGTH = 3;

    /** True when the actual name plausibly denotes the requested one. */
    static boolean matchesHeuristic(@NotNull String actualName, @NotNull String requestedName) {
        if (OBFUSCATED_ANY.matcher(actualName).matches()) {
            return false;
        }
        if (actualName.equalsIgnoreCase(requestedName)) {
            return true;
        }
        String lowerRequested = requestedName.toLowerCase(Locale.ROOT);
        String lowerActual = actualName.toLowerCase(Locale.ROOT);
        if (lowerActual.endsWith(lowerRequested)) {
            return true;
        }
        String stripped = requestedName;
        if (stripped.startsWith("get") || stripped.startsWith("set") || stripped.startsWith("is")) {
            // A bare "is" has nothing to strip: no match instead of a substring crash.
            if (stripped.length() <= ACCESSOR_PREFIX_LENGTH) {
                return false;
            }
            stripped = stripped.substring(ACCESSOR_PREFIX_LENGTH);
        }
        return lowerActual.contains(stripped.toLowerCase(Locale.ROOT));
    }

    /** True when a primitive and a wrapper (or two wrappers) box-compatibly overlap. */
    static boolean isBoxingCompatible(@NotNull Class<?> declared, @NotNull Class<?> provided) {
        if (declared.isPrimitive() && !provided.isPrimitive()) {
            return getWrapperType(declared).equals(provided);
        }
        if (!declared.isPrimitive() && provided.isPrimitive()) {
            return declared.equals(getWrapperType(provided));
        }
        return false;
    }

    private static @NotNull Class<?> getWrapperType(@NotNull Class<?> primitive) {
        if (primitive == int.class) {
            return Integer.class;
        }
        if (primitive == long.class) {
            return Long.class;
        }
        if (primitive == double.class) {
            return Double.class;
        }
        if (primitive == float.class) {
            return Float.class;
        }
        if (primitive == boolean.class) {
            return Boolean.class;
        }
        if (primitive == byte.class) {
            return Byte.class;
        }
        if (primitive == short.class) {
            return Short.class;
        }
        if (primitive == char.class) {
            return Character.class;
        }
        return primitive;
    }
}

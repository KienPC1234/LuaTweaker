package com.luatweaker.core.mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tolerant dotted-version comparison for LuaMod dependency gates.
 *
 * <p>Versions are compared segment by segment ({@code 1.21.1} vs {@code 1.21}).
 * Each segment contributes its leading numeric run; a missing or non-numeric
 * segment counts as {@code 0}, so qualifiers such as {@code -beta} or
 * {@code +build} never crash a comparison, they simply do not raise the order.
 * Bounds are inclusive: {@code satisfies("1.5.0", "1.0.0", "2.0.0")} is true.
 */
public final class ModVersion {
    private ModVersion() {}

    /**
     * Compares two version strings numerically.
     *
     * @return negative when {@code a < b}, zero when equal, positive when {@code a > b}.
     *         A {@code null} or blank version is smaller than any non-blank one.
     */
    public static int compare(@Nullable String a, @Nullable String b) {
        int[] left = parse(a);
        int[] right = parse(b);
        int len = Math.max(left.length, right.length);
        for (int i = 0; i < len; i++) {
            int l = i < left.length ? left[i] : 0;
            int r = i < right.length ? right[i] : 0;
            if (l != r) { return Integer.compare(l, r); }
        }
        return 0;
    }

    /**
     * Checks an installed version against an inclusive {@code [min, max]} range.
     * A {@code null} bound leaves that side open. A {@code null} installed
     * version never satisfies (callers report it as missing instead).
     */
    public static boolean satisfies(@Nullable String installed,
                                    @Nullable String minVersion,
                                    @Nullable String maxVersion) {
        if (installed == null || installed.isBlank()) { return false; }
        if (minVersion != null && !minVersion.isBlank() && compare(installed, minVersion) < 0) { return false; }
        return maxVersion == null || maxVersion.isBlank() || compare(installed, maxVersion) <= 0;
    }

    /** Parses a version into numeric segments; {@code null}/blank yields {@code [0]}. */
    static int[] parse(@Nullable String version) {
        if (version == null || version.isBlank()) {
            return new int[]{0};
        }
        String[] parts = version.trim().split("[.\\-_+]+");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = leadingNumber(parts[i]);
        }
        return numbers;
    }

    private static int leadingNumber(@NotNull String segment) {
        int end = 0;
        while (end < segment.length() && Character.isDigit(segment.charAt(end))) { end++; }
        if (end == 0) { return 0; }
        // At most 9 digits by construction, so parseInt cannot fail.
        return Integer.parseInt(segment.substring(0, Math.min(end, 9)));
    }
}

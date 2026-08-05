package com.luatweaker.update;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Simple version string comparator for update feeds.
 *
 * <p>Versions are split on {@code . _ - +} and compared segment by segment.
 * Segments that are whole numbers compare numerically (so {@code 1.10} beats
 * {@code 1.9}), missing trailing segments count as {@code 0}, and non-numeric
 * segments compare lexicographically with numeric segments ranking lower.</p>
 */
public final class UpdateVersion {
    private UpdateVersion() {}

    /**
     * Compares two version strings.
     *
     * @return negative when a &lt; b, zero when equal, positive when a &gt; b.
     */
    public static int compare(@NotNull String a, @NotNull String b) {
        List<String> pa = split(a);
        List<String> pb = split(b);
        int n = Math.max(pa.size(), pb.size());
        for (int i = 0; i < n; i++) {
            String sa = i < pa.size() ? pa.get(i) : "0";
            String sb = i < pb.size() ? pb.get(i) : "0";
            int c = compareSegment(sa, sb);
            if (c != 0) return c;
        }
        return 0;
    }

    private static List<String> split(String version) {
        return new ArrayList<>(Arrays.asList(
                version.trim().toLowerCase(Locale.ROOT).split("[._\\-+]")));
    }

    private static int compareSegment(String a, String b) {
        Integer na = parseNum(a);
        Integer nb = parseNum(b);
        if (na != null && nb != null) return Integer.compare(na, nb);
        if (na != null) return -1;
        if (nb != null) return 1;
        return a.compareTo(b);
    }

    private static Integer parseNum(String segment) {
        if (segment.isEmpty()) return null;
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

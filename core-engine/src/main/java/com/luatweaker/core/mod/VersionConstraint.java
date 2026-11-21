package com.luatweaker.core.mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Inclusive version window used by manifest condition blocks
 * ({@code "minecraft"}, {@code "luatweaker"}).
 * Either bound may be {@code null} for an open-ended side.
 */
public record VersionConstraint(@Nullable String minVersion, @Nullable String maxVersion) {

    /** Empty constraint: no bound on either side, everything satisfies. */
    public static final VersionConstraint ANY = new VersionConstraint(null, null);

    /** True when {@code installed} falls inside the inclusive window. */
    public boolean satisfiedBy(@Nullable String installed) {
        return ModVersion.satisfies(installed, minVersion, maxVersion);
    }

    /** Human-readable fragment for skip messages, e.g. {@code ">= 1.0.0, <= 2.0.0"}. */
    public @NotNull String describe() {
        String min = describeBound(">=", minVersion);
        String max = describeBound("<=", maxVersion);
        if (min == null) {
            return max == null ? "any version" : max;
        }
        return max == null ? min : min + ", " + max;
    }

    /** @return {@code "<operator> <version>"} or {@code null} for an open side. */
    private static @Nullable String describeBound(@NotNull String operator, @Nullable String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        return operator + " " + version;
    }
}

package com.luatweaker.core.serverpacks;

import org.jetbrains.annotations.Nullable;

/**
 * Raw inputs for one bundle offer, straight from config or verdict JSON.
 *
 * <p>Every field is nullable: {@link ServerBundleValidation} treats blanks as
 * absent and fails the whole bundle on any invalid security-relevant field.
 * Keeping the five strings in one record keeps validation call sites honest
 * instead of drifting apart argument by argument.</p>
 */
public record ServerBundleSpec(
        @Nullable String url,
        @Nullable String sha256,
        @Nullable String version,
        @Nullable String name,
        @Nullable String filename) {
}

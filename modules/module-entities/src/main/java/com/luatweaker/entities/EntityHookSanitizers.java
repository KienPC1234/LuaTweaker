package com.luatweaker.entities;

/**
 * Pure sanitizers for entity hook results shared by platform dispatchers.
 *
 * <p>MC-free so the rules stay unit-testable without a game runtime: Lua scripts
 * can return {@code NaN} or infinite damage, and a cancelled death at 0 HP would
 * leave the entity in a half-alive state.
 */
public final class EntityHookSanitizers {

    private EntityHookSanitizers() {
    }

    /**
     * Resolves an {@code onHurt} replacement damage: non-finite replacements
     * ({@code NaN}, infinities) and {@code nil} keep the original damage instead
     * of poisoning the entity with {@code NaN}.
     */
    public static float sanitizeReplacementDamage(Float replacement, float currentDamage) {
        if (replacement == null || !Float.isFinite(replacement)) {
            return currentDamage;
        }
        return Math.max(0.0f, replacement);
    }

    /** Health to restore when an {@code onDeath} hook cancels death (never below 1 HP). */
    public static float cancelDeathHealth(float currentHealth) {
        return Math.max(1.0f, currentHealth);
    }
}

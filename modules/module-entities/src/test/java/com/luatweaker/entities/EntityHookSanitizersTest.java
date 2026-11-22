package com.luatweaker.entities;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Pure hook-result sanitizers: no game runtime needed. */
class EntityHookSanitizersTest {

    @Test
    void replacementDamageKeepsFiniteValues() {
        assertEquals(3.0f, EntityHookSanitizers.sanitizeReplacementDamage(3.0f, 10.0f),
                "finite replacements must apply");
    }

    @Test
    void replacementDamageClampsNegatives() {
        assertEquals(0.0f, EntityHookSanitizers.sanitizeReplacementDamage(-5.0f, 10.0f),
                "negative replacements must clamp to zero");
    }

    @Test
    void replacementDamageIgnoresNaN() {
        assertEquals(10.0f, EntityHookSanitizers.sanitizeReplacementDamage(Float.NaN, 10.0f),
                "NaN must keep the original damage");
    }

    @Test
    void replacementDamageIgnoresInfinity() {
        assertEquals(10.0f,
                EntityHookSanitizers.sanitizeReplacementDamage(Float.POSITIVE_INFINITY, 10.0f),
                "infinite damage must keep the original damage");
    }

    @Test
    void replacementDamageIgnoresNull() {
        assertEquals(10.0f, EntityHookSanitizers.sanitizeReplacementDamage(null, 10.0f),
                "nil must keep the original damage");
    }

    @Test
    void cancelDeathRestoresZeroToOne() {
        assertEquals(1.0f, EntityHookSanitizers.cancelDeathHealth(0.0f),
                "cancelled death at zero health must restore 1 HP");
    }

    @Test
    void cancelDeathRestoresNegativeToOne() {
        assertEquals(1.0f, EntityHookSanitizers.cancelDeathHealth(-4.0f),
                "cancelled death below zero must restore 1 HP");
    }

    @Test
    void cancelDeathKeepsPositiveHealth() {
        assertEquals(12.0f, EntityHookSanitizers.cancelDeathHealth(12.0f),
                "cancelled death above 1 HP must keep current health");
    }
}

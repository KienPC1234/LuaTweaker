package com.luatweaker.text;

import com.luatweaker.api.text.TextSpec;
import org.jetbrains.annotations.Nullable;

/** Shared null/blank guards for the text service. */
final class TextGuards {

    private TextGuards() {
    }

    static void requireNode(@Nullable TextSpec node) {
        if (node == null) {
            throw new IllegalArgumentException("Text service requires a TextSpec handle");
        }
    }

    static void requireKey(@Nullable String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Text " + label + " must not be blank");
        }
    }
}

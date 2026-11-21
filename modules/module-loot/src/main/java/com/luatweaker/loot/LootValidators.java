package com.luatweaker.loot;

import java.util.Locale;
import java.util.Set;

/**
 * Field validation for loot registrations. Every error names the offending
 * value so a broken script fails at registration time instead of emitting a
 * broken datapack table.
 */
final class LootValidators {

    /** Fishing categories of the vanilla {@code gameplay/fishing} router. */
    private static final Set<String> FISHING_CATEGORIES = Set.of("FISH", "JUNK", "TREASURE");

    private LootValidators() {}

    static void requireNonBlankIds(String firstId, String secondId, String what) {
        if (firstId.isBlank() || secondId.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
    }

    static void requireChance(double chance) {
        if (chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("chance must be between 0.0 and 1.0, got: " + chance);
        }
    }

    static void requireCountRange(int minCount, int maxCount) {
        if (minCount < 0 || maxCount < minCount) {
            throw new IllegalArgumentException("Invalid count range: min=" + minCount + ", max=" + maxCount);
        }
    }

    /** @return the upper-cased category when it names a vanilla fishing pool */
    static String requireFishingCategory(String category) {
        String normalized = category.toUpperCase(Locale.ROOT);
        if (!FISHING_CATEGORIES.contains(normalized)) {
            throw new IllegalArgumentException("category must be FISH, JUNK, or TREASURE, got: " + category);
        }
        return normalized;
    }
}

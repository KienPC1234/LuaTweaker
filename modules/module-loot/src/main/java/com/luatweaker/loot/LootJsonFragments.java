package com.luatweaker.loot;

/**
 * Shared JSON fragments for loot table rendering: the optional
 * {@code functions} array, the {@code random_chance} condition, and the
 * chance-to-weight conversion.
 */
final class LootJsonFragments {

    private static final double ALWAYS_APPLY_CHANCE = 1.0;
    private static final double PERCENT_SCALE = 100.0;

    private LootJsonFragments() {}

    static void appendFunctions(StringBuilder out, String functions) {
        if (!functions.isEmpty()) {
            out.append(",\"functions\":[").append(functions).append(']');
        }
    }

    static void appendRandomChance(StringBuilder out, double chance) {
        if (chance < ALWAYS_APPLY_CHANCE) {
            out.append(",\"conditions\":[{\"condition\":\"minecraft:random_chance\","
                    + "\"chance\":")
                    .append(chance).append("}]");
        }
    }

    /** Drops the separator comma the function renderers prefix each function with. */
    static String stripLeadingComma(StringBuilder functions) {
        return functions.length() == 0 ? "" : functions.substring(1);
    }

    /**
     * Chance (0.0-1.0) as integer percentage points, truncating toward zero
     * (so 0.999 never becomes 100). Written without a narrowing cast because
     * the builder overloads make the cast read as redundant.
     */
    static int percentWeight(double chance) {
        return Double.valueOf(chance * PERCENT_SCALE).intValue();
    }
}

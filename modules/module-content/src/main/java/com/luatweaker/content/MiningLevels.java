package com.luatweaker.content;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;

import java.util.Locale;
import java.util.Map;

/**
 * Resolves the mining-level tokens accepted by the item and block builders into
 * the numeric harvest tiers used by the platform.
 *
 * <p>Both builders expose the same {@code miningLevel(String)} contract, so the
 * name lookup lives here once instead of being duplicated in each builder.</p>
 */
final class MiningLevels {

    /** Named harvest tiers (vanilla tool material order). */
    private static final Map<String, Integer> NAMED_TIERS = Map.of(
            "WOOD", 0,
            "GOLD", 0,
            "STONE", 1,
            "IRON", 2,
            "DIAMOND", 3,
            "NETHERITE", 4);

    /** Tier used when a token is neither a known name nor a number. */
    private static final int UNKNOWN_TIER_FALLBACK = 1;

    private MiningLevels() {
    }

    /**
     * @param levelName named tier (case-insensitive) or a numeric tier
     * @return the numeric harvest tier, defaulting to {@value #UNKNOWN_TIER_FALLBACK}
     *         when the token is unknown
     */
    static int parse(String levelName) {
        Integer named = NAMED_TIERS.get(levelName.toUpperCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        try {
            return Integer.parseInt(levelName);
        } catch (NumberFormatException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Invalid mining level: " + levelName + ". Defaulting to " + UNKNOWN_TIER_FALLBACK + ".");
            return UNKNOWN_TIER_FALLBACK;
        }
    }
}

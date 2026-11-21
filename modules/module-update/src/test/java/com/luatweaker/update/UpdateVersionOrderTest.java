package com.luatweaker.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the ordering half of {@link UpdateVersion#compare}: numeric segments, major dominance and
 * how pre-release suffixes rank.
 */
class UpdateVersionOrderTest {

    private static final String V1_0_0 = "1.0.0";
    private static final String V1_0_1 = "1.0.1";
    private static final String V1_9 = "1.9";
    private static final String V1_10 = "1.10";

    @Test
    void patchIncrementIsNewer() {
        assertTrue(UpdateVersion.compare(V1_0_0, V1_0_1) < 0,
                "1.0.1 must rank above 1.0.0");
    }

    @Test
    void patchIncrementIsNewerWhenArgumentsAreReversed() {
        assertTrue(UpdateVersion.compare(V1_0_1, V1_0_0) > 0,
                "the comparison must be antisymmetric: 1.0.1 > 1.0.0");
    }

    @Test
    void multiDigitSegmentsCompareNumerically() {
        assertTrue(UpdateVersion.compare(V1_9, V1_10) < 0,
                "1.10 must beat 1.9 numerically, not lexicographically");
    }

    @Test
    void multiDigitSegmentsCompareNumericallyWhenArgumentsAreReversed() {
        assertTrue(UpdateVersion.compare(V1_10, V1_9) > 0,
                "the comparison must be antisymmetric: 1.10 > 1.9");
    }

    @Test
    void majorVersionDominatesEveryMinorSegment() {
        assertTrue(UpdateVersion.compare("1.99.99", "2.0.0") < 0,
                "a higher major version must outrank any minor/patch combination");
    }

    @Test
    void prereleaseAlphaRanksBelowBeta() {
        assertTrue(UpdateVersion.compare("1.0.0-alpha", "1.0.0-beta") < 0,
                "non-numeric segments must still compare lexicographically");
    }

    @Test
    void versionWithExtraSegmentRanksAboveItsPrefix() {
        assertTrue(UpdateVersion.compare("2.0.0-beta1", "2.0.0") > 0,
                "an extra non-numeric segment must not make the version look equal");
    }
}

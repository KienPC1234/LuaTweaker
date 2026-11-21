package com.luatweaker.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the equality half of {@link UpdateVersion#compare}: missing segments default to zero and
 * dot/dash/underscore separators are interchangeable.
 */
class UpdateVersionEqualityTest {

    private static final String V1_0_0 = "1.0.0";
    private static final String V1_2_3 = "1.2.3";
    private static final String ALPHA = "1.0.0-alpha";

    @Test
    void identicalVersionsAreEqual() {
        assertEquals(0, UpdateVersion.compare(V1_0_0, V1_0_0),
                "a version must compare equal to itself");
    }

    @Test
    void missingPatchSegmentCountsAsZero() {
        assertEquals(0, UpdateVersion.compare(V1_0_0, "1.0"),
                "1.0.0 and 1.0 must be treated as the same version");
    }

    @Test
    void repeatedVersionIsEqual() {
        assertEquals(0, UpdateVersion.compare("2.5.1", "2.5.1"),
                "identical multi-part versions must compare equal");
    }

    @Test
    void dotAndDashSeparatorsAreEquivalent() {
        assertEquals(0, UpdateVersion.compare(V1_2_3, "1-2-3"),
                "dash-separated segments must be read like dot-separated ones");
    }

    @Test
    void dotAndUnderscoreSeparatorsAreEquivalent() {
        assertEquals(0, UpdateVersion.compare(V1_2_3, "1_2_3"),
                "underscore-separated segments must be read like dot-separated ones");
    }

    @Test
    void identicalPrereleaseTagsAreEqual() {
        assertEquals(0, UpdateVersion.compare(ALPHA, ALPHA),
                "identical pre-release versions must compare equal");
    }
}

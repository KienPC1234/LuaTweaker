package com.luatweaker.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UpdateVersionTest {

    @Test
    void compare_NumericIncrement_IsGreater() {
        assertTrue(UpdateVersion.compare("1.0.0", "1.0.1") < 0);
        assertTrue(UpdateVersion.compare("1.0.1", "1.0.0") > 0);
    }

    @Test
    void compare_MultiDigitSegments_NumericNotLexicographic() {
        assertTrue(UpdateVersion.compare("1.9", "1.10") < 0, "1.10 must beat 1.9 numerically");
        assertTrue(UpdateVersion.compare("1.10", "1.9") > 0);
    }

    @Test
    void compare_EqualVersions_IsZero() {
        assertEquals(0, UpdateVersion.compare("1.0.0", "1.0.0"));
        assertEquals(0, UpdateVersion.compare("1.0.0", "1.0"));
        assertEquals(0, UpdateVersion.compare("2.5.1", "2.5.1"));
    }

    @Test
    void compare_MajorVersion_IsDominant() {
        assertTrue(UpdateVersion.compare("1.99.99", "2.0.0") < 0);
    }

    @Test
    void compare_SeparatorVariants_AreEquivalent() {
        assertEquals(0, UpdateVersion.compare("1.2.3", "1-2-3"));
        assertEquals(0, UpdateVersion.compare("1.2.3", "1_2_3"));
    }

    @Test
    void compare_NonNumericSegment_Lexicographic() {
        assertTrue(UpdateVersion.compare("1.0.0-alpha", "1.0.0-alpha") == 0);
        assertTrue(UpdateVersion.compare("1.0.0-alpha", "1.0.0-beta") < 0);
    }

    @Test
    void compare_NumericVsNonNumeric_NumericRanksLower() {
        assertTrue(UpdateVersion.compare("2.0.0-beta1", "2.0.0") > 0,
                "extra non-numeric segment must not make the version look equal");
    }
}

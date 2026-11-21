package com.luatweaker.core.mod;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModVersionTest {

    private static final String V_1_0_0 = "1.0.0";
    private static final String V_2_0_0 = "2.0.0";

    @Nested
    class SegmentComparison {

        @Test
        void comparesDottedVersionsSegmentBySegment1211() {
            assertTrue(ModVersion.compare("1.21.1", "1.21") > 0,
                    "a longer version with a newer patch segment must compare greater");
        }

        @Test
        void comparesDottedVersionsSegmentBySegment110() {
            assertTrue(ModVersion.compare("1.10", "1.9") > 0,
                    "10 must compare numerically greater than 9, not as a string");
        }

        @Test
        void comparesDottedVersionsSegmentBySegment200() {
            assertEquals(0, ModVersion.compare(V_2_0_0, V_2_0_0),
                    "identical versions must compare equal");
        }

        @Test
        void comparesDottedVersionsSegmentBySegment10() {
            assertEquals(0, ModVersion.compare("1.0", V_1_0_0),
                    "a missing trailing segment must be treated as zero");
        }

        @Test
        void comparesDottedVersionsSegmentBySegment099() {
            assertTrue(ModVersion.compare("0.9.9", V_1_0_0) < 0,
                    "a lower major version must compare smaller");
        }
    }

    @Nested
    class QualifiersAndGarbage {

        @Test
        void toleratesQualifiersAndGarbage100Beta() {
            assertEquals(0, ModVersion.compare("1.0.0-beta", V_1_0_0),
                    "a pre-release qualifier must be ignored by the comparison");
        }

        @Test
        void toleratesQualifiersAndGarbage1211() {
            assertTrue(ModVersion.compare("1.21.1", "not-a-version") > 0,
                    "a parseable version must outrank unparseable text");
        }

        @Test
        void toleratesQualifiersAndGarbage100() {
            assertTrue(ModVersion.compare(null, V_1_0_0) < 0,
                    "a null version must rank below a concrete version");
        }

        @Test
        void toleratesQualifiersAndGarbage1002() {
            assertTrue(ModVersion.compare(V_1_0_0, null) > 0,
                    "a concrete version must outrank null");
        }
    }

    @Nested
    class InclusiveWindow {

        @Test
        void satisfiesInclusiveWindowWithOpenSides150() {
            assertTrue(ModVersion.satisfies("1.5.0", V_1_0_0, V_2_0_0),
                    "a version strictly inside the window must be satisfied");
        }

        @Test
        void satisfiesInclusiveWindowWithOpenSides100() {
            assertTrue(ModVersion.satisfies(V_1_0_0, V_1_0_0, V_2_0_0),
                    "the lower bound itself must be satisfied (inclusive window)");
        }

        @Test
        void satisfiesInclusiveWindowWithOpenSides200() {
            assertTrue(ModVersion.satisfies(V_2_0_0, V_1_0_0, V_2_0_0),
                    "the upper bound itself must be satisfied (inclusive window)");
        }

        @Test
        void satisfiesInclusiveWindowWithOpenSides201() {
            assertFalse(ModVersion.satisfies("2.0.1", V_1_0_0, V_2_0_0),
                    "a version above the upper bound must not be satisfied");
        }

        @Test
        void satisfiesInclusiveWindowWithOpenSides099() {
            assertFalse(ModVersion.satisfies("0.9.9", V_1_0_0, V_2_0_0),
                    "a version below the lower bound must not be satisfied");
        }

        @Test
        void satisfiesInclusiveWindowWithOpenSides999() {
            assertTrue(ModVersion.satisfies("9.9.9", V_1_0_0, null),
                    "a null upper bound must leave the window open upwards");
        }

        @Test
        void satisfiesInclusiveWindowWithOpenSides001() {
            assertTrue(ModVersion.satisfies("0.0.1", null, V_2_0_0),
                    "a null lower bound must leave the window open downwards");
        }

        @Test
        void satisfiesInclusiveWindowWithOpenSides1002() {
            assertTrue(ModVersion.satisfies(V_1_0_0, null, null),
                    "a completely open window must accept any concrete version");
        }

        @Test
        void satisfiesInclusiveWindowWithOpenSides1003() {
            assertFalse(ModVersion.satisfies(null, V_1_0_0, V_2_0_0),
                    "a null version must never satisfy a bounded window");
        }

        @Test
        void satisfiesInclusiveWindowWithOpenSidesSatisfies() {
            assertFalse(ModVersion.satisfies("", null, null),
                    "an empty version string must never satisfy an open window");
        }
    }
}

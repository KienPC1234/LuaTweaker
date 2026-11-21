package com.luatweaker.core.remap;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeRemapperTest {

    private static final String LENGTH = "length";
    private static final String SUBSTRING = "substring";
    private static final String VALUE_OF = "valueOf";

    private RuntimeRemapper remapper;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void setup() {
        remapper = RuntimeRemapper.getInstance();
        remapper.clearCache();
    }

    @Nested
    class ExactMatch {

        @Test
        void testExactMatchFindsStringMethodsShouldFindStringLength() {
            Method result = remapper.resolveMethod(String.class, LENGTH);
            assertNotNull(result, "Should find String.length()");
        }

        @Test
        void testExactMatchFindsStringMethodsLength() {
            Method result = remapper.resolveMethod(String.class, LENGTH);
            assertEquals(LENGTH, result.getName(), "the exact match must resolve to length()");
        }

        @Test
        void testExactMatchFindsSubstringMethodsShouldFindStringSubstring() {
            Method result = remapper.resolveMethod(String.class, SUBSTRING);
            assertNotNull(result, "Should find String.substring()");
        }

        @Test
        void testExactMatchFindsSubstringMethodsSubstring() {
            Method result = remapper.resolveMethod(String.class, SUBSTRING);
            assertEquals(SUBSTRING, result.getName(), "the exact match must resolve to substring()");
        }
    }

    @Nested
    class HeuristicMatch {

        @Test
        void testHeuristicMatchCaseInsensitiveShouldFindStringLengthViaCaseInsensitive() {
            Method result = remapper.resolveMethod(String.class, "LENGTH");
            assertNotNull(result, "Should find String.length() via case-insensitive match");
        }

        @Test
        void testHeuristicMatchCaseInsensitiveLength() {
            Method result = remapper.resolveMethod(String.class, "LENGTH");
            assertEquals(LENGTH, result.getName(),
                    "an upper-case request must resolve to the lower-case Java name");
        }

        @Test
        void testHeuristicMatchContainsPattern() {
            Method result = remapper.resolveMethod(String.class, "hash");
            assertNotNull(result, "Should find String.hashCode() via contains pattern");
        }

        @Test
        void testHeuristicSkipsObfuscatedNames() {
            Method result = remapper.resolveMethod(String.class, "m_12345_");
            assertNull(result, "Should NOT match obfuscated names via heuristic");
        }
    }

    @Nested
    class SignatureMatch {

        @Test
        void testSignatureMatchWithParamTypesShouldFindSubstringIntInt() {
            Method result = remapper.resolveMethodBySignature(
                    String.class, SUBSTRING,
                    new Class<?>[]{int.class, int.class}, String.class);
            assertNotNull(result, "Should find substring(int, int)");
        }

        @Test
        void testSignatureMatchWithParamTypesSubstring() {
            Method result = remapper.resolveMethodBySignature(
                    String.class, SUBSTRING,
                    new Class<?>[]{int.class, int.class}, String.class);
            assertEquals(SUBSTRING, result.getName(),
                    "the signature lookup must resolve to substring(int, int)");
        }

        @Test
        void testSignatureMatchWithReturnTypeShouldFindLengthReturningInt() {
            Method result = remapper.resolveMethodBySignature(
                    String.class, LENGTH, null, int.class);
            assertNotNull(result, "Should find length() returning int");
        }

        @Test
        void testSignatureMatchWithReturnTypeLength() {
            Method result = remapper.resolveMethodBySignature(
                    String.class, LENGTH, null, int.class);
            assertEquals(LENGTH, result.getName(),
                    "the return-type lookup must resolve to length()");
        }

        @Test
        void testSignatureMatchNoMatchReturnsNull() {
            Method result = remapper.resolveMethodBySignature(
                    String.class, "nonExistentMethod",
                    new Class<?>[]{double.class}, void.class);
            assertNull(result, "Should return null for non-existent method");
        }
    }

    @Nested
    class InheritanceTraversal {

        @Test
        void testDeepInheritanceFindsPublicInheritedMethods() {
            Method result = remapper.resolveMethod(ArrayList.class, "size");
            assertNotNull(result, "Should find size() inherited from AbstractList/List");
        }

        @Test
        void testDeepInheritanceTraversesMultipleLevels() {
            Method result = remapper.resolveMethod(LinkedList.class, "add");
            assertNotNull(result, "Should find add() inherited through multiple levels");
        }
    }

    @Nested
    class OverloadResolution {

        @Test
        void testOverloadResolutionMatchesParamCountShouldFindSubstringIntNotSubstringIntInt() {
            Method result = remapper.resolveMethod(String.class, SUBSTRING, int.class);
            assertNotNull(result, "Should find substring(int) not substring(int, int)");
        }

        @Test
        void testOverloadResolutionMatchesParamCountGetParameterCount() {
            Method result = remapper.resolveMethod(String.class, SUBSTRING, int.class);
            assertEquals(1, result.getParameterCount(),
                    "the resolved overload must be the single-argument substring(int)");
        }

        @Test
        void testOverloadResolutionMatchesParamTypes() {
            Method result = remapper.resolveMethod(String.class, VALUE_OF, int.class);
            assertNotNull(result, "Should find valueOf(int)");
        }

        @Test
        void testOverloadResolutionFindsMethodWithParamHintsShouldFindAValueOfMethodWithIntParamHint() {
            Method result = remapper.resolveMethod(String.class, VALUE_OF, int.class);
            assertNotNull(result, "Should find a valueOf method with int param hint");
        }

        @Test
        void testOverloadResolutionFindsMethodWithParamHintsValueOf() {
            Method result = remapper.resolveMethod(String.class, VALUE_OF, int.class);
            assertEquals(VALUE_OF, result.getName(),
                    "the param-hint lookup must resolve to valueOf");
        }
    }

    @Nested
    class CacheAndEnvironment {

        @Test
        void testCachingReturnsSameInstanceCheck() {
            Method first = remapper.resolveMethod(String.class, LENGTH);
            assertNotNull(first, "the cached path must still resolve length()");
        }

        @Test
        void testCachingReturnsSameInstanceCacheShouldReturnSameMethodInstance() {
            Method first = remapper.resolveMethod(String.class, LENGTH);
            Method second = remapper.resolveMethod(String.class, LENGTH);
            assertSame(first, second, "Cache should return same Method instance");
        }

        @Test
        void testCrossPlatformDoesNotMatchIntermediary() {
            Method result = remapper.resolveMethod(String.class, "method_1234");
            assertNull(result, "Should NOT resolve intermediary-style names via heuristic");
        }

        @Test
        void testObfuscationDetectionDefaultFalse() {
            assertFalse(remapper.isObfuscatedEnvironment(),
                    "Default should be false in dev environment");
        }
    }
}

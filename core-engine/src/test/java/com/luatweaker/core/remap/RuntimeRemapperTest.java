package com.luatweaker.core.remap;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

public class RuntimeRemapperTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private RuntimeRemapper remapper;

    @BeforeEach
    void setup() {
        remapper = RuntimeRemapper.getInstance();
        remapper.clearCache();
    }

    @Test
    void testExactMatch_FindsStringMethods() {
        Method result = remapper.resolveMethod(String.class, "length");
        assertNotNull(result, "Should find String.length()");
        assertEquals("length", result.getName());
    }

    @Test
    void testExactMatch_FindsSubstringMethods() {
        Method result = remapper.resolveMethod(String.class, "substring");
        assertNotNull(result, "Should find String.substring()");
        assertEquals("substring", result.getName());
    }

    @Test
    void testHeuristicMatch_CaseInsensitive() {
        Method result = remapper.resolveMethod(String.class, "LENGTH");
        assertNotNull(result, "Should find String.length() via case-insensitive match");
        assertEquals("length", result.getName());
    }

    @Test
    void testHeuristicMatch_ContainsPattern() {
        Method result = remapper.resolveMethod(String.class, "hash");
        assertNotNull(result, "Should find String.hashCode() via contains pattern");
    }

    @Test
    void testHeuristic_SkipsObfuscatedNames() {
        Method result = remapper.resolveMethod(String.class, "m_12345_");
        assertNull(result, "Should NOT match obfuscated names via heuristic");
    }

    @Test
    void testSignatureMatch_WithParamTypes() {
        Method result = remapper.resolveMethodBySignature(
                String.class, "substring",
                new Class<?>[]{int.class, int.class}, String.class
        );
        assertNotNull(result, "Should find substring(int, int)");
        assertEquals("substring", result.getName());
    }

    @Test
    void testSignatureMatch_WithReturnType() {
        Method result = remapper.resolveMethodBySignature(
                String.class, "length", null, int.class
        );
        assertNotNull(result, "Should find length() returning int");
        assertEquals("length", result.getName());
    }

    @Test
    void testSignatureMatch_NoMatchReturnsNull() {
        Method result = remapper.resolveMethodBySignature(
                String.class, "nonExistentMethod",
                new Class<?>[]{double.class}, void.class
        );
        assertNull(result, "Should return null for non-existent method");
    }

    @Test
    void testDeepInheritance_FindsPublicInheritedMethods() {
        Method result = remapper.resolveMethod(java.util.ArrayList.class, "size");
        assertNotNull(result, "Should find size() inherited from AbstractList/List");
    }

    @Test
    void testDeepInheritance_TraversesMultipleLevels() {
        Method result = remapper.resolveMethod(java.util.LinkedList.class, "add");
        assertNotNull(result, "Should find add() inherited through multiple levels");
    }

    @Test
    void testOverloadResolution_MatchesParamCount() {
        Method result = remapper.resolveMethod(
                String.class, "substring", new Class<?>[]{int.class}
        );
        assertNotNull(result, "Should find substring(int) not substring(int, int)");
        assertEquals(1, result.getParameterCount());
    }

    @Test
    void testOverloadResolution_MatchesParamTypes() {
        Method result = remapper.resolveMethod(
                String.class, "valueOf", new Class<?>[]{int.class}
        );
        assertNotNull(result, "Should find valueOf(int)");
    }

    @Test
    void testOverloadResolution_FindsMethodWithParamHints() {
        Method result = remapper.resolveMethod(
                String.class, "valueOf", new Class<?>[]{int.class}
        );
        assertNotNull(result, "Should find a valueOf method with int param hint");
        assertEquals("valueOf", result.getName());
    }

    @Test
    void testCaching_ReturnsSameInstance() {
        Method first = remapper.resolveMethod(String.class, "length");
        Method second = remapper.resolveMethod(String.class, "length");
        assertNotNull(first);
        assertSame(first, second, "Cache should return same Method instance");
    }

    @Test
    void testCrossPlatform_DoesNotMatchIntermediary() {
        Method result = remapper.resolveMethod(String.class, "method_1234");
        assertNull(result, "Should NOT resolve intermediary-style names via heuristic");
    }

    @Test
    void testObfuscationDetection_DefaultFalse() {
        assertFalse(remapper.isObfuscatedEnvironment(),
                "Default should be false in dev environment");
    }
}

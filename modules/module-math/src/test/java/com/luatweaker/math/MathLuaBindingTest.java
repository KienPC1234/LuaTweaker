package com.luatweaker.math;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MathLuaBindingTest {

    private static final String MATH_SCRIPT =
            "_G._mathResults = {\n"
            + "    math.clamp(150, 0, 100),\n"
            + "    math.lerp(10, 20, 0.5),\n"
            + "    math.round(4.6),\n"
            + "}";

    private static final String SPLIT_SCRIPT =
            "_G._splitCount = #string.split('a,b,c', ',')";

    private static final String TRIM_SCRIPT =
            "_G._trimmed = string.trim('  hello  ')";

    private static final String FAILING_SCRIPT = "assert(false, 'boom')";

    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(true);
        MathLuaBinding.registerBindings(engine);
    }

    @Test
    void robloxMathExtensionsReturnClampedLerpedAndRoundedValues() {
        engine.executeStringOrThrow(MATH_SCRIPT, "MathTest");

        ILuaTable results = engine.getGlobalEnvironment().rawget("_mathResults").asTable();
        double[] actual = {
            results.rawget(1).asDouble(),
            results.rawget(2).asDouble(),
            results.rawget(3).asDouble(),
        };
        assertArrayEquals(new double[] {100.0, 15.0, 5.0}, actual, 1e-9,
                "math.clamp/lerp/round must return 100, 15 and 5 for the scripted inputs");
    }

    @Test
    void robloxStringSplitReturnsEveryPart() {
        engine.executeStringOrThrow(SPLIT_SCRIPT, "StringTest");

        assertEquals(3, engine.getGlobalEnvironment().rawget("_splitCount").asInt(),
                "string.split('a,b,c', ',') must return three parts");
    }

    @Test
    void robloxStringTrimStripsSurroundingWhitespace() {
        engine.executeStringOrThrow(TRIM_SCRIPT, "StringTest");

        assertEquals("hello", engine.getGlobalEnvironment().rawget("_trimmed").asString(),
                "string.trim must strip the surrounding spaces");
    }

    @Test
    void executeStringOrThrowSurfacesAFailingChunk() {
        assertThrows(IllegalStateException.class,
                () -> engine.executeStringOrThrow(FAILING_SCRIPT, "FailingTest"),
                "a Lua assertion failure must surface as a Java exception");
    }

    @Test
    void failingChunkErrorMentionsTheLuaMessage() {
        IllegalStateException error = runFailingChunk();

        assertTrue(error.getMessage() != null && error.getMessage().contains("boom"),
                "the Java error must carry the Lua failure message, got: " + error.getMessage());
    }

    private IllegalStateException runFailingChunk() {
        return assertThrows(IllegalStateException.class,
                () -> engine.executeStringOrThrow(FAILING_SCRIPT, "FailingTest"));
    }
}

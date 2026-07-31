package com.luatweaker.math;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MathLuaBindingTest {

    private ILuaEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new CobaltLuaEngine(true);
        MathLuaBinding.registerBindings(engine);
    }

    @Test
    public void testRobloxMathExtensions() {
        assertDoesNotThrow(() -> {
            engine.executeString(
                "local c = math.clamp(150, 0, 100)\n" +
                "assert(c == 100, 'math.clamp failed')\n" +
                "local l = math.lerp(10, 20, 0.5)\n" +
                "assert(l == 15, 'math.lerp failed')\n" +
                "local r = math.round(4.6)\n" +
                "assert(r == 5, 'math.round failed')",
                "MathTest"
            );
        });
    }

    @Test
    public void testRobloxStringExtensions() {
        assertDoesNotThrow(() -> {
            engine.executeString(
                "local parts = string.split('a,b,c', ',')\n" +
                "assert(#parts == 3, 'string.split count failed')\n" +
                "local trimmed = string.trim('  hello  ')\n" +
                "assert(trimmed == 'hello', 'string.trim failed')",
                "StringTest"
            );
        });
    }
}

package com.luatweaker.common.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The UserInputService bridge must deliver typed input events to Lua listeners. */
class UserInputDispatcherTest {

    private static final String BEGAN_SCRIPT =
            "_type = nil\n"
            + "_code = -1\n"
            + "_action = -1\n"
            + "_mods = -1\n"
            + "_processed = nil\n"
            + "UserInputService.InputBegan:Connect(function(inputType, code, action, modifiers, gameProcessed)\n"
            + "    _type = inputType\n"
            + "    _code = code\n"
            + "    _action = action\n"
            + "    _mods = modifiers\n"
            + "    _processed = gameProcessed\n"
            + "end)";

    private static ILuaEngine engineWithBeganListener() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(BEGAN_SCRIPT, "input_began");
        UserInputDispatcher.fire(engine, "InputBegan", UserInputDispatcher.TYPE_KEY,
                87 /* GLFW_KEY_W */, 1, 2, true);
        return engine;
    }

    private static double globalNumber(ILuaEngine engine, String name) {
        return engine.getGlobalEnvironment().rawget(name).asDouble();
    }

    @Test
    void inputBeganDeliversTheInputType() {
        assertEquals("key",
                engineWithBeganListener().getGlobalEnvironment().rawget("_type").asString(),
                "Lua must see the key input type for a keyboard event");
    }

    @Test
    void inputBeganDeliversTheKeyCode() {
        assertEquals(87.0, globalNumber(engineWithBeganListener(), "_code"),
                "Lua must see the GLFW key code of the pressed key");
    }

    @Test
    void inputBeganDeliversTheAction() {
        assertEquals(1.0, globalNumber(engineWithBeganListener(), "_action"),
                "Lua must see the press action of the input event");
    }

    @Test
    void inputBeganDeliversTheModifiers() {
        assertEquals(2.0, globalNumber(engineWithBeganListener(), "_mods"),
                "Lua must see the held modifier bits of the input event");
    }

    @Test
    void inputBeganDeliversTheGameProcessedFlag() {
        ILuaEngine engine = engineWithBeganListener();

        assertTrue(engine.getGlobalEnvironment().rawget("_processed").asBoolean(),
                "scripts need to know when a GUI consumed the input");
    }
}

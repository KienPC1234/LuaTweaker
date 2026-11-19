package com.luatweaker.platform.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserInputDispatcherTest {

    @Test
    void inputBeganDeliversTypeCodeActionAndModifiers() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(
                "_type = nil\n" +
                "_code = -1\n" +
                "_action = -1\n" +
                "_mods = -1\n" +
                "_processed = nil\n" +
                "UserInputService.InputBegan:Connect(function(inputType, code, action, modifiers, gameProcessed)\n" +
                "    _type = inputType\n" +
                "    _code = code\n" +
                "    _action = action\n" +
                "    _mods = modifiers\n" +
                "    _processed = gameProcessed\n" +
                "end)",
                "input_began");

        UserInputDispatcher.fire(engine, "InputBegan", UserInputDispatcher.TYPE_KEY,
                87 /* GLFW_KEY_W */, 1, 2, true);

        ILuaTable globals = engine.getGlobalEnvironment();
        assertEquals("key", globals.rawget("_type").asString());
        assertEquals(87.0, globals.rawget("_code").asDouble());
        assertEquals(1.0, globals.rawget("_action").asDouble());
        assertEquals(2.0, globals.rawget("_mods").asDouble());
        assertEquals(true, globals.rawget("_processed").asBoolean(),
                "scripts need to know when a GUI consumed the input");
    }

    @Test
    void inputEndedIsSeparateFromInputBegan() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(
                "_began = 0\n" +
                "_ended = 0\n" +
                "UserInputService.InputBegan:Connect(function() _began = _began + 1 end)\n" +
                "UserInputService.InputEnded:Connect(function() _ended = _ended + 1 end)",
                "input_ended");

        UserInputDispatcher.fire(engine, "InputBegan", UserInputDispatcher.TYPE_MOUSE, 0, 1, 0, false);
        UserInputDispatcher.fire(engine, "InputEnded", UserInputDispatcher.TYPE_MOUSE, 0, 0, 0, false);

        ILuaTable globals = engine.getGlobalEnvironment();
        assertEquals(1.0, globals.rawget("_began").asDouble());
        assertEquals(1.0, globals.rawget("_ended").asDouble());
    }

    @Test
    void aFailingListenerIsContained() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(
                "_reached = false\n" +
                "UserInputService.InputBegan:Connect(function() error('listener boom') end)\n" +
                "UserInputService.InputBegan:Connect(function() _reached = true end)",
                "input_failing");

        UserInputDispatcher.fire(engine, "InputBegan", UserInputDispatcher.TYPE_KEY, 32, 1, 0, false);

        assertEquals(true, engine.getGlobalEnvironment().rawget("_reached").asBoolean());
    }

    @Test
    void missingServiceIsANoOp() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.getGlobalEnvironment().rawset("UserInputService", engine.nilValue());
        UserInputDispatcher.fire(engine, "InputBegan", UserInputDispatcher.TYPE_KEY, 1, 1, 0, false);
        // Reaching this line without an exception is the assertion: dispatch must not require
        // the service to exist (dedicated servers have no input).
    }
}

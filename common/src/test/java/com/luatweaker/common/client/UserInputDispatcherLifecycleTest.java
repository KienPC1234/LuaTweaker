package com.luatweaker.common.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Input dispatch must stay isolated per signal and never break on bad listeners. */
class UserInputDispatcherLifecycleTest {

    private static ILuaEngine engineWithBeganAndEndedCounters() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(
                "_began = 0\n"
                + "_ended = 0\n"
                + "UserInputService.InputBegan:Connect(function() _began = _began + 1 end)\n"
                + "UserInputService.InputEnded:Connect(function() _ended = _ended + 1 end)",
                "input_ended");

        UserInputDispatcher.fire(engine, "InputBegan", UserInputDispatcher.TYPE_MOUSE, 0, 1, 0, false);
        UserInputDispatcher.fire(engine, "InputEnded", UserInputDispatcher.TYPE_MOUSE, 0, 0, 0, false);
        return engine;
    }

    @Test
    void beganCounterFiresOnInputBegan() {
        assertEquals(1.0,
                engineWithBeganAndEndedCounters().getGlobalEnvironment().rawget("_began").asDouble(),
                "the InputBegan listener must run exactly once per began event");
    }

    @Test
    void endedCounterFiresOnInputEnded() {
        assertEquals(1.0,
                engineWithBeganAndEndedCounters().getGlobalEnvironment().rawget("_ended").asDouble(),
                "the InputEnded listener must run exactly once per ended event");
    }

    @Test
    void aFailingListenerDoesNotBlockLaterListeners() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(
                "_reached = false\n"
                + "UserInputService.InputBegan:Connect(function() error('listener boom') end)\n"
                + "UserInputService.InputBegan:Connect(function() _reached = true end)",
                "input_failing");

        UserInputDispatcher.fire(engine, "InputBegan", UserInputDispatcher.TYPE_KEY, 32, 1, 0, false);

        assertTrue(engine.getGlobalEnvironment().rawget("_reached").asBoolean(),
                "a throwing listener must not stop the remaining listeners");
    }

    @Test
    void missingServiceIsANoOp() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.getGlobalEnvironment().rawset("UserInputService", engine.nilValue());

        assertDoesNotThrow(
                () -> UserInputDispatcher.fire(engine, "InputBegan", UserInputDispatcher.TYPE_KEY, 1, 1, 0, false),
                "dispatch must not require the service to exist (dedicated servers have no input)");
    }
}

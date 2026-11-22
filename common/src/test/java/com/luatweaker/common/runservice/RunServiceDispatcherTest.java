package com.luatweaker.common.runservice;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Heartbeat/Stepped signals must reach Lua listeners synchronously and safely. */
class RunServiceDispatcherTest {

    private static final String HEARTBEAT = "Heartbeat";
    private static final double DELTA = 0.05;

    private static ILuaEngine engineWithSkippedCounter() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(
                "_skipped = 0\n"
                + "_conn = RunService.Heartbeat:Connect(function() _skipped = _skipped + 1 end)\n",
                "disconnect_setup");
        return engine;
    }

    @Test
    void firesListenerSynchronouslyWithTheDelta() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(
                "_delta = -1\n"
                + "RunService.Heartbeat:Connect(function(dt) _delta = dt end)",
                "heartbeat_listener");

        RunServiceDispatcher.fire(engine, HEARTBEAT, 0.25);

        assertEquals(0.25, engine.getGlobalEnvironment().rawget("_delta").asDouble(), 1e-9,
                "Heartbeat listeners must run during the tick, not on a later task");
    }

    @Test
    void firesEverySignalName() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(
                "_seen = 0\n"
                + "RunService.Stepped:Connect(function() _seen = _seen + 1 end)\n"
                + "RunService.Heartbeat:Connect(function() _seen = _seen + 1 end)\n"
                + "RunService.RenderStepped:Connect(function() _seen = _seen + 1 end)",
                "all_signals");

        RunServiceDispatcher.fire(engine, "Stepped", DELTA);
        RunServiceDispatcher.fire(engine, HEARTBEAT, DELTA);
        RunServiceDispatcher.fire(engine, "RenderStepped", DELTA);

        assertEquals(3.0, engine.getGlobalEnvironment().rawget("_seen").asDouble(),
                "every signal name must dispatch to its own listeners");
    }

    @Test
    void failingListenerDoesNotBlockTheNextOne() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(
                "_after = false\n"
                + "RunService.Heartbeat:Connect(function() error('listener boom') end)\n"
                + "RunService.Heartbeat:Connect(function() _after = true end)",
                "failing_listener");

        RunServiceDispatcher.fire(engine, HEARTBEAT, DELTA);

        assertTrue(engine.getGlobalEnvironment().rawget("_after").asBoolean(),
                "a broken listener must not stop the others");
    }

    @Test
    void nullEngineAndUnknownSignalAreNoOps() {
        ILuaEngine engine = LuaEngineFactory.create(false);

        assertDoesNotThrow(() -> {
            RunServiceDispatcher.fire(null, HEARTBEAT, DELTA);
            RunServiceDispatcher.fire(engine, "NoSuchSignal", DELTA);
            RunServiceDispatcher.fire(engine, HEARTBEAT, DELTA);
        }, "a null engine or unknown signal must be a silent no-op");
    }

    @Test
    void connectedListenerFires() {
        ILuaEngine engine = engineWithSkippedCounter();

        // Connected listener: fires normally through the fast-path check.
        RunServiceDispatcher.fire(engine, HEARTBEAT, DELTA);

        assertEquals(1.0, engine.getGlobalEnvironment().rawget("_skipped").asDouble(),
                "a connected listener must fire exactly once per signal");
    }

    @Test
    void disconnectedListenerIsSkipped() {
        ILuaEngine engine = engineWithSkippedCounter();
        RunServiceDispatcher.fire(engine, HEARTBEAT, DELTA);

        // After Disconnect the listener list is empty: the dispatcher must skip
        // the Lua pcall entirely instead of erroring or firing.
        engine.executeStringOrThrow("_conn:Disconnect()", "disconnect_all");
        RunServiceDispatcher.fire(engine, HEARTBEAT, DELTA);

        assertEquals(1.0, engine.getGlobalEnvironment().rawget("_skipped").asDouble(),
                "listenerless signal must be skipped, not fired");
    }
}

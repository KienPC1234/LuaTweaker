package com.luatweaker.platform.runservice;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunServiceDispatcherTest {

    @Test
    void firesListenerSynchronouslyWithTheDelta() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(
                "_delta = -1\n" +
                "RunService.Heartbeat:Connect(function(dt) _delta = dt end)",
                "heartbeat_listener");

        RunServiceDispatcher.fire(engine, "Heartbeat", 0.25);

        ILuaTable globals = engine.getGlobalEnvironment();
        assertEquals(0.25, globals.rawget("_delta").asDouble(), 1e-9,
                "Heartbeat listeners must run during the tick, not on a later task");
    }

    @Test
    void firesEverySignalName() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(
                "_seen = 0\n" +
                "RunService.Stepped:Connect(function() _seen = _seen + 1 end)\n" +
                "RunService.Heartbeat:Connect(function() _seen = _seen + 1 end)\n" +
                "RunService.RenderStepped:Connect(function() _seen = _seen + 1 end)",
                "all_signals");

        RunServiceDispatcher.fire(engine, "Stepped", 0.05);
        RunServiceDispatcher.fire(engine, "Heartbeat", 0.05);
        RunServiceDispatcher.fire(engine, "RenderStepped", 0.05);

        assertEquals(3.0, engine.getGlobalEnvironment().rawget("_seen").asDouble());
    }

    @Test
    void aFailingListenerDoesNotEscapeOrBlockTheNextOne() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(
                "_after = false\n" +
                "RunService.Heartbeat:Connect(function() error('listener boom') end)\n" +
                "RunService.Heartbeat:Connect(function() _after = true end)",
                "failing_listener");

        RunServiceDispatcher.fire(engine, "Heartbeat", 0.05);

        assertTrue(engine.getGlobalEnvironment().rawget("_after").asBoolean(),
                "a broken listener must not stop the others");
    }

    @Test
    void nullEngineAndUnknownSignalAreNoOps() {
        RunServiceDispatcher.fire(null, "Heartbeat", 0.05);
        ILuaEngine engine = LuaEngineFactory.create(false);
        RunServiceDispatcher.fire(engine, "NoSuchSignal", 0.05);
        RunServiceDispatcher.fire(engine, "Heartbeat", 0.05);
        // Reaching this line means no exception escaped.
        assertTrue(true);
    }

    @Test
    void disconnectEmptiesListenersAndListenerlessFireIsSkipped() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow(
                "_skipped = 0\n" +
                "_conn = RunService.Heartbeat:Connect(function() _skipped = _skipped + 1 end)\n",
                "disconnect_setup");

        // Connected listener: fires normally through the fast-path check.
        RunServiceDispatcher.fire(engine, "Heartbeat", 0.05);
        assertEquals(1.0, engine.getGlobalEnvironment().rawget("_skipped").asDouble());

        // After Disconnect the listener list is empty: the dispatcher must skip
        // the Lua pcall entirely instead of erroring or firing.
        engine.executeStringOrThrow("_conn:Disconnect()", "disconnect_all");
        RunServiceDispatcher.fire(engine, "Heartbeat", 0.05);
        assertEquals(1.0, engine.getGlobalEnvironment().rawget("_skipped").asDouble(),
                "listenerless signal must be skipped, not fired");
    }

    @Test
    void tickDeltaIsBounded() {
        double first = ServerTickClock.nextDelta();
        double second = ServerTickClock.nextDelta();
        assertTrue(first >= ServerTickClock.NOMINAL_DELTA_SECONDS && first <= 1.0, "first=" + first);
        assertTrue(second >= ServerTickClock.NOMINAL_DELTA_SECONDS && second <= 1.0, "second=" + second);
    }
}

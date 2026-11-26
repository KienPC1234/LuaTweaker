package com.luatweaker.core.signal;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link LuaSignalDispatcher}: listenerless short-circuiting before argument
 * wrapping, synchronous cached dispatch when listeners exist, and disconnect cleanup.
 */
class LuaSignalDispatcherTest {

    private static final String SERVICE = "GuiService";
    private static final String SIGNAL = "OnRenderHUD";

    private ILuaEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    void listenerlessSignalSkipsArgsSupplierAndDispatch() {
        engine = LuaEngineFactory.create(false);
        AtomicInteger supplierCalls = new AtomicInteger();

        LuaSignalDispatcher.fireWithArgs(engine, SERVICE, SIGNAL, () -> {
            supplierCalls.incrementAndGet();
            return new ILuaValue[] { engine.wrapNumber(1.0) };
        });

        assertAll("zero listeners must bypass argument wrapping and Lua entry",
                () -> assertFalse(LuaSignalDispatcher.hasListeners(engine, SERVICE, SIGNAL),
                        "signal with no connected listeners must report false"),
                () -> assertEquals(0, supplierCalls.get(),
                        "argsSupplier must not run when no listener is connected"));
    }

    @Test
    void connectedListenerReceivesSignalAndDisconnectResumesSkip() {
        engine = LuaEngineFactory.create(false);
        engine.executeStringOrThrow("""
                GuiService = { OnRenderHUD = Signal.new() }
                _hudCount = 0
                _hudLast = -1
                _hudConn = GuiService.OnRenderHUD:Connect(function(dt)
                    _hudCount = _hudCount + 1
                    _hudLast = dt
                end)
                """, "hud_signal_setup");

        boolean connectedBeforeFire = LuaSignalDispatcher.hasListeners(engine, SERVICE, SIGNAL);
        LuaSignalDispatcher.fireNumber(engine, SERVICE, SIGNAL, 0.25);

        engine.executeStringOrThrow("_hudConn:Disconnect()", "hud_signal_disconnect");
        boolean connectedAfterDisconnect = LuaSignalDispatcher.hasListeners(engine, SERVICE, SIGNAL);
        LuaSignalDispatcher.fireNumber(engine, SERVICE, SIGNAL, 0.75);

        assertAll("connected listeners must run synchronously until disconnected",
                () -> assertTrue(connectedBeforeFire,
                        "signal with an active listener must report true"),
                () -> assertEquals(1, engine.getGlobalEnvironment().rawget("_hudCount").asInt(),
                        "listener must fire once before disconnect"),
                () -> assertEquals(0.25, engine.getGlobalEnvironment().rawget("_hudLast").asDouble(), 1e-9,
                        "listener must receive the dispatched delta argument"),
                () -> assertFalse(connectedAfterDisconnect,
                        "signal must report false once disconnected"));
    }
}

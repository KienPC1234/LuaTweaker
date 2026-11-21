package com.luatweaker.network;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** RemoteEvent:FireClient argument packing plus the reserved-channel policy. */
class NetworkRemoteEventTest {

    private static final String EVENT_NAME = "OrderTest";
    private static final String SKILL = "skill";
    private static final String FIRE_SCRIPT = """
        local ev = NetworkService:GetOrCreateRemoteEvent('OrderTest')
        ev:FireClient('00000000-0000-0000-0000-000000000000', 100, 200, 'skill', true)
        """;
    private static final String RESERVED_SYNC_CHANNEL = "LuaTweaker:SyncData";

    CapturingNetwork network;
    ILuaEngine engine;
    NetworkServiceImpl service;

    @BeforeEach
    void setup() {
        network = new CapturingNetwork();
        Platform.setNetwork(network);
        engine = LuaEngineFactory.create(false);
        service = NetworkTestFixture.service(engine);
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    private void fireFromLua() {
        engine.executeString(FIRE_SCRIPT, "TEST");
    }

    @Test
    void fireClientSendsOneFrame() {
        fireFromLua();

        assertEquals(1, network.payloads.size(), "FireClient must send exactly one frame");
    }

    @Test
    void fireClientUsesTheEventChannel() {
        fireFromLua();

        assertEquals(EVENT_NAME, network.last().channel(), "the frame must travel on the event channel");
    }

    @Test
    void fireClientPreservesOrderAndValues() {
        fireFromLua();

        ILuaValue[] args = NetworkTestFixture.decodeArgs(engine, network.last());

        assertEquals(List.of(100.0, 200.0, SKILL, true),
            List.of(args[0].asDouble(), args[1].asDouble(), args[2].asString(), args[3].asBoolean()),
            "every vararg must be packed and arrive at the packet in the fired order");
    }

    @Test
    void fireClientReportsNoLuaError() {
        fireFromLua();

        assertNull(engine.getAndClearLastExecutionError(), "the FireClient script must run without a Lua error");
    }

    @Test
    void reservedSyncChannelIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> service.GetOrCreateRemoteEvent(RESERVED_SYNC_CHANNEL),
            "scripts must not register the engine's sync channel");
    }

    @Test
    void reservedRpcChannelIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> service.GetOrCreateRemoteFunction(NetworkServiceImpl.RPC_CHANNEL),
            "scripts must not register the engine's RPC channel");
    }

    @Test
    void blankEventChannelIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> service.GetOrCreateRemoteEvent("  "),
            "a blank remote event channel must be rejected");
    }
}

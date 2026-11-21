package com.luatweaker.network;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** RpcInvokeClient addressing and validation. */
class NetworkRpcClientTest {

    private static final String ASK = "Ask";
    private static final String PLAYER_UUID = "00000000-0000-0000-0000-000000000001";
    private static final String UNKNOWN_FUNCTION = "Missing";

    CapturingNetwork network;
    ILuaEngine engine;
    NetworkServiceImpl service;
    String requestId;
    Frame frame;
    RpcEnvelope envelope;

    @BeforeEach
    void setup() {
        network = new CapturingNetwork();
        Platform.setNetwork(network);
        engine = LuaEngineFactory.create(false);
        service = NetworkTestFixture.service(engine);
        service.GetOrCreateRemoteFunction(ASK);
        requestId = service.RpcInvokeClient(ASK, PLAYER_UUID);
        frame = network.last();
        envelope = NetworkTestFixture.decodeEnvelope(frame);
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void invokeClientTargetsThePlayer() {
        assertEquals(PLAYER_UUID, frame.target(), "the clientbound request must be addressed to the target player");
    }

    @Test
    void invokeClientUsesTheRpcChannel() {
        assertEquals(NetworkServiceImpl.RPC_CHANNEL, frame.channel(),
            "the clientbound request must travel on the RPC channel");
    }

    @Test
    void invokeClientCarriesRequestId() {
        assertEquals(requestId, envelope.requestId(),
            "the clientbound envelope request id must match the id handed back to the caller");
    }

    @Test
    void invokeClientUsesInvokePhase() {
        assertEquals(RpcEnvelope.PHASE_INVOKE, envelope.phase(),
            "a clientbound request envelope must use the invoke phase");
    }

    @Test
    void invokeClientSendsNoArguments() {
        assertEquals(0, LuaPayloadCodec.decodeArgs(engine, envelope.payload()).length,
            "a call without arguments must send an empty argument payload");
    }

    @Test
    void invokeClientWithoutPlayerIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> service.RpcInvokeClient(ASK, "  "),
            "a clientbound invoke without a target player must be rejected");
    }

    @Test
    void invokeUnknownFunctionIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> service.RpcInvokeServer(UNKNOWN_FUNCTION),
            "invoking a RemoteFunction that was never registered must be rejected");
    }
}

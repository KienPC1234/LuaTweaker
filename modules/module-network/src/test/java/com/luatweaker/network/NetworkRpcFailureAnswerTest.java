package com.luatweaker.network;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Failure paths must answer with a result-phase envelope that explains the failure. */
class NetworkRpcFailureAnswerTest {

    private static final String MISSING_HANDLER = "NoHandler";
    private static final String BAD_RETURN = "BadReturn";
    private static final String MALFORMED = "Malformed";
    private static final String SERVER_SIDE = "ServerSide";
    private static final String BAD_SCRIPT = """
        local fn = NetworkService:GetOrCreateRemoteFunction('BadReturn')
        fn.OnClientInvoke = function() return function() end end
        """;
    private static final String MALFORMED_SCRIPT = """
        local fn = NetworkService:GetOrCreateRemoteFunction('Malformed')
        fn.OnClientInvoke = function() return 1 end
        """;
    private static final String SERVER_SCRIPT = """
        local fn = NetworkService:GetOrCreateRemoteFunction('ServerSide')
        fn.OnServerInvoke = function(player, a) return a end
        """;

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

    @Test
    void invokeWithoutHandlerSendsAnAnswer() {
        service.GetOrCreateRemoteFunction(MISSING_HANDLER);
        service.OnRpcInvoke("req-2", MISSING_HANDLER, "", LuaPayloadCodec.encodeArgs());

        assertEquals(1, network.payloads.size(), "an invoke without a handler must still send one answer");
    }

    @Test
    void invokeWithoutHandlerUsesResultPhase() {
        service.GetOrCreateRemoteFunction(MISSING_HANDLER);
        service.OnRpcInvoke("req-2", MISSING_HANDLER, "", LuaPayloadCodec.encodeArgs());

        assertEquals(RpcEnvelope.PHASE_RESULT, NetworkTestFixture.decodeEnvelope(network.last()).phase(),
            "a failure answer must still use the result phase");
    }

    @Test
    void invokeWithoutHandlerReportsMissingHandler() {
        service.GetOrCreateRemoteFunction(MISSING_HANDLER);
        service.OnRpcInvoke("req-2", MISSING_HANDLER, "", LuaPayloadCodec.encodeArgs());
        String error = NetworkTestFixture.decodeEnvelope(network.last()).error();

        assertTrue(error.contains("OnClientInvoke"),
            "the error must name the missing handler, got: " + error);
    }

    @Test
    void invokeWithUnserializableReturnReportsError() {
        engine.executeString(BAD_SCRIPT, "TEST");
        service.OnRpcInvoke("req-3", BAD_RETURN, "", LuaPayloadCodec.encodeArgs());
        String error = NetworkTestFixture.decodeEnvelope(network.last()).error();

        assertTrue(error.contains("cannot be sent"),
            "a function return value must fail loudly, got: " + error);
    }

    @Test
    void malformedRequestPayloadReportsError() {
        engine.executeString(MALFORMED_SCRIPT, "TEST");
        service.OnRpcInvoke("req-4", MALFORMED, "", new byte[]{9, 9, 9});
        String error = NetworkTestFixture.decodeEnvelope(network.last()).error();

        assertTrue(error.contains(MALFORMED),
            "a bad request payload must be reported, got: " + error);
    }

    @Test
    void serverInvokeWithoutPlatformPlayerReportsOnlineError() {
        engine.executeString(SERVER_SCRIPT, "TEST");
        service.OnRpcInvoke("req-5", SERVER_SIDE, NetworkTestFixture.OTHER_PLAYER_UUID,
            LuaPayloadCodec.encodeArgs(engine.wrapNumber(1)));
        String error = NetworkTestFixture.decodeEnvelope(network.last()).error();

        assertTrue(error.contains("no longer online"),
            "a missing player must be reported instead of running the handler, got: " + error);
    }
}

package com.luatweaker.network;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The client-side OnClientInvoke handler must answer a server invoke over the wire. */
class NetworkRpcClientAnswerTest {

    private static final String ADD = "Add";
    private static final String REQUEST_ID = "req-1";
    private static final String HANDLER_SCRIPT = """
        local fn = NetworkService:GetOrCreateRemoteFunction('Add')
        fn.OnClientInvoke = function(a, b) return a + b end
        """;

    CapturingNetwork network;
    ILuaEngine engine;
    NetworkServiceImpl service;
    RpcEnvelope answer;

    @BeforeEach
    void setup() {
        network = new CapturingNetwork();
        Platform.setNetwork(network);
        engine = LuaEngineFactory.create(false);
        service = NetworkTestFixture.service(engine);
        engine.executeString(HANDLER_SCRIPT, "TEST");
        service.OnRpcInvoke(REQUEST_ID, ADD, "",
            LuaPayloadCodec.encodeArgs(engine.wrapNumber(2), engine.wrapNumber(3)));
        answer = NetworkTestFixture.decodeEnvelope(network.last());
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void clientInvokeSendsOneAnswer() {
        assertEquals(1, network.payloads.size(), "the client must send exactly one answer frame");
    }

    @Test
    void clientAnswerCarriesRequestId() {
        assertEquals(REQUEST_ID, answer.requestId(), "the answer must echo the originating request id");
    }

    @Test
    void clientAnswerUsesResultPhase() {
        assertEquals(RpcEnvelope.PHASE_RESULT, answer.phase(), "an answer must use the result phase");
    }

    @Test
    void clientAnswerCarriesFunctionName() {
        assertEquals(ADD, answer.functionName(), "the answer must name the invoked RemoteFunction");
    }

    @Test
    void clientAnswerHasEmptyError() {
        assertEquals("", answer.error(), "a successful answer must not carry an error message");
    }

    @Test
    void clientAnswerCarriesHandlerResult() {
        ILuaValue[] result = LuaPayloadCodec.decodeArgs(engine, answer.payload());

        assertEquals(5.0, result[0].asDouble(), "the answer payload must carry the handler's return value");
    }
}

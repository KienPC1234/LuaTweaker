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

/** RpcInvokeServer must emit a well-formed invoke envelope and start a pending request. */
class NetworkRpcInvokeTest {

    private static final String ECHO = "Echo";
    private static final String STATUS_KEY = "Status";

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
        service.GetOrCreateRemoteFunction(ECHO);
        requestId = service.RpcInvokeServer(ECHO, engine.wrapNumber(1), engine.wrapString("hi"));
        frame = network.last();
        envelope = NetworkTestFixture.decodeEnvelope(frame);
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void invokeServerSendsSingleRequest() {
        assertEquals(1, network.payloads.size(), "RpcInvokeServer must send exactly one request frame");
    }

    @Test
    void invokeServerUsesTheRpcChannel() {
        assertEquals(NetworkServiceImpl.RPC_CHANNEL, frame.channel(),
            "the request frame must travel on the RPC channel");
    }

    @Test
    void invokeServerCarriesRequestId() {
        assertEquals(requestId, envelope.requestId(),
            "the envelope request id must match the id handed back to the caller");
    }

    @Test
    void invokeServerUsesInvokePhase() {
        assertEquals(RpcEnvelope.PHASE_INVOKE, envelope.phase(), "a request envelope must use the invoke phase");
    }

    @Test
    void invokeServerCarriesFunctionName() {
        assertEquals(ECHO, envelope.functionName(), "the envelope must name the invoked RemoteFunction");
    }

    @Test
    void invokeServerHasEmptyError() {
        assertEquals("", envelope.error(), "a request envelope must not carry an error message");
    }

    @Test
    void invokeServerCarriesArguments() {
        ILuaValue[] args = LuaPayloadCodec.decodeArgs(engine, envelope.payload());

        assertEquals(List.of(1.0, "hi"), List.of(args[0].asDouble(), args[1].asString()),
            "the invoke payload must carry every argument in order");
    }

    @Test
    void invokeServerStartsPending() {
        assertEquals("pending", service.RpcPoll(requestId).rawget(STATUS_KEY).asString(),
            "a fresh request must poll as pending until a result arrives");
    }
}

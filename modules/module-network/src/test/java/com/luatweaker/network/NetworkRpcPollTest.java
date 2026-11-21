package com.luatweaker.network;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** RpcPoll state machine: pending, done, one-shot consumption, errors and cancellation. */
class NetworkRpcPollTest {

    private static final String ECHO = "Echo";
    private static final String STATUS_KEY = "Status";
    private static final String VALUE_KEY = "Value";
    private static final String ERROR_KEY = "Error";
    private static final String HANDLER_ERROR = "handler failed";
    private static final String UNKNOWN_REQUEST = "not-a-request";
    private static final String ERROR_STATUS = "error";

    CapturingNetwork network;
    ILuaEngine engine;
    NetworkServiceImpl service;
    String requestId;

    @BeforeEach
    void setup() {
        network = new CapturingNetwork();
        Platform.setNetwork(network);
        engine = LuaEngineFactory.create(false);
        service = NetworkTestFixture.service(engine);
        service.GetOrCreateRemoteFunction(ECHO);
        requestId = service.RpcInvokeServer(ECHO);
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void pollReportsPending() {
        assertEquals("pending", service.RpcPoll(requestId).rawget(STATUS_KEY).asString(),
            "a request without a result must poll as pending");
    }

    @Test
    void pollReportsDoneAfterResult() {
        service.OnRpcResult(requestId, LuaPayloadCodec.encodeArgs(engine.wrapNumber(42)), "");

        assertEquals("done", service.RpcPoll(requestId).rawget(STATUS_KEY).asString(),
            "a request with a result must poll as done");
    }

    @Test
    void pollReturnsResultValue() {
        service.OnRpcResult(requestId, LuaPayloadCodec.encodeArgs(engine.wrapNumber(42)), "");

        assertEquals(42.0, service.RpcPoll(requestId).rawget(VALUE_KEY).asDouble(),
            "the poll must surface the handler's return value");
    }

    @Test
    void pollConsumesCompletedRequest() {
        service.OnRpcResult(requestId, LuaPayloadCodec.encodeArgs(engine.wrapNumber(42)), "");
        service.RpcPoll(requestId);

        assertEquals(ERROR_STATUS, service.RpcPoll(requestId).rawget(STATUS_KEY).asString(),
            "a completed request must only be readable once");
    }

    @Test
    void pollReportsHandlerErrorStatus() {
        service.OnRpcResult(requestId, LuaPayloadCodec.encodeArgs(), HANDLER_ERROR);

        assertEquals(ERROR_STATUS, service.RpcPoll(requestId).rawget(STATUS_KEY).asString(),
            "a handler failure must poll with the error status");
    }

    @Test
    void pollReportsHandlerErrorText() {
        service.OnRpcResult(requestId, LuaPayloadCodec.encodeArgs(), HANDLER_ERROR);

        assertEquals(HANDLER_ERROR, service.RpcPoll(requestId).rawget(ERROR_KEY).asString(),
            "the handler's error message must be surfaced through the poll");
    }

    @Test
    void unknownResponseIsIgnored() {
        service.OnRpcResult(UNKNOWN_REQUEST, LuaPayloadCodec.encodeArgs(engine.wrapNumber(1)), "");

        assertEquals(ERROR_STATUS, service.RpcPoll(UNKNOWN_REQUEST).rawget(STATUS_KEY).asString(),
            "a response for an unknown request must not create a completed entry");
    }

    @Test
    void cancelledRequestStopsWaiting() {
        service.RpcCancel(requestId);

        assertEquals(ERROR_STATUS, service.RpcPoll(requestId).rawget(STATUS_KEY).asString(),
            "a cancelled request must stop polling as pending");
    }
}

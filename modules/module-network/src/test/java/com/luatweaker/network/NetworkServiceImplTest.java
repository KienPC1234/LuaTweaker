package com.luatweaker.network;

import com.luatweaker.api.pal.IPlatformNetwork;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NetworkServiceImplTest {

    /** One message the service handed to the transport, matching the real byte API. */
    private record Frame(String target, String channel, byte[] data) {
    }

    private static final class CapturingNetwork implements IPlatformNetwork {
        private final List<Frame> payloads = new ArrayList<>();

        @Override
        public void sendPayloadBytes(String playerUuid, String channelName, byte[] data) {
            payloads.add(new Frame(playerUuid, channelName, data));
        }

        @Override
        public void broadcastPayloadBytes(String channelName, byte[] data) {
            payloads.add(new Frame("", channelName, data));
        }

        @Override
        public void sendPayloadBytesToServer(String channelName, byte[] data) {
            payloads.add(new Frame("", channelName, data));
        }
    }

    private CapturingNetwork network;

    @BeforeEach
    void setup() {
        network = new CapturingNetwork();
        Platform.setNetwork(network);
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    private NetworkServiceImpl newService(ILuaEngine engine) {
        NetworkServiceImpl service = new NetworkServiceImpl(engine);
        NetworkLuaBinding.registerBindings(engine, service);
        return service;
    }

    private static ILuaValue[] decodeArgs(ILuaEngine engine, Frame frame) {
        return LuaPayloadCodec.decodeArgs(engine, frame.data());
    }

    private static RpcEnvelope decodeEnvelope(Frame frame) {
        return RpcEnvelope.decode(frame.data());
    }

    @Test
    void remoteEventFireClientPreservesArgumentOrder() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        newService(engine);

        // Full Lua path: RemoteEvent:FireClient packs its varargs into a positional table; the
        // order MUST survive the Java unpack (regression for the HashMap-scrambling bug).
        engine.executeString("local ev = NetworkService:GetOrCreateRemoteEvent('OrderTest')\n" +
                "ev:FireClient('00000000-0000-0000-0000-000000000000', 100, 200, 'skill', true)\n", "TEST");

        assertNull(engine.getAndClearLastExecutionError());
        assertEquals(1, network.payloads.size());
        Frame frame = network.payloads.get(0);
        assertEquals("OrderTest", frame.channel());
        ILuaValue[] args = decodeArgs(engine, frame);
        assertEquals(4, args.length);
        assertEquals(100.0, args[0].asDouble());
        assertEquals(200.0, args[1].asDouble());
        assertEquals("skill", args[2].asString());
        assertTrue(args[3].asBoolean(), "positional args must arrive at the packet in the fired order");
    }

    @Test
    void invokeServerSendsRequestAndWaitsForTheResult() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        service.GetOrCreateRemoteFunction("Echo");

        String requestId = service.RpcInvokeServer("Echo",
                new ILuaValue[]{engine.wrapNumber(1), engine.wrapString("hi")});

        assertEquals(1, network.payloads.size(), "exactly one request frame");
        Frame frame = network.payloads.get(0);
        assertEquals(NetworkServiceImpl.RPC_CHANNEL, frame.channel());
        RpcEnvelope envelope = decodeEnvelope(frame);
        assertEquals(requestId, envelope.requestId());
        assertEquals(RpcEnvelope.PHASE_INVOKE, envelope.phase());
        assertEquals("Echo", envelope.functionName());
        assertEquals("", envelope.error());
        ILuaValue[] args = LuaPayloadCodec.decodeArgs(engine, envelope.payload());
        assertEquals(1.0, args[0].asDouble());
        assertEquals("hi", args[1].asString());

        assertEquals("pending", service.RpcPoll(requestId).rawget("Status").asString());

        service.OnRpcResult(requestId, LuaPayloadCodec.encodeArgs(
                new ILuaValue[]{engine.wrapNumber(42)}), "");
        ILuaTable done = service.RpcPoll(requestId);
        assertEquals("done", done.rawget("Status").asString());
        assertEquals(42.0, done.rawget("Value").asDouble());

        // One-shot: a completed request is not readable twice.
        assertEquals("error", service.RpcPoll(requestId).rawget("Status").asString());
    }

    @Test
    void invokeClientSendsRequestToThePlayer() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        service.GetOrCreateRemoteFunction("Ask");
        String uuid = "00000000-0000-0000-0000-000000000001";

        String requestId = service.RpcInvokeClient("Ask", uuid, new ILuaValue[0]);

        assertEquals(1, network.payloads.size());
        Frame frame = network.payloads.get(0);
        assertEquals(uuid, frame.target());
        assertEquals(NetworkServiceImpl.RPC_CHANNEL, frame.channel());
        RpcEnvelope envelope = decodeEnvelope(frame);
        assertEquals(requestId, envelope.requestId());
        assertEquals(RpcEnvelope.PHASE_INVOKE, envelope.phase());
        assertEquals(0, LuaPayloadCodec.decodeArgs(engine, envelope.payload()).length);
    }

    @Test
    void invokeClientWithoutPlayerIsRejected() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        service.GetOrCreateRemoteFunction("Ask");

        assertThrows(IllegalArgumentException.class,
                () -> service.RpcInvokeClient("Ask", "  ", new ILuaValue[0]));
        assertTrue(network.payloads.isEmpty(), "nothing may be sent without a target");
    }

    @Test
    void invokeUnknownFunctionIsRejected() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);

        assertThrows(IllegalArgumentException.class,
                () -> service.RpcInvokeServer("Missing", new ILuaValue[0]));
        assertTrue(network.payloads.isEmpty());
    }

    @Test
    void remoteErrorSurfacesThroughPoll() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        service.GetOrCreateRemoteFunction("Boom");

        String requestId = service.RpcInvokeServer("Boom", new ILuaValue[0]);
        service.OnRpcResult(requestId, LuaPayloadCodec.encodeArgs(new ILuaValue[0]), "handler failed");

        ILuaTable state = service.RpcPoll(requestId);
        assertEquals("error", state.rawget("Status").asString());
        assertEquals("handler failed", state.rawget("Error").asString());
    }

    @Test
    void unknownResponseIsIgnored() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);

        service.OnRpcResult("not-a-request",
                LuaPayloadCodec.encodeArgs(new ILuaValue[]{engine.wrapNumber(1)}), "");
        assertEquals("error", service.RpcPoll("not-a-request").rawget("Status").asString());
    }

    @Test
    void cancelledRequestStopsWaiting() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        service.GetOrCreateRemoteFunction("Echo");

        String requestId = service.RpcInvokeServer("Echo", new ILuaValue[0]);
        service.RpcCancel(requestId);

        assertEquals("error", service.RpcPoll(requestId).rawget("Status").asString());
    }

    @Test
    void clientInvokeHandlerAnswersOverTheWire() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        engine.executeString(
                "local fn = NetworkService:GetOrCreateRemoteFunction('Add')\n" +
                "fn.OnClientInvoke = function(a, b) return a + b end\n",
                "TEST");
        assertNull(engine.getAndClearLastExecutionError());

        service.OnRpcInvoke("req-1", "Add", "", LuaPayloadCodec.encodeArgs(
                new ILuaValue[]{engine.wrapNumber(2), engine.wrapNumber(3)}));

        assertEquals(1, network.payloads.size(), "the client must answer over the network");
        RpcEnvelope answer = decodeEnvelope(network.payloads.get(0));
        assertEquals("req-1", answer.requestId());
        assertEquals(RpcEnvelope.PHASE_RESULT, answer.phase());
        assertEquals("Add", answer.functionName());
        assertEquals("", answer.error());
        ILuaValue[] result = LuaPayloadCodec.decodeArgs(engine, answer.payload());
        assertEquals(5.0, result[0].asDouble());
    }

    @Test
    void invokeWithoutHandlerAnswersWithAnError() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        service.GetOrCreateRemoteFunction("NoHandler");

        service.OnRpcInvoke("req-2", "NoHandler", "", LuaPayloadCodec.encodeArgs(new ILuaValue[0]));

        assertEquals(1, network.payloads.size());
        RpcEnvelope answer = decodeEnvelope(network.payloads.get(0));
        assertEquals(RpcEnvelope.PHASE_RESULT, answer.phase());
        assertTrue(answer.error().contains("OnClientInvoke"),
                "error must name the missing handler: " + answer.error());
    }

    @Test
    void invokeWithUnserializableReturnAnswersWithAnError() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        engine.executeString(
                "local fn = NetworkService:GetOrCreateRemoteFunction('BadReturn')\n" +
                "fn.OnClientInvoke = function() return function() end end\n",
                "TEST");

        service.OnRpcInvoke("req-3", "BadReturn", "", LuaPayloadCodec.encodeArgs(new ILuaValue[0]));

        assertEquals(1, network.payloads.size());
        RpcEnvelope answer = decodeEnvelope(network.payloads.get(0));
        assertTrue(answer.error().contains("cannot be sent"),
                "a function return value must fail loudly: " + answer.error());
    }

    @Test
    void malformedRequestPayloadAnswersWithAnError() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        engine.executeString(
                "local fn = NetworkService:GetOrCreateRemoteFunction('Malformed')\n" +
                "fn.OnClientInvoke = function() return 1 end\n",
                "TEST");

        service.OnRpcInvoke("req-4", "Malformed", "", new byte[]{9, 9, 9});

        assertEquals(1, network.payloads.size());
        RpcEnvelope answer = decodeEnvelope(network.payloads.get(0));
        assertTrue(answer.error().contains("Malformed"),
                "bad payload must be reported: " + answer.error());
    }

    @Test
    void serverInvokeWithoutPlatformPlayerAnswersWithAnError() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        engine.executeString(
                "local fn = NetworkService:GetOrCreateRemoteFunction('ServerSide')\n" +
                "fn.OnServerInvoke = function(player, a) return a end\n",
                "TEST");

        // Platform.isInitialized() is false in unit tests, so the player cannot be resolved.
        service.OnRpcInvoke("req-5", "ServerSide", "00000000-0000-0000-0000-000000000002",
                LuaPayloadCodec.encodeArgs(new ILuaValue[]{engine.wrapNumber(1)}));

        assertEquals(1, network.payloads.size());
        RpcEnvelope answer = decodeEnvelope(network.payloads.get(0));
        assertTrue(answer.error().contains("no longer online"),
                "missing player must be reported instead of running the handler: " + answer.error());
    }

    @Test
    void invokeFloodFromOnePlayerIsRateLimited() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        engine.executeString(
                "local fn = NetworkService:GetOrCreateRemoteFunction('RateLimited')\n" +
                "fn.OnServerInvoke = function(player) return 1 end\n",
                "TEST");
        byte[] empty = LuaPayloadCodec.encodeArgs(new ILuaValue[0]);

        for (int i = 0; i < 60; i++) {
            service.OnRpcInvoke("flood-" + i, "RateLimited", "player-a", empty);
        }
        service.OnRpcInvoke("flood-99", "RateLimited", "player-a", empty);

        RpcEnvelope last = decodeEnvelope(network.payloads.get(network.payloads.size() - 1));
        assertTrue(last.error().contains("Too many RemoteFunction requests"),
                "the burst budget must reject the flood: " + last.error());

        // Another player keeps its own budget.
        service.OnRpcInvoke("flood-other", "RateLimited", "player-b", empty);
        RpcEnvelope other = decodeEnvelope(network.payloads.get(network.payloads.size() - 1));
        assertFalse(other.error().contains("Too many"), "budgets must be per player: " + other.error());
    }

    @Test
    void outboundRemoteEventFloodIsRateLimited() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        ILuaValue[] args = new ILuaValue[]{engine.wrapNumber(1)};

        for (int i = 0; i < 10_000; i++) {
            service.FireServer("Spam", args);
        }

        assertTrue(network.payloads.size() >= NetworkServiceImpl.EVENT_BURST,
                "the burst must go out before the limit, sent " + network.payloads.size());
        assertTrue(network.payloads.size() < 1_000,
                "a 10k loop must be limited, sent " + network.payloads.size());
    }

    @Test
    void reservedEngineChannelsCannotBeRegisteredByScripts() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);

        assertThrows(IllegalArgumentException.class,
                () -> service.GetOrCreateRemoteEvent("LuaTweaker:SyncData"));
        assertThrows(IllegalArgumentException.class,
                () -> service.GetOrCreateRemoteFunction(NetworkServiceImpl.RPC_CHANNEL));
        assertThrows(IllegalArgumentException.class,
                () -> service.GetOrCreateRemoteEvent("  "));
    }

    @Test
    void remoteEventFloodFromOnePlayerIsRateLimited() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        engine.executeString(
                "local ev = NetworkService:GetOrCreateRemoteEvent('Spam')\n" +
                "_G._spamCount = 0\n" +
                "ev.OnServerEvent:Connect(function(player) _G._spamCount = _G._spamCount + 1 end)\n",
                "TEST");
        byte[] empty = LuaPayloadCodec.encodeArgs(new ILuaValue[0]);

        for (int i = 0; i < 10_000; i++) {
            service.OnClientFired("Spam", "player-a", empty);
        }

        double fired = engine.getGlobalEnvironment().rawget("_spamCount").asDouble();
        assertTrue(fired >= NetworkServiceImpl.EVENT_BURST,
                "the burst must be delivered before the limit kicks in, got " + fired);
        assertTrue(fired < 1_000, "a 10k flood must be rate-limited, delivered " + fired);

        // Another player keeps its own budget.
        service.OnClientFired("Spam", "player-b", empty);
        double afterOther = engine.getGlobalEnvironment().rawget("_spamCount").asDouble();
        assertEquals(fired + 1, afterOther, "budgets must be per player");
    }
}

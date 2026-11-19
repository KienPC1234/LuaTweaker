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

    /** Records every packet the service hands to the platform transport. */
    private static final class CapturingNetwork implements IPlatformNetwork {
        private final List<String[]> payloads = new ArrayList<>();
        private final List<String[]> rpcToPlayer = new ArrayList<>();
        private final List<String[]> rpcToServer = new ArrayList<>();

        @Override
        public void sendPayloadPacket(String playerUuid, String channelName, String dataJson) {
            payloads.add(new String[]{playerUuid, channelName, dataJson});
        }

        @Override
        public void broadcastPayloadPacket(String channelName, String dataJson) {
            payloads.add(new String[]{"", channelName, dataJson});
        }

        @Override
        public void sendPayloadPacketToServer(String channelName, String dataJson) {
            payloads.add(new String[]{"", channelName, dataJson});
        }

        @Override
        public void sendRpcPacket(String playerUuid, String requestId, String phase,
                                  String functionName, String dataJson, String error) {
            rpcToPlayer.add(new String[]{playerUuid, requestId, phase, functionName, dataJson, error});
        }

        @Override
        public void sendRpcPacketToServer(String requestId, String phase,
                                         String functionName, String dataJson, String error) {
            rpcToServer.add(new String[]{"", requestId, phase, functionName, dataJson, error});
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

    @Test
    void remoteEventFireClientPreservesArgumentOrder() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl networkService = newService(engine);

        // Full Lua path: RemoteEvent:FireClient packs its varargs into a positional table; the
        // order MUST survive the Java unpack (regression for the HashMap-scrambling bug).
        engine.executeString("local ev = NetworkService:GetOrCreateRemoteEvent('OrderTest')\n" +
                "ev:FireClient('00000000-0000-0000-0000-000000000000', 100, 200, 'skill', true)\n", "TEST");

        assertNull(engine.getAndClearLastExecutionError());
        assertEquals(1, network.payloads.size());
        assertEquals("OrderTest", network.payloads.get(0)[1]);
        assertEquals("[100,200,\"skill\",true]", network.payloads.get(0)[2],
                "positional args must arrive at the packet in the fired order");
        assertNotNull(networkService);
    }

    @Test
    void invokeServerSendsRequestAndWaitsForTheResult() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        service.GetOrCreateRemoteFunction("Echo");

        String requestId = service.RpcInvokeServer("Echo",
                new ILuaValue[]{engine.wrapNumber(1), engine.wrapString("hi")});

        assertEquals(1, network.rpcToServer.size(), "exactly one request packet");
        String[] packet = network.rpcToServer.get(0);
        assertEquals(requestId, packet[1]);
        assertEquals("invoke", packet[2]);
        assertEquals("Echo", packet[3]);
        assertEquals("[1.0,\"hi\"]", packet[4]);
        assertEquals("", packet[5]);

        assertEquals("pending", service.RpcPoll(requestId).rawget("Status").asString());

        service.OnRpcResult(requestId, "[42.0]", "");
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

        assertEquals(1, network.rpcToPlayer.size());
        String[] packet = network.rpcToPlayer.get(0);
        assertEquals(uuid, packet[0]);
        assertEquals(requestId, packet[1]);
        assertEquals("invoke", packet[2]);
        assertEquals("Ask", packet[3]);
        assertEquals("[]", packet[4]);
    }

    @Test
    void invokeClientWithoutPlayerIsRejected() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        service.GetOrCreateRemoteFunction("Ask");

        assertThrows(IllegalArgumentException.class,
                () -> service.RpcInvokeClient("Ask", "  ", new ILuaValue[0]));
        assertTrue(network.rpcToPlayer.isEmpty(), "nothing may be sent without a target");
    }

    @Test
    void invokeUnknownFunctionIsRejected() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);

        assertThrows(IllegalArgumentException.class,
                () -> service.RpcInvokeServer("Missing", new ILuaValue[0]));
        assertTrue(network.rpcToServer.isEmpty());
    }

    @Test
    void remoteErrorSurfacesThroughPoll() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        service.GetOrCreateRemoteFunction("Boom");

        String requestId = service.RpcInvokeServer("Boom", new ILuaValue[0]);
        service.OnRpcResult(requestId, "", "handler failed");

        ILuaTable state = service.RpcPoll(requestId);
        assertEquals("error", state.rawget("Status").asString());
        assertEquals("handler failed", state.rawget("Error").asString());
    }

    @Test
    void unknownResponseIsIgnored() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);

        service.OnRpcResult("not-a-request", "[1.0]", "");
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

        service.OnRpcInvoke("req-1", "Add", "", "[2.0,3.0]");

        assertEquals(1, network.rpcToServer.size(), "the client must answer over the network");
        String[] answer = network.rpcToServer.get(0);
        assertEquals("req-1", answer[1]);
        assertEquals("result", answer[2]);
        assertEquals("Add", answer[3]);
        assertEquals("[5.0]", answer[4]);
        assertEquals("", answer[5]);
    }

    @Test
    void invokeWithoutHandlerAnswersWithAnError() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        service.GetOrCreateRemoteFunction("NoHandler");

        service.OnRpcInvoke("req-2", "NoHandler", "", "[]");

        assertEquals(1, network.rpcToServer.size());
        String[] answer = network.rpcToServer.get(0);
        assertEquals("result", answer[2]);
        assertTrue(answer[5].contains("OnClientInvoke"),
                "error must name the missing handler: " + answer[5]);
    }

    @Test
    void invokeWithUnserializableReturnAnswersWithAnError() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        engine.executeString(
                "local fn = NetworkService:GetOrCreateRemoteFunction('BadReturn')\n" +
                "fn.OnClientInvoke = function() return function() end end\n",
                "TEST");

        service.OnRpcInvoke("req-3", "BadReturn", "", "[]");

        assertEquals(1, network.rpcToServer.size());
        assertTrue(network.rpcToServer.get(0)[5].contains("cannot be sent"),
                "a function return value must fail loudly: " + network.rpcToServer.get(0)[5]);
    }

    @Test
    void malformedRequestPayloadAnswersWithAnError() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        engine.executeString(
                "local fn = NetworkService:GetOrCreateRemoteFunction('Malformed')\n" +
                "fn.OnClientInvoke = function() return 1 end\n",
                "TEST");

        service.OnRpcInvoke("req-4", "Malformed", "", "{not json");

        assertEquals(1, network.rpcToServer.size());
        assertTrue(network.rpcToServer.get(0)[5].contains("Malformed"),
                "bad payload must be reported: " + network.rpcToServer.get(0)[5]);
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
        service.OnRpcInvoke("req-5", "ServerSide", "00000000-0000-0000-0000-000000000002", "[1.0]");

        assertEquals(1, network.rpcToPlayer.size());
        assertTrue(network.rpcToPlayer.get(0)[5].contains("no longer online"),
                "missing player must be reported instead of running the handler: "
                        + network.rpcToPlayer.get(0)[5]);
    }

    @Test
    void invokeFloodFromOnePlayerIsRateLimited() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = newService(engine);
        engine.executeString(
                "local fn = NetworkService:GetOrCreateRemoteFunction('RateLimited')\n" +
                "fn.OnServerInvoke = function(player) return 1 end\n",
                "TEST");

        for (int i = 0; i < 60; i++) {
            service.OnRpcInvoke("flood-" + i, "RateLimited", "player-a", "[]");
        }
        service.OnRpcInvoke("flood-99", "RateLimited", "player-a", "[]");

        String lastError = network.rpcToPlayer.get(network.rpcToPlayer.size() - 1)[5];
        assertTrue(lastError.contains("Too many RemoteFunction requests"),
                "the burst budget must reject the flood: " + lastError);

        // Another player keeps its own budget.
        service.OnRpcInvoke("flood-other", "RateLimited", "player-b", "[]");
        String otherError = network.rpcToPlayer.get(network.rpcToPlayer.size() - 1)[5];
        assertFalse(otherError.contains("Too many"), "budgets must be per player: " + otherError);
    }
}

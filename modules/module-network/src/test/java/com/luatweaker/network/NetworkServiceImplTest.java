package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;

import com.luatweaker.api.pal.IPlatformNetwork;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NetworkServiceImplTest {

    @BeforeAll
    static void setup() {
        Platform.setNetwork(new IPlatformNetwork() {
            @Override
            public void sendPayloadPacket(String playerUuid, String channelName, String dataJson) {}

            @Override
            public void broadcastPayloadPacket(String channelName, String dataJson) {}

            @Override
            public void sendPayloadPacketToServer(String channelName, String dataJson) {}
        });
    }

    @Test
    void testGetOrCreateRemoteEvent() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl networkService = new NetworkServiceImpl(engine);

        ILuaTable remoteEvent = networkService.GetOrCreateRemoteEvent("TestEvent");
        assertNotNull(remoteEvent);
        assertEquals("TestEvent", remoteEvent.rawget("Name").asString());
    }

    @Test
    void testRemoteFunctionInvokeServer() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl networkService = new NetworkServiceImpl(engine);
        NetworkLuaBinding.registerBindings(engine, networkService);

        ILuaTable remoteFunc = networkService.GetOrCreateRemoteFunction("TestFunc");
        assertNotNull(remoteFunc);

        // Bind callback function
        engine.executeString("local net = Mod:GetService('NetworkService')\n" +
                "local fn = net:GetOrCreateRemoteFunction('TestFunc')\n" +
                "fn.OnServerInvoke = function(a, b)\n" +
                "    return a + b\n" +
                "end\n", "TEST");

        ILuaValue res = networkService.InvokeServer("TestFunc", new ILuaValue[]{engine.wrapNumber(10), engine.wrapNumber(20)});
        assertEquals(30.0, res.asDouble());
    }

    @Test
    void remoteFunctionLuaWrapperCanInvokeServerCallback() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl networkService = new NetworkServiceImpl(engine);
        NetworkLuaBinding.registerBindings(engine, networkService);

        engine.executeString("local net = Mod:GetService('NetworkService')\n" +
                "local fn = net:GetOrCreateRemoteFunction('LuaFunc')\n" +
                "fn.OnServerInvoke = function(a, b)\n" +
                "    return a + b\n" +
                "end\n" +
                "invokeResult = fn:InvokeServer(10, 20)\n", "TEST");

        assertNull(engine.getAndClearLastExecutionError());
        assertEquals(30.0, engine.getGlobalEnvironment().rawget("invokeResult").asDouble());
    }

    @Test
    void remoteFunctionLuaWrapperCanInvokeClientCallback() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl networkService = new NetworkServiceImpl(engine);
        NetworkLuaBinding.registerBindings(engine, networkService);

        engine.executeString("local fn = NetworkService:GetOrCreateRemoteFunction('ClientFunc')\n" +
                "fn.OnClientInvoke = function(a, b)\n" +
                "    return a .. ':' .. b\n" +
                "end\n" +
                "clientInvokeResult = fn:InvokeClient('00000000-0000-0000-0000-000000000000', 'x', 7)\n", "TEST");

        assertNull(engine.getAndClearLastExecutionError());
        assertEquals("x:7", engine.getGlobalEnvironment().rawget("clientInvokeResult").asString());
    }

    @Test
    void remoteEventFireClientPreservesArgumentOrder() {
        List<String[]> captured = new java.util.ArrayList<>();
        Platform.setNetwork(new IPlatformNetwork() {
            @Override
            public void sendPayloadPacket(String playerUuid, String channelName, String dataJson) {
                captured.add(new String[]{channelName, dataJson});
            }

            @Override
            public void broadcastPayloadPacket(String channelName, String dataJson) {}

            @Override
            public void sendPayloadPacketToServer(String channelName, String dataJson) {}
        });

        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl networkService = new NetworkServiceImpl(engine);
        NetworkLuaBinding.registerBindings(engine, networkService);

        // Full Lua path: RemoteEvent:FireClient packs its varargs into a positional
        // table; the order MUST survive the Java unpack (regression for the
        // HashMap-scrambling asMap() bug that reordered mana/maxMana/skill...).
        engine.executeString("local ev = NetworkService:GetOrCreateRemoteEvent('OrderTest')\n" +
                "ev:FireClient('00000000-0000-0000-0000-000000000000', 100, 200, 'skill', true)\n", "TEST");

        assertEquals(1, captured.size());
        assertEquals("OrderTest", captured.get(0)[0]);
        assertEquals("[100,200,\"skill\",true]", captured.get(0)[1],
                "positional args must arrive at the packet in the fired order");
    }
}

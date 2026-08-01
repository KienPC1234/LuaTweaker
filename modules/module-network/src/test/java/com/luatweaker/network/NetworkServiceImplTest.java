package com.luatweaker.network;

import com.luatweaker.api.pal.IPlatformNetwork;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
        CobaltLuaEngine engine = new CobaltLuaEngine(false);
        NetworkServiceImpl networkService = new NetworkServiceImpl(engine);

        ILuaTable remoteEvent = networkService.GetOrCreateRemoteEvent("TestEvent");
        assertNotNull(remoteEvent);
        assertEquals("TestEvent", remoteEvent.rawget("Name").asString());
    }

    @Test
    void testRemoteFunctionInvokeServer() {
        CobaltLuaEngine engine = new CobaltLuaEngine(false);
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
}

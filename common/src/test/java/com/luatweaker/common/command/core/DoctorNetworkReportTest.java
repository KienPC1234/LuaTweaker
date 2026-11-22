package com.luatweaker.common.command.core;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.mod.LuaModContext;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.network.NetworkLuaBinding;
import com.luatweaker.network.NetworkServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DoctorNetworkReportTest {

    private static final String CHANNEL = "ShopBuy";
    private static final String OWNER = "my_shop";

    private Object previousService;

    @BeforeEach
    void setup() {
        previousService = LuaServiceRegistry.get("NetworkServiceImpl");
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl service = new NetworkServiceImpl(engine);
        NetworkLuaBinding.registerBindings(engine, service);
        LuaServiceRegistry.register("NetworkServiceImpl", service);
        LuaModContext.pushLoadingMod(OWNER);
        try {
            service.GetOrCreateRemoteEvent(CHANNEL);
        } finally {
            LuaModContext.popLoadingMod();
        }
        engine.executeString(
                "NetworkService:GetOrCreateRemoteEvent('" + CHANNEL + "'):SetInboundEnabled(false)",
                "doctor_network");
    }

    @AfterEach
    void restoreRegistry() {
        if (previousService != null) {
            LuaServiceRegistry.register("NetworkServiceImpl", previousService);
        }
    }

    @Test
    void reportListsChannelOwnerAndInboundFlag() {
        MockCommandSender sender = new MockCommandSender();
        new DoctorCommand().execute(sender, new String[0]);

        assertTrue(sender.messages.stream().anyMatch(line ->
                        line.contains(CHANNEL) && line.contains(OWNER) && line.contains("inbound blocked")),
                "doctor must attribute the channel to its mod and show the inbound switch, got: "
                        + sender.messages);
    }
}

package com.luatweaker.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClientServiceImplTest {

    private ILuaEngine engine;
    private ClientServiceImpl clientService;

    @BeforeEach
    public void setUp() {
        engine = new CobaltLuaEngine(true);
        clientService = new ClientServiceImpl();
        ClientLuaBinding.registerBindings(engine, clientService);
    }

    @Test
    public void testClientServiceBindings() {
        assertDoesNotThrow(() -> {
            engine.executeString(
                "local camera = Mod:GetService('Camera')\n" +
                "assert(camera ~= nil, 'Camera service should exist')\n" +
                "camera:Shake(1.5, 0.5)\n" +
                "local effects = Mod:GetService('ClientEffects')\n" +
                "assert(effects ~= nil, 'ClientEffects service should exist')\n" +
                "effects:PlaySound('minecraft:block.note_block.harp', 1.0, 1.0)",
                "ClientTest"
            );
        });
    }
}

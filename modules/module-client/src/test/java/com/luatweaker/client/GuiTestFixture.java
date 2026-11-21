package com.luatweaker.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared fixture for the {@code GuiService} binding tests: a headless engine with all client
 * bindings registered against a recording {@link MockGuiService}.
 */
class GuiTestFixture {

    protected ILuaEngine engine;
    protected MockGuiService gui;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
        gui = new MockGuiService();
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl(),
                new KeyBindServiceImpl(), gui);
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    protected void run(String lua, String chunkName) {
        engine.executeStringOrThrow(lua, chunkName);
    }
}

package com.luatweaker.client;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared fixture for the {@code ClientLuaBinding} service tests: a recording PAL client installed
 * in the process-wide {@link Platform}, a client engine and the bound services.
 */
class ClientServiceFixture {

    protected ILuaEngine engine;
    protected ClientServiceImpl clientService;
    protected RecordingClient client;

    @BeforeEach
    void setUp() {
        // The client bindings delegate to the PAL; without a helper every call fails.
        client = new RecordingClient();
        Platform.setClient(client);
        engine = LuaEngineFactory.create(true);
        clientService = new ClientServiceImpl();
        ClientLuaBinding.registerBindings(engine, clientService);
    }

    @AfterEach
    void tearDown() {
        // Platform is process-wide static state; leaking a fake client would change
        // every later client test in this JVM.
        Platform.setClient(null);
    }
}

package com.luatweaker.client;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Pins {@code RunService}: IsClient/IsServer must follow the platform's process flags instead of
 * returning constants, otherwise scripts cannot tell a dedicated server from a client.
 */
class RunServiceTest {

    @Test
    void runServiceRoleFollowsThePlatform() {
        assertDoesNotThrow(RunServiceTest::checkEveryProcessRole,
                "RunService must report the role of single-player, remote client and dedicated "
                        + "server processes without error");
    }

    /** Runs the three process shapes and lets the Lua assertions fail the test. */
    private static void checkEveryProcessRole() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        try {
            ClientLuaBinding.registerBindings(engine, new ClientServiceImpl());

            Platform.setContent(new FakeContent(false, true, true));
            engine.executeStringOrThrow("""
                    assert(RunService:IsClient() == true, 'a client process must report IsClient')
                    assert(RunService:IsServer() == true,
                        'single-player hosts an integrated server')
                    """, "RunServiceSpTest");

            Platform.setContent(new FakeContent(false, true, false));
            engine.executeStringOrThrow("""
                    assert(RunService:IsClient() == true)
                    assert(RunService:IsServer() == false,
                        'a remote client hosts no server')
                    """, "RunServiceRemoteTest");

            Platform.setContent(new FakeContent(true, false, false));
            engine.executeStringOrThrow("""
                    assert(RunService:IsClient() == false, 'dedicated servers are not clients')
                    assert(RunService:IsServer() == true)
                    """, "RunServiceDedicatedTest");
        } finally {
            // Platform is global static state shared by the whole test JVM; leaving a dedicated
            // fake behind would push every later client test onto the dummy GUI branch.
            Platform.setContent(null);
            engine.shutdown();
        }
    }
}

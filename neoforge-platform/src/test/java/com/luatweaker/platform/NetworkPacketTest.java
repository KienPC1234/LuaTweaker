package com.luatweaker.platform;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Lua remote-event packets must fire through the network service. */
class NetworkPacketTest {

    private static final String DUMMY_UUID = "dummy-uuid";

    @BeforeAll
    static void loadMods() {
        AutonomousModsFixture.ensureLoaded();
    }

    @Test
    void networkServiceIsRegistered() {
        assertNotNull(AutonomousModsFixture.getNetworkService(),
                "NetworkServiceImpl should be registered");
    }

    @Test
    void staffSwapPacketFires() {
        assertDoesNotThrow(() -> AutonomousModsFixture.getNetworkService()
                        .OnClientFired("StaffSwapSkill", DUMMY_UUID, new byte[0]),
                "StaffSwapSkill packet firing failed");
    }

    @Test
    void staffCastPacketFires() {
        assertDoesNotThrow(() -> AutonomousModsFixture.getNetworkService()
                        .OnClientFired("StaffCastSkill", DUMMY_UUID, new byte[0]),
                "StaffCastSkill packet firing failed");
    }
}

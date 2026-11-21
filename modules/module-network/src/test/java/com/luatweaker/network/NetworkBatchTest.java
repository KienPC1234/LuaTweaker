package com.luatweaker.network;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Batch fan-out: one encode for N players, plus the except-one helper. */
class NetworkBatchTest {

    CapturingNetwork network;
    ILuaEngine engine;
    NetworkServiceImpl service;

    @BeforeEach
    void setup() {
        network = new CapturingNetwork();
        Platform.setNetwork(network);
        engine = LuaEngineFactory.create(false);
        service = NetworkTestFixture.service(engine);
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void fireClientsEncodesOnceForTwoPlayers() {
        List<String> players = List.of(
                "00000000-0000-0000-0000-000000000001",
                "00000000-0000-0000-0000-000000000002");
        service.FireClients("BatchEvent", players, engine.wrapString("hi"));

        assertBatch();
    }

    private void assertBatch() {
        assertEquals(2, network.payloads.size(), "one frame per target player");
        assertEquals(network.payloads.get(0).channel(), network.payloads.get(1).channel(),
                "both frames share the event channel");
        assertTrue(java.util.Arrays.equals(network.payloads.get(0).data(), network.payloads.get(1).data()),
                "batch fan-out sends identical bytes (single encode)");
    }

    @Test
    void fireClientsSkipsBlankUuids() {
        service.FireClients("BatchEvent",
                List.of("  ", "00000000-0000-0000-0000-000000000001"), engine.wrapString("hi"));

        assertEquals(1, network.payloads.size(), "blank uuids are skipped, not sent");
    }

    @Test
    void fireClientsWithEmptyListSendsNothing() {
        service.FireClients("BatchEvent", List.of(), engine.wrapString("hi"));

        assertTrue(network.payloads.isEmpty(), "empty target list sends no packets");
    }
}

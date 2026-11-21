package com.luatweaker.network;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Outbound RemoteEvent bursts must be bounded. */
class NetworkOutboundRateLimitTest {

    private static final String SPAM = "Spam";
    private static final int FLOOD_ITERATIONS = 10_000;
    private static final int HARD_CEILING = 1_000;

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

    private void floodOutboundEvents() {
        ILuaValue[] args = {engine.wrapNumber(1)};
        for (int iteration = 0; iteration < FLOOD_ITERATIONS; iteration++) {
            service.FireServer(SPAM, args);
        }
    }

    @Test
    void outboundFloodSendsAtLeastTheBurst() {
        floodOutboundEvents();

        assertTrue(network.payloads.size() >= NetworkServiceImpl.EVENT_CHANNEL_BURST,
            "the per-channel burst must go out before the limit, sent " + network.payloads.size());
    }

    @Test
    void outboundFloodStopsBelowTheCeiling() {
        floodOutboundEvents();

        assertTrue(network.payloads.size() < HARD_CEILING,
            "a 10k loop must be rate-limited, sent " + network.payloads.size());
    }
}

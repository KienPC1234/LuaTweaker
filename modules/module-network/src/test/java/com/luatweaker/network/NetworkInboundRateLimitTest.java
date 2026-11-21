package com.luatweaker.network;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inbound floods from one player are bounded per player, not globally. */
class NetworkInboundRateLimitTest {

    private static final String RATE_LIMITED = "RateLimited";
    private static final String SPAM = "Spam";
    private static final String PLAYER_A = "player-a";
    private static final String PLAYER_B = "player-b";
    private static final String COUNTER_KEY = "_spamCount";
    private static final int FLOOD_ITERATIONS = 10_000;
    private static final int HARD_CEILING = 1_000;
    private static final int INVOKE_FLOOD = 60;

    private static final String EVENT_SCRIPT = """
        local ev = NetworkService:GetOrCreateRemoteEvent('Spam')
        _G._spamCount = 0
        ev.OnServerEvent:Connect(function(player) _G._spamCount = _G._spamCount + 1 end)
        """;

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

    private byte[] emptyArgs() {
        return LuaPayloadCodec.encodeArgs();
    }

    private void floodInboundInvokes() {
        service.GetOrCreateRemoteFunction(RATE_LIMITED);
        for (int iteration = 0; iteration < INVOKE_FLOOD; iteration++) {
            service.OnRpcInvoke("flood-" + iteration, RATE_LIMITED, PLAYER_A, emptyArgs());
        }
        service.OnRpcInvoke("flood-final", RATE_LIMITED, PLAYER_A, emptyArgs());
    }

    private double floodInboundEvents() {
        engine.executeString(EVENT_SCRIPT, "TEST");
        for (int iteration = 0; iteration < FLOOD_ITERATIONS; iteration++) {
            service.OnClientFired(SPAM, PLAYER_A, emptyArgs());
        }
        return engine.getGlobalEnvironment().rawget(COUNTER_KEY).asDouble();
    }

    @Test
    void inboundInvokeFloodIsRejected() {
        floodInboundInvokes();
        String error = NetworkTestFixture.decodeEnvelope(network.last()).error();

        assertTrue(error.contains("Too many RemoteFunction requests"),
            "the burst budget must reject the flood, got: " + error);
    }

    @Test
    void inboundInvokeBudgetIsPerPlayer() {
        floodInboundInvokes();
        service.OnRpcInvoke("flood-other", RATE_LIMITED, PLAYER_B, emptyArgs());
        String error = NetworkTestFixture.decodeEnvelope(network.last()).error();

        assertFalse(error.contains("Too many"),
            "one player's burst must not spend another player's budget, got: " + error);
    }

    @Test
    void inboundEventFloodIsDelivered() {
        double fired = floodInboundEvents();

        assertTrue(fired >= NetworkServiceImpl.EVENT_CHANNEL_BURST,
            "the per-channel burst must be delivered before the limit kicks in, got " + fired);
    }

    @Test
    void inboundEventFloodIsLimited() {
        double fired = floodInboundEvents();

        assertTrue(fired < HARD_CEILING, "a 10k flood must be rate-limited, delivered " + fired);
    }

    @Test
    void inboundEventBudgetIsPerPlayer() {
        double fired = floodInboundEvents();

        service.OnClientFired(SPAM, PLAYER_B, emptyArgs());

        assertEquals(fired + 1, engine.getGlobalEnvironment().rawget(COUNTER_KEY).asDouble(),
            "one player's flood must not spend another player's event budget");
    }
}

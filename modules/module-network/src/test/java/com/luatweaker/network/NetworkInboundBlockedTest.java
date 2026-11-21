package com.luatweaker.network;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.mod.LuaModContext;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Push-only channels refuse forged client packets: {@code SetInboundEnabled(false)}
 * drops serverbound traffic before decode, and channel ownership records the first
 * registering mod for audit.
 */
class NetworkInboundBlockedTest {

    private static final String BLOCKED_EVENT = "ShieldPush";
    private static final String OPEN_EVENT = "ChatPush";
    private static final String BLOCKED_FUNCTION = "AdminGrant";
    private static final String OPEN_FUNCTION = "PingQuery";
    private static final String COUNTER_KEY = "_shieldHits";
    private static final String CHUNK = "TEST";

    private CapturingNetwork network;
    private ILuaEngine engine;
    private NetworkServiceImpl service;

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

    private double counter() {
        return engine.getGlobalEnvironment().rawget(COUNTER_KEY).asDouble();
    }

    private byte[] emptyArgs() {
        return LuaPayloadCodec.encodeArgs();
    }

    private void connectCounter(String eventName, boolean blockInbound) {
        String script = "local ev = NetworkService:GetOrCreateRemoteEvent('"
                + eventName + "')\n"
                + "_G._shieldHits = 0\n"
                + "ev.OnServerEvent:Connect(function(player)"
                + " _G._shieldHits = _G._shieldHits + 1 end)\n"
                + (blockInbound ? "ev:SetInboundEnabled(false)\n" : "");
        engine.executeString(script, CHUNK);
    }

    @Nested
    class BlockedEvents {

        @Test
        void blockedEventDropsInbound() {
            connectCounter(BLOCKED_EVENT, true);
            service.OnClientFired(BLOCKED_EVENT, NetworkTestFixture.PLAYER_UUID, emptyArgs());

            assertEquals(0.0, counter(),
                    "a forged packet on a blocked channel must never reach the listener");
        }

        @Test
        void openEventDeliversInbound() {
            connectCounter(OPEN_EVENT, false);
            service.OnClientFired(OPEN_EVENT, NetworkTestFixture.PLAYER_UUID, emptyArgs());

            assertEquals(1.0, counter(),
                    "inbound stays accepted by default so existing mods keep working");
        }

        @Test
        void reenabledEventDeliversInbound() {
            connectCounter(BLOCKED_EVENT, true);
            engine.executeString(
                    "NetworkService:GetOrCreateRemoteEvent('"
                            + BLOCKED_EVENT + "'):SetInboundEnabled(true)",
                    CHUNK);
            service.OnClientFired(BLOCKED_EVENT, NetworkTestFixture.PLAYER_UUID, emptyArgs());

            assertEquals(1.0, counter(),
                    "re-allowing inbound must restore delivery for maintenance windows");
        }
    }

    @Nested
    class BlockedFunctions {

        @Test
        void blockedFunctionAnswersImmediately() {
            engine.executeString("""
                    local fn = NetworkService:GetOrCreateRemoteFunction('AdminGrant')
                    fn:SetInboundEnabled(false)
                    """, CHUNK);
            service.OnRpcInvoke("req-blocked", BLOCKED_FUNCTION,
                    NetworkTestFixture.PLAYER_UUID, emptyArgs());

            assertTrue(NetworkTestFixture.decodeEnvelope(network.last()).error()
                            .contains("does not accept client requests"),
                    "a blocked function must fail fast instead of timing the caller out");
        }

        @Test
        void openFunctionWithoutHandlerReportsMissingHandler() {
            service.GetOrCreateRemoteFunction(OPEN_FUNCTION);
            service.OnRpcInvoke("req-open", OPEN_FUNCTION,
                    NetworkTestFixture.PLAYER_UUID, emptyArgs());

            assertTrue(NetworkTestFixture.decodeEnvelope(network.last()).error()
                            .contains("has no OnServerInvoke handler"),
                    "an open channel keeps the previous behavior for unhandled functions");
        }
    }

    @Nested
    class ChannelOwnership {

        @Test
        void ownerIsFirstRegistrant() {
            LuaModContext.pushLoadingMod("mod_a");
            try {
                service.GetOrCreateRemoteEvent("OwnedChannel");
            } finally {
                LuaModContext.popLoadingMod();
            }
            LuaModContext.pushLoadingMod("mod_b");
            try {
                service.GetOrCreateRemoteEvent("OwnedChannel");
            } finally {
                LuaModContext.popLoadingMod();
            }

            assertEquals("mod_a", service.getChannelOwners().get("OwnedChannel"),
                    "the first mod to register keeps ownership when another mod shares the name");
        }

        @Test
        void runtimeOwnerWhenCreatedOutsideLoad() {
            service.GetOrCreateRemoteEvent("RuntimeChannel");

            assertEquals("runtime", service.getChannelOwners().get("RuntimeChannel"),
                    "channels created outside a mod load are attributed to the runtime");
        }
    }
}

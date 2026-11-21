package com.luatweaker.network;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-channel budgets isolate chatty mods, per-owner RPC caps isolate hung
 * clients, and circular tables fail fast instead of burning the nesting budget.
 */
class NetworkChannelBudgetTest {

    private static final String QUIET_EVENT = "QuietTrade";
    private static final String LOUD_EVENT = "SpammyHud";
    private static final String QUIET_FUNCTION = "PingQuiet";
    private static final String LOUD_FUNCTION = "SpammyQuery";
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

    private double counter(String key) {
        return engine.getGlobalEnvironment().rawget(key).asDouble();
    }

    private byte[] emptyArgs() {
        return LuaPayloadCodec.encodeArgs();
    }

    @Nested
    class EventIsolation {

        @Test
        void loudChannelDoesNotStarveQuietChannel() {
            engine.executeString("""
                    local loud = NetworkService:GetOrCreateRemoteEvent('SpammyHud')
                    _G._loudCount = 0
                    loud.OnServerEvent:Connect(function(p) _G._loudCount = _G._loudCount + 1 end)
                    local quiet = NetworkService:GetOrCreateRemoteEvent('QuietTrade')
                    _G._quietCount = 0
                    quiet.OnServerEvent:Connect(function(p) _G._quietCount = _G._quietCount + 1 end)
                    """, CHUNK);
            for (int iteration = 0; iteration < 70; iteration++) {
                service.OnClientFired(LOUD_EVENT, NetworkTestFixture.PLAYER_UUID, emptyArgs());
            }
            service.OnClientFired(QUIET_EVENT, NetworkTestFixture.PLAYER_UUID, emptyArgs());

            assertEquals(1.0, counter("_quietCount"),
                    "exhausting one channel must not silence another channel");
        }
    }

    @Nested
    class FunctionIsolation {

        @Test
        void loudFunctionDoesNotStarveQuietFunction() {
            service.GetOrCreateRemoteFunction(LOUD_FUNCTION);
            service.GetOrCreateRemoteFunction(QUIET_FUNCTION);
            for (int iteration = 0; iteration < 40; iteration++) {
                service.OnRpcInvoke("loud-" + iteration, LOUD_FUNCTION,
                        NetworkTestFixture.PLAYER_UUID, emptyArgs());
            }
            service.OnRpcInvoke("quiet-1", QUIET_FUNCTION,
                    NetworkTestFixture.PLAYER_UUID, emptyArgs());

            assertTrue(NetworkTestFixture.decodeEnvelope(network.last()).error()
                            .contains("has no OnServerInvoke handler"),
                    "the quiet function must still answer after the loud one hit its own cap");
        }
    }

    @Nested
    class RpcOwnerCap {

        @Test
        void ownerCapRejectsTheSeventeenthRequest() {
            PendingRpcTracker tracker = new PendingRpcTracker(engine);
            for (int iteration = 0; iteration < 16; iteration++) {
                tracker.registerRequest("player-a");
            }

            assertThrows(IllegalStateException.class,
                () -> tracker.registerRequest("player-a"),
                "one hung client must not be able to grow its slice past the cap");
        }

        @Test
        void otherOwnerIsUnaffected() {
            PendingRpcTracker tracker = new PendingRpcTracker(engine);
            for (int iteration = 0; iteration < 16; iteration++) {
                tracker.registerRequest("player-a");
            }

            assertDoesNotThrow(() -> tracker.registerRequest("player-b"),
                "a full slice for one caller must not block a healthy caller");
        }
    }

    @Nested
    class CircularTables {

        @Test
        void circularReferenceFailsFast() {
            ILuaTable root = engine.createTable();
            root.rawset("self", root);

            assertThrows(IllegalArgumentException.class, () -> LuaPayloadCodec.encodeArgs(root),
                "a self-referencing table must be rejected instead of walking the nesting budget");
        }

        @Test
        void circularErrorNamesTheCause() {
            ILuaTable root = engine.createTable();
            root.rawset("self", root);

            assertTrue(NetworkTestFixture.errorMessage(() -> LuaPayloadCodec.encodeArgs(root))
                            .contains("circular"),
                    "the rejection must name the circular reference");
        }

        @Test
        void sharedSubtableWithoutCycleEncodes() {
            ILuaTable shared = engine.createTable();
            shared.rawset(1, engine.wrapNumber(1));
            ILuaTable root = engine.createTable();
            root.rawset("a", shared);
            root.rawset("b", shared);

            assertDoesNotThrow(() -> LuaPayloadCodec.encodeArgs(root),
                "a DAG that merely shares a subtable must still encode");
        }
    }
}

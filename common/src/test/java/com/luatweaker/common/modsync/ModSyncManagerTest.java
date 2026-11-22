package com.luatweaker.common.modsync;

import com.luatweaker.core.mod.ModSync;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModSyncManagerTest {

    private static final String PLAYER = "00000000-0000-0000-0000-000000000001";
    private static final String MOD_ID = "core";
    private static final String MOD_VERSION = "1.0.0";

    @AfterEach
    void resetState() {
        ModSyncManager.onPlayerLeave(PLAYER);
        ModSyncManager.setEnforcementMode(ModSyncManager.MODE_KICK);
        ModSyncBundles.clear();
    }

    @Nested
    class EnforcementMode {

        @Test
        void defaultIsKick() {
            assertEquals(ModSyncManager.MODE_KICK, ModSyncManager.getEnforcementMode(),
                    "an unconfigured server must refuse mismatched clients like Forge");
        }

        @Test
        void warnDowngradesToWarnOnly() {
            ModSyncManager.setEnforcementMode("warn");

            assertEquals(ModSyncManager.MODE_WARN, ModSyncManager.getEnforcementMode(),
                    "the server owner can downgrade enforcement to warn-only");
        }

        @Test
        void unknownFallsBackToKick() {
            ModSyncManager.setEnforcementMode("bogus");

            assertEquals(ModSyncManager.MODE_KICK, ModSyncManager.getEnforcementMode(),
                    "an unknown mode must fail closed to kick, never to silent allow");
        }
    }

    @Nested
    class HelloLifecycle {

        @Test
        void helloRoundTripIsVisible() {
            ModSyncManager.onPlayerJoin(PLAYER);
            ModSyncManager.storeHello(PLAYER, Map.of(MOD_ID, MOD_VERSION));

            assertEquals(Map.of(MOD_ID, MOD_VERSION), ModSyncManager.clientMods(PLAYER),
                    "the stored hello must be returned for enforcement and Lua checks");
        }

        @Test
        void helloClearsPendingJoin() {
            ModSyncManager.onPlayerJoin(PLAYER);
            ModSyncManager.storeHello(PLAYER, Map.of());

            assertTrue(ModSyncManager.pendingJoins(Long.MAX_VALUE).isEmpty(),
                    "a client that sent its hello must never hit the no-hello timeout");
        }

        @Test
        void freshJoinIsPendingOnlyAfterTimeout() {
            ModSyncManager.onPlayerJoin(PLAYER);
            boolean flaggedImmediately = ModSyncManager.pendingJoins(0L).containsKey(PLAYER);
            boolean flaggedFarFuture = !ModSyncManager.pendingJoins(Long.MAX_VALUE).isEmpty();

            assertTrue(flaggedImmediately && !flaggedFarFuture,
                    "a join with no hello must be pending at once but not for a huge timeout");
        }

        @Test
        void leaveDropsClientState() {
            ModSyncManager.onPlayerJoin(PLAYER);
            ModSyncManager.storeHello(PLAYER, Map.of(MOD_ID, MOD_VERSION));
            ModSyncManager.onPlayerLeave(PLAYER);

            assertTrue(ModSyncManager.clientMods(PLAYER).isEmpty()
                    && ModSyncManager.pendingJoins(0L).isEmpty(),
                    "logout must drop the hello and the pending join for offline players");
        }
    }

    @Nested
    class Verdicts {

        @Test
        void mismatchKicksInKickMode() {
            ModSync.Report report = ModSync.compare(Map.of(MOD_ID, MOD_VERSION), Map.of());

            assertTrue(!report.isOk() && ModSyncManager.shouldKick(report),
                    "a missing client mod must fail the report and kick in kick mode");
        }

        @Test
        void mismatchOnlyWarnsInWarnMode() {
            ModSyncManager.setEnforcementMode(ModSyncManager.MODE_WARN);
            ModSync.Report report = ModSync.compare(Map.of(MOD_ID, MOD_VERSION), Map.of());

            assertFalse(ModSyncManager.shouldKick(report),
                    "a failed report must not kick when the owner configured warn");
        }

        @Test
        void cleanReportNeverKicks() {
            ModSync.Report report = ModSync.compare(Map.of(), Map.of());

            assertTrue(report.isOk() && !ModSyncManager.shouldKick(report),
                    "empty required sets on both sides must sync cleanly without kick");
        }
    }

    @Nested
    class BundleState {

        private static final String URL = "https://example.com/packs/aurora.zip";
        private static final String SHA =
                "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

        private static com.luatweaker.core.serverpacks.ServerBundleInfo bundle() {
            com.luatweaker.core.serverpacks.ServerBundleInfo bundle =
                    com.luatweaker.core.serverpacks.ServerBundleValidation.create(
                            new com.luatweaker.core.serverpacks.ServerBundleSpec(
                                    URL, SHA, "1.2.0", "Aurora", "aurora.zip"));
            if (bundle == null) {
                throw new AssertionError("the fixture bundle must be valid");
            }
            return bundle;
        }

        @Test
        void advertisedRoundTrips() {
            ModSyncBundles.setAdvertised(bundle());

            assertEquals(URL, ModSyncBundles.getAdvertised().url(),
                    "the advertised bundle must be visible to the verdict builder");
        }

        @Test
        void lastOfferRoundTrips() {
            ModSyncBundles.storeOffer(bundle());

            assertEquals(URL, ModSyncBundles.getLastOffer().url(),
                    "the last received offer must be visible to Lua on the client");
        }

        @Test
        void clearResetsAdvertised() {
            ModSyncBundles.setAdvertised(bundle());
            ModSyncBundles.clear();

            assertTrue(ModSyncBundles.getAdvertised() == null,
                    "clearing must drop the advertised bundle");
        }

        @Test
        void clearResetsOffer() {
            ModSyncBundles.storeOffer(bundle());
            ModSyncBundles.clear();

            assertTrue(ModSyncBundles.getLastOffer() == null,
                    "clearing must drop the last received offer");
        }
    }
}

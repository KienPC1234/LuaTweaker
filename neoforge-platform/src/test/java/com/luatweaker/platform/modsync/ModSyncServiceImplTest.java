package com.luatweaker.platform.modsync;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bundle half of the ModSync Lua service: config wins, verdict offer is the fallback. */
class ModSyncServiceImplTest {

    private static final String URL = "https://example.com/packs/aurora.zip";
    private static final String VERSION = "1.2.0";
    private static final String SHA =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @AfterEach
    void resetState() {
        ModSyncBundles.clear();
    }

    @Test
    void emptyWhenNoBundleAnywhere() {
        ModSyncServiceImpl service = new ModSyncServiceImpl();

        assertEquals("", service.getBundleInfoJson(),
                "no advertised bundle and no offer must yield an empty string");
    }

    @Test
    void advertisedBundleIsExposed() {
        ModSyncBundles.setAdvertised(bundle());
        ModSyncServiceImpl service = new ModSyncServiceImpl();

        assertTrue(service.getBundleInfoJson().contains("\"url\":\"" + URL + "\""),
                "the server-advertised bundle must be exposed, got: " + service.getBundleInfoJson());
    }

    @Test
    void lastOfferIsExposedOnClients() {
        ModSyncBundles.storeOffer(bundle());
        ModSyncServiceImpl service = new ModSyncServiceImpl();

        assertTrue(service.getBundleInfoJson().contains("\"version\":\"" + VERSION + "\""),
                "the last verdict offer must be exposed, got: " + service.getBundleInfoJson());
    }

    private static com.luatweaker.core.serverpacks.ServerBundleInfo bundle() {
        com.luatweaker.core.serverpacks.ServerBundleInfo bundle =
                com.luatweaker.core.serverpacks.ServerBundleValidation.create(
                        new com.luatweaker.core.serverpacks.ServerBundleSpec(
                                URL, SHA, VERSION, "Aurora", "aurora.zip"));
        if (bundle == null) {
            throw new AssertionError("the fixture bundle must be valid");
        }
        return bundle;
    }
}

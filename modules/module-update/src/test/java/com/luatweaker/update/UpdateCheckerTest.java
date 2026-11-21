package com.luatweaker.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link UpdateChecker#parseFeed}: a valid feed fills the whole status, an equal version
 * reports no update, and malformed feeds fail loudly instead of inventing a version.
 */
class UpdateCheckerTest {

    private static final String MOD_ID = "gt_addon";
    private static final String CURRENT = "1.0.2";
    private static final String FEED_URL = "https://example.com/u";

    @Test
    void parseFeedWithUpdateBuildsTheFullStatus() {
        UpdateStatus status = UpdateChecker.parseFeed(MOD_ID, CURRENT, FEED_URL,
                "{\"version\":\"1.1.0\",\"name\":\"1.1.0 release\","
                        + "\"download_url\":\"https://example.com/d\",\"changelog\":\"fixes\"}");
        assertEquals(new UpdateStatus(MOD_ID, CURRENT, FEED_URL, "1.1.0", "1.1.0 release",
                        "https://example.com/d", "fixes", false, null),
                status,
                "a valid feed must produce a completed status carrying every optional field");
    }

    @Test
    void parseFeedUpToDateReportsNoUpdate() {
        UpdateStatus status = UpdateChecker.parseFeed(MOD_ID, CURRENT, FEED_URL,
                "{\"version\":\"1.0.2\"}");
        assertEquals(new UpdateStatus(MOD_ID, CURRENT, FEED_URL, "1.0.2", null, null, null,
                        false, null),
                status,
                "a feed whose version equals the installed one must report no update");
    }

    @Test
    void parseFeedWithoutVersionFailsNamingTheField() {
        UpdateStatus status = UpdateChecker.parseFeed(MOD_ID, CURRENT, FEED_URL,
                "{\"name\":\"no version here\"}");
        assertTrue(status.error() != null && status.error().contains("version")
                        && status.latestVersion() == null,
                "a feed without a version must fail loudly and name the missing field: "
                        + status.error());
    }

    @Test
    void parseFeedWithInvalidJsonFails() {
        UpdateStatus status = UpdateChecker.parseFeed(MOD_ID, CURRENT, FEED_URL,
                "this is not json");
        assertTrue(status.error() != null && status.latestVersion() == null,
                "a non-JSON feed must fail instead of reporting a phantom version");
    }

    @Test
    void parseFeedWithNullVersionFails() {
        UpdateStatus status = UpdateChecker.parseFeed(MOD_ID, CURRENT, FEED_URL,
                "{\"version\": null}");
        assertTrue(status.error() != null,
                "an explicit JSON null version must be rejected like a missing one");
    }
}

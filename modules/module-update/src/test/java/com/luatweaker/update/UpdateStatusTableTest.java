package com.luatweaker.update;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Lua shape of {@code UpdateService:GetStatus()} for every status flavour: available
 * update, up to date, failure, in-flight check and unknown mod.
 */
class UpdateStatusTableTest extends UpdateStatusFixture {

    private static final String DOWNLOAD_URL = "https://example.com/dl";

    @Test
    void availableUpdateExposesEveryField() {
        UpdateServiceImpl.recordResult(new UpdateStatus(MOD_ID, CURRENT, FEED_URL, LATEST,
                "New features", DOWNLOAD_URL, "Fixed everything", false, null));
        assertEquals(List.of("true", "false", CURRENT, LATEST, "New features", DOWNLOAD_URL,
                        "Fixed everything", FEED_URL),
                statusFields(MOD_ID),
                "an available update must expose HasUpdate/Checking/versions/name/download/"
                        + "changelog/update_url with the expected values");
    }

    @Test
    void upToDateStatusHasUpdateFalse() {
        UpdateServiceImpl.recordResult(status(LATEST, LATEST, false, null));
        assertTrue(!tableFlag("HasUpdate"),
                "equal installed and feed versions must report HasUpdate=false");
    }

    @Test
    void upToDateStatusKeepsLatestVersion() {
        UpdateServiceImpl.recordResult(status(LATEST, LATEST, false, null));
        assertEquals(LATEST, new UpdateServiceImpl(engine).GetStatus(MOD_ID).asTable()
                        .rawget("LatestVersion").asString(),
                "an up-to-date check must still expose the feed version scripts compare against");
    }

    @Test
    void failureStatusExposesThePlatformError() {
        UpdateServiceImpl.recordResult(status(CURRENT, null, false, HTTP_404));
        assertEquals(HTTP_404, new UpdateServiceImpl(engine).GetStatus(MOD_ID).asTable()
                        .rawget("Error").asString(),
                "a failed check must surface its reason through GetStatus");
    }

    @Test
    void failureStatusHasNoUpdate() {
        UpdateServiceImpl.recordResult(status(CURRENT, null, false, HTTP_404));
        assertTrue(!tableFlag("HasUpdate"),
                "a failed check must never claim an available update");
    }

    @Test
    void failureStatusIsNotChecking() {
        UpdateServiceImpl.recordResult(status(CURRENT, null, false, HTTP_404));
        assertTrue(!tableFlag("Checking"),
                "a recorded failure is terminal, so Checking must be false");
    }

    @Test
    void failureStatusHasNoLatestVersion() {
        UpdateServiceImpl.recordResult(status(CURRENT, null, false, HTTP_404));
        assertNull(new UpdateServiceImpl(engine).GetStatus(MOD_ID).asTable()
                        .rawget("LatestVersion"),
                "a failed check must leave LatestVersion as Lua nil");
    }

    @Test
    void inFlightCheckIsFlagged() {
        UpdateServiceImpl.recordResult(status(CURRENT, null, true, null));
        assertTrue(tableFlag("Checking"),
                "an in-flight check must report Checking=true so scripts can wait");
    }

    @Test
    void inFlightCheckHasNoUpdate() {
        UpdateServiceImpl.recordResult(status(CURRENT, null, true, null));
        assertTrue(!tableFlag("HasUpdate"),
                "an unfinished check must not claim an available update");
    }

    @Test
    void unknownModStatusIsNil() {
        assertNull(new UpdateServiceImpl(engine).GetStatus("does_not_exist"),
                "an unknown mod must resolve to nil so Lua sees 'no data'");
    }
}

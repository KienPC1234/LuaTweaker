package com.luatweaker.update;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the asynchronous {@code UpdateServiceImpl.checkAll()} pipeline: a plain-http feed must
 * produce a terminal, non-checking, error-carrying status with no invented version, completed
 * checks must not be rescheduled, and mods without {@code update_url} are never checked.
 */
class UpdateCheckPipelineTest extends UpdateServiceFixture {

    private static final String WEB_MOD = "web_mod";
    private static final String REFUSED_URL = "http://localhost:1/update.json";
    private static final String PLAIN_MOD = "plain_mod";

    /** Loads a mod whose feed uses plain http (rejected without any network I/O) and waits. */
    private UpdateStatus runRejectedFeedCheck() throws IOException, InterruptedException {
        ModFixtures.loadModWithUpdateUrl(luamods(), engine, WEB_MOD, REFUSED_URL, "net.http");
        requestChecks();
        return waitForTerminal(WEB_MOD);
    }

    private static void requestChecks() {
        UpdateServiceImpl.checkAll();
    }

    private static boolean isChecking(UpdateStatus status) {
        return status.checking();
    }

    @Test
    void checkAllRecordsTheHttpsRejection() throws IOException, InterruptedException {
        UpdateStatus terminal = runRejectedFeedCheck();
        assertTrue(terminal.error().contains("https"),
                "checkAll must store the https-only rejection on the terminal status: "
                        + terminal.error());
    }

    @Test
    void checkAllClearsTheCheckingFlagWhenTerminal() throws IOException, InterruptedException {
        assertFalse(isChecking(runRejectedFeedCheck()),
                "a finished check must not keep reporting Checking=true to Lua");
    }

    @Test
    void checkAllNeverInventsALatestVersion() throws IOException, InterruptedException {
        assertNull(runRejectedFeedCheck().latestVersion(),
                "a rejected feed must not fabricate a latest version");
    }

    @Test
    void completedCheckIsNotRescheduled() throws IOException, InterruptedException {
        ModFixtures.loadModWithUpdateUrl(luamods(), engine, WEB_MOD, REFUSED_URL);
        requestChecks();
        waitForTerminal(WEB_MOD);
        UpdateStatus before = UpdateServiceImpl.getUpdateStatuses().get(WEB_MOD);

        requestChecks();

        UpdateStatus after = UpdateServiceImpl.getUpdateStatuses().get(WEB_MOD);
        assertSame(before, after,
                "a completed check must not be rescheduled on the same load cycle");
    }

    @Test
    void modsWithoutUpdateUrlAreNeverChecked() throws IOException {
        ModFixtures.loadMod(luamods(), engine, PLAIN_MOD);
        requestChecks();
        assertNull(UpdateServiceImpl.getUpdateStatuses().get(PLAIN_MOD),
                "mods without update_url must never be checked");
    }
}

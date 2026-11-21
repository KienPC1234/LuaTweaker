package com.luatweaker.core.serverpacks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verdict shape: old clients keep working, mismatch can carry an offer. */
class ModSyncVerdictTest {

    private static final String MESSAGE = "missing on client: core v1.0.0";
    private static final String URL = "https://example.com/packs/aurora.zip";
    private static final String SHA = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    private static ServerBundleInfo bundle() {
        ServerBundleInfo bundle = ServerBundleValidation.create(new ServerBundleSpec(
                URL, SHA, "1.2.0", "Aurora", "aurora.zip"));
        if (bundle == null) {
            throw new AssertionError("the fixture bundle must be valid");
        }
        return bundle;
    }

    @Test
    void okVerdictOmitsDownloadBlock() {
        String verdict = ModSyncVerdict.verdictJson(true, MESSAGE, bundle());

        assertFalse(verdict.contains("download"),
                "an in-sync verdict must not advertise a bundle: " + verdict);
    }

    @Test
    void mismatchWithoutBundleKeepsOldShape() {
        String verdict = ModSyncVerdict.verdictJson(false, MESSAGE, null);

        assertEquals("{\"ok\":false,\"message\":\"" + MESSAGE + "\"}", verdict,
                "a bundle-less mismatch must keep the exact old shape for old clients");
    }

    @Test
    void mismatchWithBundleEmbedsBlock() {
        String verdict = ModSyncVerdict.verdictJson(false, MESSAGE, bundle());

        assertTrue(verdict.contains("\"download\":{\"url\":\"" + URL + "\""),
                "a mismatch with an advertised bundle must embed the offer: " + verdict);
    }

    @Test
    void messageQuotesAreEscaped() {
        String verdict = ModSyncVerdict.verdictJson(false, "say \"hi\"", null);

        assertTrue(verdict.contains("say \\\"hi\\\""),
                "quotes in the message must stay valid JSON: " + verdict);
    }
}

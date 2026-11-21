package com.luatweaker.update;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link UpdateChecker} transport rules: https only, non-blank URL, loud failure for
 * unreachable hosts and bounded body reads.
 */
class UpdateTransportTest {

    private static final String MOD_ID = "gt_addon";
    private static final String CURRENT = "1.0.2";

    /** Runs a check against {@code url} without naming the call "check" (that reads as an assert). */
    private static UpdateStatus fetchChecked(String url) {
        return UpdateChecker.check(MOD_ID, CURRENT, url);
    }

    private static boolean isChecking(UpdateStatus status) {
        return status.checking();
    }

    @Test
    void plainHttpIsRejected() {
        UpdateStatus status = fetchChecked("http://example.com/u");
        assertTrue(!isChecking(status) && status.error() != null
                        && status.error().contains("https"),
                "a plain http feed must be rejected with an explanation naming the https rule");
    }

    @Test
    void blankUrlIsRejected() {
        UpdateStatus status = fetchChecked("  ");
        assertTrue(status.error() != null && status.error().contains("update_url"),
                "a blank feed URL must be rejected and named as update_url");
    }

    @Test
    void unreachableHttpsFeedFailsLoudly() {
        UpdateStatus status = fetchChecked("https://127.0.0.1:1/update.json");
        assertTrue(status.error() != null && status.latestVersion() == null,
                "an unreachable https feed must fail loudly, not hang forever or invent a version");
    }

    @Test
    void oversizedBodyIsRejected() {
        byte[] oversized = new byte[UpdateChecker.MAX_BODY_BYTES + 1];
        assertThrows(IOException.class, () ->
                        UpdateChecker.readBounded(new ByteArrayInputStream(oversized),
                                UpdateChecker.MAX_BODY_BYTES),
                "a body above the cap must raise IOException instead of buffering it");
    }

    @Test
    void smallBodyIsReadBack() throws IOException {
        byte[] small = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] read = UpdateChecker.readBounded(new ByteArrayInputStream(small),
                UpdateChecker.MAX_BODY_BYTES);
        assertEquals("hello", new String(read, StandardCharsets.UTF_8),
                "a body under the cap must be read back byte-for-byte");
    }
}

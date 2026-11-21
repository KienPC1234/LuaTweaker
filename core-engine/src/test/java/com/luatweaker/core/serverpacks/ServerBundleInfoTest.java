package com.luatweaker.core.serverpacks;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Validation of server-advertised bundles: fail-closed on any problem. */
class ServerBundleInfoTest {

    private static final String URL = "https://example.com/packs/aurora.zip";
    private static final String OTHER_URL = "https://cdn.example.org/aurora.zip";
    private static final String BARE_HOST_URL = "https://example.com/";
    private static final String SHA = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
    private static final String VERSION = "1.2.0";
    private static final String NAME = "Aurora RPG";
    private static final String FILENAME = "aurora.zip";

    private static ServerBundleInfo valid() {
        ServerBundleInfo bundle = ServerBundleValidation.create(
                new ServerBundleSpec(URL, SHA, VERSION, NAME, FILENAME));
        if (bundle == null) {
            throw new AssertionError("the fixture bundle must be valid");
        }
        return bundle;
    }

    private static ServerBundleSpec spec(String url, String sha) {
        return new ServerBundleSpec(url, sha, VERSION, NAME, FILENAME);
    }

    @Nested
    class Create {

        @Test
        void validSpecIsAccepted() {
            assertNotNull(ServerBundleValidation.create(spec(URL, SHA)),
                    "a well-formed https bundle must be accepted");
        }

        @Test
        void httpUrlIsRejected() {
            assertNull(ServerBundleValidation.create(spec("http://example.com/pack.zip", SHA)),
                    "plain http must be rejected: downloads are https-only");
        }

        @Test
        void urlWithUserinfoIsRejected() {
            assertNull(ServerBundleValidation.create(spec("https://user@example.com/pack.zip", SHA)),
                    "credentials embedded in the URL must be rejected");
        }

        @Test
        void urlWithoutHostIsRejected() {
            assertNull(ServerBundleValidation.create(spec("https:///pack.zip", SHA)),
                    "a URL without a host must be rejected");
        }

        @Test
        void shortShaIsRejected() {
            assertNull(ServerBundleValidation.create(spec(URL, "abc123")),
                    "a truncated digest must be rejected");
        }

        @Test
        void nonHexShaIsRejected() {
            assertNull(ServerBundleValidation.create(spec(URL,
                    "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz")),
                    "a digest outside the hex alphabet must be rejected");
        }

        @Test
        void uppercaseShaIsNormalized() {
            ServerBundleInfo bundle = ServerBundleValidation.create(
                    spec(URL, SHA.toUpperCase(java.util.Locale.ROOT)));

            assertEquals(SHA, bundle.sha256(),
                    "an uppercase digest must be accepted and normalized to lowercase");
        }
    }

    @Nested
    class Identity {

        @Test
        void idIsSixteenHexChars() {
            assertTrue(valid().bundleId().matches("[0-9a-f]{16}"),
                    "the folder identity must be fixed-length lowercase hex");
        }

        @Test
        void idIsContentBound() {
            ServerBundleInfo first = ServerBundleValidation.create(spec(URL, SHA));
            ServerBundleInfo second = ServerBundleValidation.create(spec(OTHER_URL, SHA));

            assertEquals(first.bundleId(), second.bundleId(),
                    "the same bytes from another mirror must share one folder and one consent");
        }
    }

    private static boolean digestMatches(String text) {
        return valid().verifySha256(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Nested
    class Verify {

        @Test
        void verifyMatchesKnownDigest() {
            assertTrue(digestMatches("test"),
                    "sha256('test') must verify against its known digest");
        }

        @Test
        void verifyRejectsTamperedBytes() {
            assertFalse(digestMatches("tampered"),
                    "one changed byte must fail verification");
        }
    }

    @Nested
    class BundleJson {

        @Test
        void malformedJsonParsesToNull() {
            assertNull(ServerBundleJson.parseBundle("{broken"),
                    "malformed bundle JSON must parse to null, never throw");
        }

        @Test
        void bareBundleRoundTrips() {
            assertEquals(URL, ServerBundleJson.parseBundle(ServerBundleJson.toJson(valid())).url(),
                    "a serialized bundle must parse back to the same URL");
        }

        @Test
        void verdictOfferIsExtracted() {
            String verdict = "{\"ok\":false,\"message\":\"missing\",\"download\":"
                    + ServerBundleJson.toJson(valid()) + "}";

            assertEquals(URL, ServerBundleJson.parseVerdictOffer(verdict).url(),
                    "the download block must be extracted from a verdict envelope");
        }

        @Test
        void verdictWithoutBlockParsesToNull() {
            assertNull(ServerBundleJson.parseVerdictOffer("{\"ok\":true,\"message\":\"in sync\"}"),
                    "a verdict without a download block must parse to null, never throw");
        }
    }

    @Nested
    class Filenames {

        @Test
        void traversalFilenameFallsBackToUrlSegment() {
            ServerBundleInfo bundle = ServerBundleValidation.create(
                    new ServerBundleSpec(URL, SHA, VERSION, NAME, "../evil.zip"));

            assertEquals(FILENAME, bundle.filename(),
                    "a traversal filename must fall back to the safe URL segment");
        }

        @Test
        void unusableNamesFallBackToDefault() {
            ServerBundleInfo bundle = ServerBundleValidation.create(
                    new ServerBundleSpec(BARE_HOST_URL, SHA, VERSION, NAME, "pack.jar"));

            assertEquals(ServerBundleInfo.DEFAULT_FILENAME, bundle.filename(),
                    "with no usable name anywhere, the default must apply");
        }
    }

    @Nested
    class Labels {

        @Test
        void labelPrefersAdminName() {
            assertEquals(NAME, valid().label(),
                    "the admin display name must win over the URL host");
        }

        @Test
        void labelFallsBackToHost() {
            ServerBundleInfo bundle = ServerBundleValidation.create(
                    new ServerBundleSpec(URL, SHA, null, null, FILENAME));

            assertEquals("example.com", bundle.label(),
                    "without an admin name the URL host must be the display label");
        }
    }
}

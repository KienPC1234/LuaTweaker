package com.luatweaker.core.serverpacks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Fetch-verify-store: no unverified byte ever lands, matching zips are reused. */
class BundleDownloaderTest {

    private static final byte[] PAYLOAD = "fake-zip-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String TAMPERED_TEXT = "evil";
    private static final String SERVER_DIRNAME = "srv";

    @TempDir
    Path temp;

    private static ServerBundleInfo bundleFor(byte[] payload) {
        StringBuilder hex = new StringBuilder();
        for (byte b : ServerBundleInfo.sha256(payload)) {
            hex.append(String.format("%02x", b));
        }
        ServerBundleInfo bundle = ServerBundleValidation.create(new ServerBundleSpec(
                "https://example.com/packs/unit.zip", hex.toString(), "1.0", "Unit", "unit.zip"));
        if (bundle == null) {
            throw new AssertionError("the fixture bundle must be valid");
        }
        return bundle;
    }

    @Test
    void successStoresVerifiedFile() throws IOException {
        ServerBundleInfo bundle = bundleFor(PAYLOAD);
        BundleDownloader.Outcome outcome =
                BundleDownloader.download(bundle, temp.resolve(SERVER_DIRNAME), url -> PAYLOAD);

        assertArrayEquals(PAYLOAD, Files.readAllBytes(outcome.file()),
                "the stored zip must be byte-identical to the verified download");
    }

    @Test
    void freshDownloadIsMarkedFresh() throws IOException {
        ServerBundleInfo bundle = bundleFor(PAYLOAD);
        BundleDownloader.Outcome outcome =
                BundleDownloader.download(bundle, temp.resolve(SERVER_DIRNAME), url -> PAYLOAD);

        assertTrue(outcome.fresh(), "a first-time download must be marked fresh");
    }

    @Test
    void existingValidFileSkipsFetch() throws IOException {
        ServerBundleInfo bundle = bundleFor(PAYLOAD);
        Path serverDir = temp.resolve(SERVER_DIRNAME);
        BundleDownloader.download(bundle, serverDir, url -> PAYLOAD);
        BundleDownloader.Outcome second = BundleDownloader.download(bundle, serverDir, url -> {
            throw new IOException("fetch must not run when the verified file exists");
        });

        assertFalse(second.fresh(), "a matching on-disk zip must be reused without fetching");
    }

    @Test
    void hashMismatchThrows() {
        ServerBundleInfo bundle = bundleFor(PAYLOAD);

        assertThrows(IOException.class,
                () -> BundleDownloader.download(bundle, temp.resolve(SERVER_DIRNAME),
                        url -> TAMPERED_TEXT.getBytes(StandardCharsets.UTF_8)),
                "a digest mismatch must fail instead of storing hostile bytes");
    }

    @Test
    void hashMismatchLeavesNothing() throws IOException {
        ServerBundleInfo bundle = bundleFor(PAYLOAD);
        Path serverDir = temp.resolve(SERVER_DIRNAME);
        Path target = serverDir.resolve(BundleDownloader.PACKS_DIRNAME).resolve(bundle.filename());
        try {
            BundleDownloader.download(bundle, serverDir,
                    url -> TAMPERED_TEXT.getBytes(StandardCharsets.UTF_8));
        } catch (IOException expected) {
            assertFalse(Files.exists(target),
                    "a rejected download must leave no file behind");
            return;
        }
        throw new AssertionError("expected an IOException for tampered bytes but nothing was thrown");
    }

    @Test
    void oversizePayloadThrows() {
        ServerBundleInfo bundle = bundleFor(PAYLOAD);

        assertThrows(IOException.class,
                () -> BundleDownloader.download(bundle, temp.resolve(SERVER_DIRNAME),
                        url -> new byte[(int) BundleDownloader.BUNDLE_MAX_BYTES + 1]),
                "a payload above the cap must be refused before hashing");
    }

    @Test
    void nullBytesThrow() {
        ServerBundleInfo bundle = bundleFor(PAYLOAD);

        assertThrows(IOException.class,
                () -> BundleDownloader.download(bundle, temp.resolve(SERVER_DIRNAME), url -> null),
                "a fetcher returning nothing must fail loudly");
    }
}

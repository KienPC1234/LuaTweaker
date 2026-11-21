package com.luatweaker.core.serverpacks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Consent defaults to prompt; only an explicit well-formed file changes that. */
class ServerConsentStoreTest {

    private static final String LABEL = "Aurora RPG";
    private static final String PACK_DIRNAME = "pack";

    @TempDir
    Path temp;

    @Test
    void missingFileReadsAsk() {
        assertEquals(ServerConsentStore.Decision.ASK,
                ServerConsentStore.read(temp.resolve("fresh")),
                "a bundle with no consent file must prompt, never auto-download");
    }

    @Test
    void allowRoundTrips() throws IOException {
        Path dir = temp.resolve(PACK_DIRNAME);
        ServerConsentStore.write(dir, ServerConsentStore.Decision.ALLOW, LABEL);

        assertEquals(ServerConsentStore.Decision.ALLOW, ServerConsentStore.read(dir),
                "a remembered allow must survive the round trip");
    }

    @Test
    void denyRoundTrips() throws IOException {
        Path dir = temp.resolve(PACK_DIRNAME);
        ServerConsentStore.write(dir, ServerConsentStore.Decision.DENY, LABEL);

        assertEquals(ServerConsentStore.Decision.DENY, ServerConsentStore.read(dir),
                "a remembered deny must survive the round trip");
    }

    @Test
    void corruptFileReadsAsk() throws IOException {
        Path dir = temp.resolve(PACK_DIRNAME);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(ServerConsentStore.CONSENT_FILENAME), "{broken",
                StandardCharsets.UTF_8);

        assertEquals(ServerConsentStore.Decision.ASK, ServerConsentStore.read(dir),
                "a corrupt consent file must fail safe to prompt");
    }

    @Test
    void unknownValueReadsAsk() throws IOException {
        Path dir = temp.resolve(PACK_DIRNAME);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(ServerConsentStore.CONSENT_FILENAME),
                "{\"decision\":\"maybe\"}", StandardCharsets.UTF_8);

        assertEquals(ServerConsentStore.Decision.ASK, ServerConsentStore.read(dir),
                "an unknown decision value must fail safe to prompt");
    }

    @Test
    void oversizedFileReadsAsk() throws IOException {
        Path dir = temp.resolve(PACK_DIRNAME);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(ServerConsentStore.CONSENT_FILENAME),
                "x".repeat(ServerConsentStore.MAX_CONSENT_BYTES + 1), StandardCharsets.UTF_8);

        assertEquals(ServerConsentStore.Decision.ASK, ServerConsentStore.read(dir),
                "an oversized consent file must fail safe to prompt");
    }
}

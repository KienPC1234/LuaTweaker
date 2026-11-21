package com.luatweaker.core.mod;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModSyncTest {

    static final String CORE = "core";
    static final String VERSION = "1.0.0";
    static final String REQUIRED = "required";
    static final String OPTIONAL = "optional";
    static final String UNIVERSAL = "universal";

    @Test
    void syncDefaultsToRequiredForUniversal() {
        LuaModManifest parsed = LuaModManifest.parseJson(
                "{\"id\":\"demo\",\"environment\":\"" + UNIVERSAL + "\"}");
        assertTrue(parsed != null && REQUIRED.equals(parsed.sync()),
                "universal mods default to required so mismatches are enforced");
    }

    @Test
    void syncDefaultsToOptionalForSideOnly() {
        LuaModManifest parsed = LuaModManifest.parseJson(
                "{\"id\":\"demo\",\"environment\":\"server\"}");
        assertTrue(parsed != null && OPTIONAL.equals(parsed.sync()),
                "server-only mods default to optional and never block clients");
    }

    @Test
    void enforcedOnlyForUniversalRequired() {
        ModSyncAssertions.assertEnforced();
    }

    @Test
    void compareDetectsMissingAndMismatch() {
        ModSyncAssertions.assertMismatchReport(ModSync.compare(
                Map.of(CORE, VERSION, "magic", "2.1.0"),
                Map.of(CORE, VERSION, "magic", "2.0.0")));
    }

    @Test
    void compareDetectsMissingOnClient() {
        ModSyncAssertions.assertMissingClient(
                ModSync.compare(Map.of(CORE, VERSION), Map.of()));
    }

    @Test
    void matchingVersionsAreOk() {
        ModSync.Report report = ModSync.compare(
                Map.of(CORE, VERSION), Map.of(CORE, VERSION));

        assertTrue(report.isOk(), "identical versions sync cleanly");
    }

    @Test
    void parseHelloIgnoresMalformedInput() {
        ModSyncAssertions.assertMalformedHello();
    }

    @Test
    void buildMessageMentionsMissing() {
        ModSync.Report report = ModSync.compare(Map.of(CORE, VERSION), Map.of());
        String message = ModSync.buildMessage(report, 400);

        assertTrue(message.contains(CORE), "kick message names the missing mod");
    }
}

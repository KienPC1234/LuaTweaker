package com.luatweaker.common.modsync;

import com.luatweaker.common.config.LuaTweakerConfig;
import com.luatweaker.core.mod.ModSync;
import com.luatweaker.core.serverpacks.ServerBundleInfo;
import com.luatweaker.core.serverpacks.ServerBundleSpec;
import com.luatweaker.core.serverpacks.ServerBundleValidation;
import com.luatweaker.core.vm.LuaEngineLimits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies ServerModSync JSON payloads and LuaTweakerConfig engine limit conversion. */
class ServerModSyncTest {

    private static final String VALID_SHA =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @AfterEach
    void reset() {
        ModSyncBundles.clear();
    }

    @Test
    void challengeJsonContainsModsObject() {
        String json = ServerModSync.buildChallengeJson();

        assertTrue(json.startsWith("{\"mods\":{"),
                "ModSync challenge JSON must contain root mods map, got: " + json);
    }

    @Test
    void cleanVerdictMarksOkWithoutDownloadOffer() {
        ModSync.Report clean = ModSync.compare(Map.of("core", "1.0.0"), Map.of("core", "1.0.0"));
        ServerBundleInfo bundle = fixtureBundle();

        String verdict = ServerModSync.buildVerdictJson(clean, bundle);

        assertTrue(verdict.contains("\"ok\":true") && !verdict.contains("\"download\":"),
                "an in-sync client verdict must report ok=true and omit download offer: " + verdict);
    }

    @Test
    void mismatchVerdictEmbedsAdvertisedBundleDownload() {
        ModSync.Report mismatch = ModSync.compare(Map.of("core", "2.0.0"), Map.of());
        ServerBundleInfo bundle = fixtureBundle();

        String verdict = ServerModSync.buildVerdictJson(mismatch, bundle);

        assertTrue(verdict.contains("\"ok\":false")
                        && verdict.contains("\"download\":")
                        && verdict.contains("https://example.com/packs/core.zip"),
                "a mismatched verdict with an advertised bundle must embed the download JSON: " + verdict);
    }

    @Test
    void configEngineLimitsStayNonNegative() {
        LuaEngineLimits limits = LuaTweakerConfig.toEngineLimits();

        assertTrue(limits.maxMemoryBytes() >= 0L
                        && limits.maxCallDepth() >= 0
                        && limits.maxCoroutines() >= 0,
                "LuaTweakerConfig.toEngineLimits() must produce valid non-negative limits");
    }

    private static ServerBundleInfo fixtureBundle() {
        ServerBundleInfo info = ServerBundleValidation.create(new ServerBundleSpec(
                "https://example.com/packs/core.zip",
                VALID_SHA,
                "2.0.0",
                "Core Pack",
                "core.zip"));
        if (info == null) {
            throw new AssertionError("Fixture ServerBundleSpec must pass validation");
        }
        return info;
    }
}

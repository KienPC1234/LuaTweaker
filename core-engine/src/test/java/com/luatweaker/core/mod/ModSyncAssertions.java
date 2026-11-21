package com.luatweaker.core.mod;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shared assertions for {@link ModSyncTest} (kept outside the test class). */
final class ModSyncAssertions {

    private ModSyncAssertions() {
    }

    static void assertEnforced() {
        assertTrue(ModSync.isEnforced(manifest("a", ModSyncTest.VERSION, ModSyncTest.UNIVERSAL,
                        ModSyncTest.REQUIRED)),
                "universal required mods must match on both sides");
        assertFalse(ModSync.isEnforced(manifest("b", ModSyncTest.VERSION, ModSyncTest.UNIVERSAL,
                        ModSyncTest.OPTIONAL)),
                "universal optional mods warn but never kick");
        assertFalse(ModSync.isEnforced(manifest("c", ModSyncTest.VERSION, "server",
                        ModSyncTest.REQUIRED)),
                "server-only mods never enforce on clients");
        assertFalse(ModSync.isEnforced(manifest("d", ModSyncTest.VERSION, "client",
                        ModSyncTest.REQUIRED)),
                "client-only mods never enforce on the server");
    }

    static void assertMismatchReport(ModSync.Report report) {
        assertFalse(report.isOk(), "a version drift must fail the report");
        assertEquals(List.of("magic"), report.versionMismatch(),
                "only the drifted mod is listed as mismatch");
        assertTrue(report.missingOnClient().isEmpty(), "no mod is missing in this case");
    }

    static void assertMissingClient(ModSync.Report report) {
        assertFalse(report.isOk(), "a missing client mod must fail the report");
        assertEquals(1, report.missingOnClient().size(), "exactly one mod is missing");
        assertEquals(ModSyncTest.CORE, report.missingOnClient().get(0).id(),
                "the missing id is reported");
    }

    static void assertMalformedHello() {
        assertTrue(ModSync.parseClientHello(null).isEmpty(), "null hello parses to empty");
        assertTrue(ModSync.parseClientHello("not-json").isEmpty(),
                "malformed hello parses to empty");
        Map<String, String> parsed =
                ModSync.parseClientHello("{\"mods\":{\"core\":\"1.0.0\"}}");
        assertEquals(ModSyncTest.VERSION, parsed.get(ModSyncTest.CORE),
                "well-formed hello keeps versions");
    }

    private static LuaModManifest manifest(String id, String version, String env, String sync) {
        String json = "{\"id\":\"" + id + "\",\"version\":\"" + version
                + "\",\"environment\":\"" + env + "\",\"sync\":\"" + sync + "\"}";
        LuaModManifest parsed = LuaModManifest.parseJson(json);
        if (parsed == null) {
            throw new IllegalStateException("manifest parse failed for " + id);
        }
        return parsed;
    }
}

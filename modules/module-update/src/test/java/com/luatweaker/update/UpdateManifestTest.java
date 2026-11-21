package com.luatweaker.update;

import com.luatweaker.core.mod.LuaModManifest;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the manifest {@code update_url} parsing: declared feeds are kept verbatim, a missing or
 * blank field stays null so no check is scheduled.
 */
class UpdateManifestTest {

    @Test
    void manifestExposesTheDeclaredUpdateUrl() {
        LuaModManifest manifest = Objects.requireNonNull(LuaModManifest.parseJson("""
                {
                  "id": "gt_addon",
                  "version": "1.0.2",
                  "environment": "universal",
                  "update_url": "https://raw.githubusercontent.com/example/gt_addon/main/update.json"
                }
                """), "a manifest with an update_url must parse");
        assertEquals("https://raw.githubusercontent.com/example/gt_addon/main/update.json",
                manifest.updateUrl(),
                "the declared update_url must be exposed verbatim for the update checker");
    }

    @Test
    void manifestWithoutUpdateUrlHasNoFeed() {
        LuaModManifest manifest = Objects.requireNonNull(LuaModManifest.parseJson("""
                {
                  "id": "gt_addon",
                  "version": "1.0.2",
                  "environment": "universal"
                }
                """), "a manifest without update_url must still parse");
        assertNull(manifest.updateUrl(),
                "a manifest without update_url must not schedule an update check");
    }

    @Test
    void blankUpdateUrlIsIgnored() {
        LuaModManifest manifest = Objects.requireNonNull(LuaModManifest.parseJson("""
                {
                  "id": "gt_addon",
                  "environment": "universal",
                  "update_url": "   "
                }
                """), "a manifest with a blank update_url must still parse");
        assertNull(manifest.updateUrl(),
                "a blank update_url must be treated as absent, not as a relative URL");
    }
}

package com.luatweaker.core.mod;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Manifest dependency gates: rich object entries, platform condition blocks,
 * and the legacy plain-id form side by side.
 */
class LuaModManifestTest {

    private static final String BASE = "{\"id\":\"demo\",\"environment\":\"universal\",";

    private static final String RICH_DEPENDENCIES = BASE
            + "\"dependencies\":[\"plain_lib\","
            + "{\"id\":\"lib\",\"min_version\":\"1.0.0\",\"max_version\":\"1.9.9\"},"
            + "{\"id\":\"soft\",\"min_version\":\"0.5.0\",\"optional\":true}]}";

    private static final String PLATFORM_BLOCKS = BASE
            + "\"minecraft\":{\"min_version\":\"1.21\",\"max_version\":\"1.21.1\"},"
            + "\"luatweaker\":{\"min_version\":\"1.0.0\"},"
            + "\"mods\":[{\"id\":\"jei\",\"min_version\":\"19.0.0\"},\"curios\"]}";

    private static final String EXACT_MINECRAFT = BASE + "\"minecraft\":\"1.21.1\"}";

    private static final String MALFORMED_DEPENDENCIES = BASE
            + "\"dependencies\":[\"\",42,{\"noid\":true},{\"id\":\"  \"},{\"id\":\"ok\"}]}";

    private static final String LEGACY_MINIMAL = BASE + "\"dependencies\":[]}";

    private static final String INTEGRATIONS = BASE
            + "\"integrations\":{"
            + "\"jei\":\"integrations/jei.lua\","
            + "\"mek\":{\"entry\":\"integrations/mek.lua\",\"min_version\":\"10.7.0\"},"
            + "\"curios\":{\"min_version\":\"7.0.0\"},"
            + "\"jade\":true,"
            + "\"broken\":42,"
            + "\"empty\":{}}}";

    private static final String MEK_ID = "mek";
    private static final String CURIOS_ID = "curios";

    private static LuaModManifest parse(String json) {
        return LuaModManifest.parseJson(json);
    }

    @Nested
    class Dependencies {

        @Test
        void parsesRichDependencyObjectsAlongsidePlainIdsCheck() {
            LuaModManifest manifest = parse(RICH_DEPENDENCIES);
            assertNotNull(manifest,
                    "a manifest mixing plain ids and dependency objects must parse");
        }

        @Test
        void parsesRichDependencyObjectsAlongsidePlainIdsPlainLib() {
            LuaModManifest manifest = parse(RICH_DEPENDENCIES);
            assertEquals(
                    List.of(
                            new ModDependency("plain_lib", null, null, false),
                            new ModDependency("lib", "1.0.0", "1.9.9", false),
                            new ModDependency("soft", "0.5.0", null, true)),
                    manifest.dependencies(),
                    "plain ids and rich objects must keep declaration order with their version bounds");
        }

        @Test
        void dropsMalformedEntriesButKeepsTheModCheck() {
            LuaModManifest manifest = parse(MALFORMED_DEPENDENCIES);
            assertNotNull(manifest,
                    "a dependency list containing malformed entries must still yield a manifest");
        }

        @Test
        void dropsMalformedEntriesButKeepsTheModOk() {
            LuaModManifest manifest = parse(MALFORMED_DEPENDENCIES);
            assertEquals(List.of(new ModDependency("ok", null, null, false)), manifest.dependencies(),
                    "only the well-formed dependency object must survive parsing");
        }
    }

    @Nested
    class PlatformGates {

        @Test
        void parsesPlatformConditionBlocksCheck() {
            LuaModManifest manifest = parse(PLATFORM_BLOCKS);
            assertNotNull(manifest,
                    "a manifest carrying minecraft/luatweaker/mods gates must parse");
        }

        @Test
        void parsesPlatformConditionBlocks121() {
            LuaModManifest manifest = parse(PLATFORM_BLOCKS);
            assertEquals(new VersionConstraint("1.21", "1.21.1"), manifest.minecraft(),
                    "the minecraft object must become an inclusive min/max constraint");
        }

        @Test
        void parsesPlatformConditionBlocks100() {
            LuaModManifest manifest = parse(PLATFORM_BLOCKS);
            assertEquals(new VersionConstraint("1.0.0", null), manifest.luatweaker(),
                    "the luatweaker object must become a lower-bound-only constraint");
        }

        @Test
        void parsesPlatformConditionBlocksJei() {
            LuaModManifest manifest = parse(PLATFORM_BLOCKS);
            assertEquals(
                    List.of(
                            new ModDependency("jei", "19.0.0", null, false),
                            new ModDependency("curios", null, null, false)),
                    manifest.modDependencies(),
                    "the mods array must accept both object entries and plain ids");
        }

        @Test
        void parsesExactVersionStringAsClosedWindowCheck() {
            LuaModManifest manifest = parse(EXACT_MINECRAFT);
            assertNotNull(manifest,
                    "a manifest whose minecraft gate is a bare version string must parse");
        }

        @Test
        void parsesExactVersionStringAsClosedWindow1211() {
            LuaModManifest manifest = parse(EXACT_MINECRAFT);
            assertEquals(new VersionConstraint("1.21.1", "1.21.1"), manifest.minecraft(),
                    "a bare minecraft version must become an exact min=max constraint");
        }
    }

    @Nested
    class LegacyDefaults {

        @Test
        void legacyManifestWithoutGatesParsesCleanCheck() {
            LuaModManifest manifest = parse(LEGACY_MINIMAL);
            assertNotNull(manifest,
                    "a manifest without any gate block must parse");
        }

        @Test
        void legacyManifestWithoutGatesParsesCleanDependencies() {
            LuaModManifest manifest = parse(LEGACY_MINIMAL);
            assertTrue(manifest.dependencies().isEmpty(),
                    "an empty dependencies array must produce no dependencies");
        }

        @Test
        void legacyManifestWithoutGatesParsesCleanModDependencies() {
            LuaModManifest manifest = parse(LEGACY_MINIMAL);
            assertTrue(manifest.modDependencies().isEmpty(),
                    "a manifest without a mods array must produce no mod dependencies");
        }

        @Test
        void legacyManifestWithoutGatesParsesCleanIntegrations() {
            LuaModManifest manifest = parse(LEGACY_MINIMAL);
            assertTrue(manifest.integrations().isEmpty(),
                    "a manifest without an integrations block must produce no integrations");
        }

        @Test
        void legacyManifestWithoutGatesParsesCleanLuatweaker() {
            LuaModManifest manifest = parse(LEGACY_MINIMAL);
            assertNull(manifest.luatweaker(),
                    "a manifest without a luatweaker block must report no constraint");
        }

        @Test
        void legacyManifestWithoutGatesParsesCleanMinecraft() {
            LuaModManifest manifest = parse(LEGACY_MINIMAL);
            assertNull(manifest.minecraft(),
                    "a manifest without a minecraft block must report no constraint");
        }
    }

    @Nested
    class IntegrationShapes {

        @Test
        void parsesIntegrationBlockInAllShapesCheck() {
            LuaModManifest manifest = parse(INTEGRATIONS);
            assertNotNull(manifest,
                    "a manifest whose integrations mix strings, objects and booleans must parse");
        }

        @Test
        void parsesIntegrationBlockInAllShapesIntegrations() {
            LuaModManifest manifest = parse(INTEGRATIONS);
            Map<String, ModIntegration> integrations = manifest.integrations();
            assertEquals(4, integrations.size(),
                    "only the four well-formed integration entries must be kept: " + integrations);
        }

        @Test
        void parsesIntegrationBlockInAllShapesJei() {
            LuaModManifest manifest = parse(INTEGRATIONS);
            Map<String, ModIntegration> integrations = manifest.integrations();
            assertEquals(new ModIntegration("integrations/jei.lua", null, null),
                    integrations.get("jei"),
                    "a string integration entry must be read as its entry path");
        }

        @Test
        void parsesIntegrationBlockInAllShapesMek() {
            LuaModManifest manifest = parse(INTEGRATIONS);
            Map<String, ModIntegration> integrations = manifest.integrations();
            assertEquals(new ModIntegration("integrations/mek.lua", "10.7.0", null),
                    integrations.get(MEK_ID),
                    "an object integration entry must keep both its path and min_version");
        }

        @Test
        void parsesIntegrationBlockInAllShapesCurios() {
            LuaModManifest manifest = parse(INTEGRATIONS);
            Map<String, ModIntegration> integrations = manifest.integrations();
            assertEquals(new ModIntegration(null, "7.0.0", null), integrations.get(CURIOS_ID),
                    "an integration object without an entry keeps only its min_version");
        }

        @Test
        void parsesIntegrationBlockInAllShapesJade() {
            LuaModManifest manifest = parse(INTEGRATIONS);
            Map<String, ModIntegration> integrations = manifest.integrations();
            assertEquals(new ModIntegration(null, null, null), integrations.get("jade"),
                    "a boolean true integration must degrade to an empty requirement");
        }
    }

    @Nested
    class IntegrationRequirements {

        @Test
        void curiosEntryFallsBackToConventionalPath() {
            LuaModManifest manifest = parse(INTEGRATIONS);
            Map<String, ModIntegration> integrations = manifest.integrations();
            assertEquals("integrations/curios.lua",
                    integrations.get(CURIOS_ID).entryOrDefault(CURIOS_ID),
                    "an integration without an entry defaults to integrations/<id>.lua");
        }

        @Test
        void mekRequirementAcceptsNewerPatch() {
            LuaModManifest manifest = parse(INTEGRATIONS);
            Map<String, ModIntegration> integrations = manifest.integrations();
            assertTrue(integrations.get(MEK_ID).satisfiedBy("10.7.1"),
                    "a newer patch must satisfy the declared minimum version");
        }

        @Test
        void mekRequirementRejectsOlderPatch() {
            LuaModManifest manifest = parse(INTEGRATIONS);
            Map<String, ModIntegration> integrations = manifest.integrations();
            assertFalse(integrations.get(MEK_ID).satisfiedBy("10.6.9"),
                    "an older patch must not satisfy the declared minimum version");
        }

        @Test
        void mekRequirementDescribesMinimum() {
            LuaModManifest manifest = parse(INTEGRATIONS);
            Map<String, ModIntegration> integrations = manifest.integrations();
            assertEquals("mek (>= 10.7.0)", integrations.get(MEK_ID).describeRequirement(MEK_ID),
                    "the requirement description must name the integration and its minimum version");
        }
    }
}

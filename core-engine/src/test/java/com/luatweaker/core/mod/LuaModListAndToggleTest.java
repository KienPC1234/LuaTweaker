package com.luatweaker.core.mod;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Management UI backend: summaries, on/off cascade, and config read/write.
 */
class LuaModListAndToggleTest {

    private static final String UNIVERSAL = "universal";
    private static final String DEFAULT_MAIN = "x = 1";
    private static final String LIB_ID = "lib";
    private static final String MID_ID = "mid";
    private static final String TOP_ID = "top";
    private static final String SOFT_ID = "soft";
    private static final String CFG_ID = "cfg";
    private static final String GONE_ID = "gone";
    private static final String ZIP_ID = "zipped";
    private static final String GHOST_ID = "ghost";
    private static final String SOLO_ID = "solo";

    private static final String BASE_ID = "base";
    private static final String BROKEN_ID = "broken";

    private static final String BASE_MANIFEST =
            manifestFor(BASE_ID, "\"version\":\"2.0.0\",");
    private static final String BROKEN_MANIFEST = "{\"id\":\"broken\",\"environment\":\"universal\","
            + "\"dependencies\":[\"ghost\"]}";
    private static final String MID_MANIFEST = "{\"id\":\"mid\",\"environment\":\"universal\","
            + "\"dependencies\":[\"lib\"]}";
    private static final String TOP_MANIFEST = "{\"id\":\"top\",\"environment\":\"universal\","
            + "\"dependencies\":[{\"id\":\"mid\"}]}";
    private static final String TOP_ON_LIB_MANIFEST = "{\"id\":\"top\",\"environment\":\"universal\","
            + "\"dependencies\":[\"lib\"]}";
    private static final String SOFT_MANIFEST = "{\"id\":\"soft\",\"environment\":\"universal\","
            + "\"optional_dependencies\":[\"lib\"]}";

    private static final String POWER_10 = "{\"power\": 10}";
    private static final String CONFIG_V1 = "{\"v\": 1}";
    private static final String CONFIG_V2 = "{\"v\": 2}";

    @TempDir
    Path root;

    @AfterAll
    static void stopLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @AfterEach
    void resetEnvironment() {
        LuaModManager.resetEnvironment();
    }

    private static String manifestFor(String id) {
        return manifestFor(id, "");
    }

    private static String manifestFor(String id, String extraFields) {
        return "{\"id\":\"" + id + "\",\"environment\":\"" + UNIVERSAL + "\"," + extraFields + "\"main\":\"main.lua\"}";
    }

    private void writeMod(String id, String manifestJson, String mainLua, String defaultConfig) throws IOException {
        Path modDir = Files.createDirectories(root.resolve("luamods").resolve(id));
        Files.writeString(modDir.resolve("manifest.json"), manifestJson);
        Files.writeString(modDir.resolve("main.lua"), mainLua);
        if (defaultConfig != null) {
            Files.writeString(modDir.resolve("default_config.json"), defaultConfig);
        }
    }

    private void writeMod(String id) throws IOException {
        writeMod(id, manifestFor(id), DEFAULT_MAIN, null);
    }

    @Nested
    class Summaries {

        private Map<String, ModSummary> summaries() throws IOException {
            writeMod(BASE_ID, BASE_MANIFEST, "baseLoaded = true", null);
            writeMod(BROKEN_ID, BROKEN_MANIFEST, "brokenLoaded = true", null);
            ILuaEngine engine = LuaEngineFactory.create(false);
            LuaModManager.loadLuaMods(root.toFile(), engine, UNIVERSAL);
            return LuaModManager.listModSummaries(root.toFile()).stream()
                    .collect(Collectors.toMap(ModSummary::id, s -> s));
        }

        @Test
        void summariesReportSwitchLoadAndErrorStateById() throws IOException {
            assertEquals(2, summaries().size(),
                    "every discovered mod must be reported exactly once");
        }

        @Test
        void summariesReportSwitchLoadAndErrorStateEnabled() throws IOException {
            assertTrue(summaries().get(BASE_ID).enabled(),
                    "a healthy mod must be reported as enabled");
        }

        @Test
        void summariesReportSwitchLoadAndErrorStateLoaded() throws IOException {
            assertTrue(summaries().get(BASE_ID).loaded(),
                    "a healthy mod must be reported as loaded");
        }

        @Test
        void summariesReportSwitchLoadAndErrorStateLoadError() throws IOException {
            assertNull(summaries().get(BASE_ID).loadError(),
                    "a healthy mod must not report a load error");
        }

        @Test
        void summariesReportSwitchLoadAndErrorStateONLoadedV200() throws IOException {
            assertEquals("ON - loaded v2.0.0", summaries().get(BASE_ID).statusLine(),
                    "the status line must show ON, loaded, and the manifest version");
        }

        @Test
        void summariesReportSwitchLoadAndErrorStateEnabled2() throws IOException {
            assertTrue(summaries().get(BROKEN_ID).enabled(),
                    "a mod skipped at load time must still be reported as enabled");
        }

        @Test
        void summariesReportSwitchLoadAndErrorStateLoaded2() throws IOException {
            assertFalse(summaries().get(BROKEN_ID).loaded(),
                    "a mod with a missing required dependency must not be reported as loaded");
        }

        @Test
        void summariesReportSwitchLoadAndErrorStateLoadError2() throws IOException {
            assertNotNull(summaries().get(BROKEN_ID).loadError(),
                    "a skipped mod must report the reason it was not loaded");
        }

        @Test
        void summariesReportSwitchLoadAndErrorStateERROR() throws IOException {
            ModSummary broken = summaries().get(BROKEN_ID);
            assertTrue(broken.statusLine().startsWith("ERROR - "),
                    "a skipped mod's status line must start with ERROR: " + broken.statusLine());
        }
    }

    @Nested
    class DisableCascade {

        private void writeCascadeGraph() throws IOException {
            writeMod(LIB_ID);
            writeMod(MID_ID, MID_MANIFEST, DEFAULT_MAIN, null);
            writeMod(TOP_ID, TOP_MANIFEST, DEFAULT_MAIN, null);
            writeMod(SOFT_ID, SOFT_MANIFEST, DEFAULT_MAIN, null);
        }

        private Map<String, ModSummary> summaryById() {
            return LuaModManager.listModSummaries(root.toFile()).stream()
                    .collect(Collectors.toMap(ModSummary::id, s -> s));
        }

        private Map<String, ModSummary> summariesAfterDisablingLib() throws IOException {
            writeCascadeGraph();
            LuaModManager.setModEnabled(root.toFile(), LIB_ID, false);
            return summaryById();
        }

        @Test
        void disableCascadesThroughRequiredDependentsOnlyLib() throws IOException {
            writeCascadeGraph();
            assertEquals(List.of(LIB_ID, MID_ID, TOP_ID),
                    LuaModManager.previewDisableCascade(root.toFile(), LIB_ID),
                    "the preview must list the target and its transitive required dependents only");
        }

        @Test
        void disableCascadesThroughRequiredDependentsOnlyLib2() throws IOException {
            writeCascadeGraph();
            assertEquals(List.of(LIB_ID, MID_ID, TOP_ID),
                    LuaModManager.setModEnabled(root.toFile(), LIB_ID, false),
                    "disabling must switch off the target and its transitive required dependents");
        }

        @Test
        void disableCascadesThroughRequiredDependentsOnlyLib3() throws IOException {
            assertFalse(summariesAfterDisablingLib().get(LIB_ID).enabled(),
                    "the disabled target itself must be switched off");
        }

        @Test
        void disableCascadesThroughRequiredDependentsOnlyMid() throws IOException {
            assertFalse(summariesAfterDisablingLib().get(MID_ID).enabled(),
                    "a direct required dependent must be switched off");
        }

        @Test
        void disableCascadesThroughRequiredDependentsOnlyTop() throws IOException {
            assertFalse(summariesAfterDisablingLib().get(TOP_ID).enabled(),
                    "a transitive required dependent must be switched off");
        }

        @Test
        void disableCascadesThroughRequiredDependentsOnlySoft() throws IOException {
            assertTrue(summariesAfterDisablingLib().get(SOFT_ID).enabled(),
                    "optional dependents must not cascade");
        }

        @Test
        void disableCascadesThroughRequiredDependentsOnlyLib4() throws IOException {
            assertEquals("OFF - disabled", summariesAfterDisablingLib().get(LIB_ID).statusLine(),
                    "the status line of a switched-off mod must read OFF - disabled");
        }
    }

    @Nested
    class EnableToggle {

        private List<String> enableTopAfterDisablingLib() throws IOException {
            writeMod(LIB_ID);
            writeMod(TOP_ID, TOP_ON_LIB_MANIFEST, DEFAULT_MAIN, null);
            LuaModManager.setModEnabled(root.toFile(), LIB_ID, false);
            return LuaModManager.setModEnabled(root.toFile(), TOP_ID, true);
        }

        private Map<String, ModSummary> summaryById() {
            return LuaModManager.listModSummaries(root.toFile()).stream()
                    .collect(Collectors.toMap(ModSummary::id, s -> s));
        }

        @Test
        void enableFlipsOnlyTheOneModLib() throws IOException {
            writeMod(LIB_ID);
            writeMod(TOP_ID, TOP_ON_LIB_MANIFEST, DEFAULT_MAIN, null);
            assertEquals(List.of(LIB_ID, TOP_ID),
                    LuaModManager.setModEnabled(root.toFile(), LIB_ID, false),
                    "disabling a required dependency must cascade to its dependent");
        }

        @Test
        void enableFlipsOnlyTheOneModTop() throws IOException {
            writeMod(LIB_ID);
            writeMod(TOP_ID, TOP_ON_LIB_MANIFEST, DEFAULT_MAIN, null);
            assertTrue(LuaModManager.setModEnabled(root.toFile(), TOP_ID, true).isEmpty(),
                    "enabling a mod that is already on must report no change");
        }

        @Test
        void enableFlipsOnlyTheOneModTopEnableReportsOnlyTheFlippedMod() throws IOException {
            assertEquals(List.of(TOP_ID), enableTopAfterDisablingLib(),
                    "re-enabling a dependent must report only the mod that actually changed");
        }

        @Test
        void enableFlipsOnlyTheOneModLib2() throws IOException {
            enableTopAfterDisablingLib();
            assertFalse(summaryById().get(LIB_ID).enabled(),
                    "enabling a dependent must not silently re-enable its disabled dependency");
        }

        @Test
        void enableFlipsOnlyTheOneModTop2() throws IOException {
            enableTopAfterDisablingLib();
            assertTrue(summaryById().get(TOP_ID).enabled(),
                    "the re-enabled mod must be reported as enabled");
        }
    }

    @Nested
    class NoOps {

        @Test
        void unknownIdAndRedundantFlipAreNoOpsGhost() throws IOException {
            writeMod(SOLO_ID);
            assertTrue(LuaModManager.previewDisableCascade(root.toFile(), GHOST_ID).isEmpty(),
                    "previewing an unknown id must switch nothing off");
        }

        @Test
        void unknownIdAndRedundantFlipAreNoOpsGhost2() throws IOException {
            writeMod(SOLO_ID);
            assertTrue(LuaModManager.setModEnabled(root.toFile(), GHOST_ID, false).isEmpty(),
                    "disabling an unknown id must change nothing");
        }

        @Test
        void unknownIdAndRedundantFlipAreNoOpsSolo() throws IOException {
            writeMod(SOLO_ID);
            assertTrue(LuaModManager.setModEnabled(root.toFile(), SOLO_ID, true).isEmpty(),
                    "already-on enable must not touch the file");
        }
    }

    @Nested
    class ConfigReadWrite {

        private void writeCfgWithDefaultConfig() throws IOException {
            writeMod(CFG_ID, manifestFor(CFG_ID), DEFAULT_MAIN, POWER_10);
        }

        @Test
        void configRoundTripFallsBackToDefaultsAndRejectsGarbagePower10() throws IOException {
            writeCfgWithDefaultConfig();
            assertEquals(POWER_10, LuaModManager.readConfigFor(root.toFile(), CFG_ID).trim(),
                    "readConfigFor must return the user config once it has been seeded");
        }

        @Test
        void configRoundTripFallsBackToDefaultsAndRejectsGarbageGarbageMustBeRejectedWithAReason() throws IOException {
            writeCfgWithDefaultConfig();
            String invalid = LuaModManager.writeConfigFor(root.toFile(), CFG_ID, "not json{");
            assertNotNull(invalid, "garbage must be rejected with a reason");
        }

        @Test
        void configRoundTripFallsBackToDefaultsAndRejectsGarbageNonObjectJSONMustBeRejected() throws IOException {
            writeCfgWithDefaultConfig();
            String notObject = LuaModManager.writeConfigFor(root.toFile(), CFG_ID, "[1,2]");
            assertNotNull(notObject, "non-object JSON must be rejected");
        }

        @Test
        void configRoundTripFallsBackToDefaultsAndRejectsGarbageCfg() throws IOException {
            writeCfgWithDefaultConfig();
            assertNull(LuaModManager.writeConfigFor(root.toFile(), CFG_ID, "{\"power\": 42}"),
                    "a well-formed object must be accepted without an error");
        }

        @Test
        void configRoundTripFallsBackToDefaultsAndRejectsGarbage42() throws IOException {
            writeCfgWithDefaultConfig();
            assertNull(LuaModManager.writeConfigFor(root.toFile(), CFG_ID, "{\"power\": 42}"),
                    "a valid config write must not report an error");
        }

        @Test
        void configRoundTripFallsBackToDefaultsAndRejectsGarbage42IsPersisted() throws IOException {
            writeCfgWithDefaultConfig();
            LuaModManager.writeConfigFor(root.toFile(), CFG_ID, "{\"power\": 42}");
            assertTrue(LuaModManager.readConfigFor(root.toFile(), CFG_ID).contains("42"),
                    "the accepted value must be persisted to the user config");
        }

        @Test
        void configRoundTripFallsBackToDefaultsAndRejectsGarbageGhost() throws IOException {
            writeCfgWithDefaultConfig();
            assertNull(LuaModManager.readConfigFor(root.toFile(), GHOST_ID),
                    "unknown mod has no config");
        }
    }

    @Nested
    class Backup {

        private Path backupDir() {
            return root.resolve("luaconfig");
        }

        private void writeTwoVersions() throws IOException {
            writeMod(CFG_ID);
            LuaModManager.writeConfigFor(root.toFile(), CFG_ID, CONFIG_V1);
            LuaModManager.writeConfigFor(root.toFile(), CFG_ID, CONFIG_V2);
        }

        private String writeSecondVersion() throws IOException {
            writeMod(CFG_ID);
            LuaModManager.writeConfigFor(root.toFile(), CFG_ID, CONFIG_V1);
            return LuaModManager.writeConfigFor(root.toFile(), CFG_ID, CONFIG_V2);
        }

        @Test
        void secondWriteKeepsSingleBackupCfg() throws IOException {
            writeMod(CFG_ID);
            assertNull(LuaModManager.writeConfigFor(root.toFile(), CFG_ID, CONFIG_V1),
                    "the first config write must not report an error");
        }

        @Test
        void secondWriteKeepsSingleBackupCfg2() throws IOException {
            assertNull(writeSecondVersion(),
                    "the second config write must not report an error either");
        }

        @Test
        void secondWriteKeepsSingleBackup2() throws IOException {
            writeTwoVersions();
            Path live = backupDir().resolve("cfg.json");
            assertTrue(Files.readString(live).contains("2"),
                    "the live config file must hold the newest written value");
        }

        @Test
        void secondWriteKeepsSingleBackupPreviousConfigMustBeKeptAsBak() throws IOException {
            writeTwoVersions();
            Path backup = backupDir().resolve("cfg.json.bak");
            assertTrue(Files.isRegularFile(backup), "previous config must be kept as .bak");
        }

        @Test
        void secondWriteKeepsSingleBackup1() throws IOException {
            writeTwoVersions();
            Path backup = backupDir().resolve("cfg.json.bak");
            assertTrue(Files.readString(backup).contains("1"),
                    "the backup must hold the value that was live before the last write");
        }
    }

    @Nested
    class DeleteMod {

        private Path backupZip() {
            return root.resolve("luamods").resolve(ZIP_ID + ".zip");
        }

        private void writeModDirectoryAndZip() throws IOException {
            writeMod(GONE_ID);
            Path zipPath = backupZip();
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(manifestFor(ZIP_ID).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            LuaModManager.listModSummaries(root.toFile());
        }

        @Test
        void deleteModRemovesDirectoryOrZipAndSwitchGone() throws IOException {
            writeModDirectoryAndZip();
            assertNull(LuaModManager.deleteMod(root.toFile(), GONE_ID),
                    "deleting an existing directory mod must not report an error");
        }

        @Test
        void deleteModRemovesDirectoryOrZipAndSwitchLuamods() throws IOException {
            writeModDirectoryAndZip();
            LuaModManager.deleteMod(root.toFile(), GONE_ID);
            assertFalse(Files.exists(root.resolve("luamods").resolve(GONE_ID)),
                    "deleting a directory mod must remove its folder");
        }

        @Test
        void deleteModRemovesDirectoryOrZipAndSwitchZipped() throws IOException {
            writeModDirectoryAndZip();
            assertNull(LuaModManager.deleteMod(root.toFile(), ZIP_ID),
                    "deleting an existing zip mod must not report an error");
        }

        @Test
        void deleteModRemovesDirectoryOrZipAndSwitchExists() throws IOException {
            writeModDirectoryAndZip();
            LuaModManager.deleteMod(root.toFile(), ZIP_ID);
            assertFalse(Files.exists(backupZip()),
                    "deleting a zip mod must remove the archive");
        }

        @Test
        void deleteModRemovesDirectoryOrZipAndSwitchGhost() throws IOException {
            writeModDirectoryAndZip();
            assertNotNull(LuaModManager.deleteMod(root.toFile(), GHOST_ID), "unknown id must fail loudly");
        }

        @Test
        void deleteModRemovesDirectoryOrZipAndSwitchDeletedModsDisappearFromTheList() throws IOException {
            writeModDirectoryAndZip();
            LuaModManager.deleteMod(root.toFile(), GONE_ID);
            LuaModManager.deleteMod(root.toFile(), ZIP_ID);
            List<ModSummary> remaining = LuaModManager.listModSummaries(root.toFile());
            assertTrue(remaining.isEmpty(), "deleted mods disappear from the list: " + remaining);
        }
    }
}

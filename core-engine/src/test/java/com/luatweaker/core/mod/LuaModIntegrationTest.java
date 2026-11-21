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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Declared cross-mod integrations: loaded when the host mod matches, skipped
 * with a recorded reason otherwise, and never fatal to the owning mod.
 */
class LuaModIntegrationTest {

    private static final String FACTORY_ID = "factory";
    private static final String MEKANISM = "mekanism";
    private static final String JEI = "jei";
    private static final String MEKANISM_ENTRY = "integrations/mekanism.lua";
    private static final String JEI_ENTRY = "integrations/jei.lua";
    private static final String FACTORY_MAIN = "factoryMainLoaded = true";
    private static final String INTEGRATION_LOADED = "factoryIntegrationLoaded = true";

    private static final String FACTORY_MANIFEST = "{\"id\":\"factory\",\"environment\":\"universal\","
            + "\"integrations\":{\"mekanism\":{\"min_version\":\"10.7.0\"}}}";

    private static final String INSTALLED_MEKANISM = "10.7.1";
    private static final String OLD_MEKANISM = "10.6.0";
    private static final String JEI_VERSION = "19.1.0";

    private static final String INTEGRATION_SCRIPT = """
            assert(mod:HasIntegration('mekanism') == true)
            assert(mod:GetIntegrationStatus('mekanism'):find('loaded') ~= nil)
            assert(mod:GetIntegrationStatus().mekanism ~= nil)
            assert(mod:HasIntegration('ghost') == false)
            """;

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

    private ILuaEngine load(boolean runIntegrationScript) throws IOException {
        ILuaEngine engine = LuaEngineFactory.create(false);
        LuaModManager.loadLuaMods(root.toFile(), engine, "universal");
        if (runIntegrationScript) {
            engine.executeString(INTEGRATION_SCRIPT, "integration_api_test");
        }
        return engine;
    }

    private ILuaEngine loadWithInstalledHost() throws IOException {
        LuaModManager.setInstalledMods(Map.of(MEKANISM, INSTALLED_MEKANISM));
        writeMod(FACTORY_MANIFEST, Map.of(MEKANISM_ENTRY, INTEGRATION_LOADED));
        return load(true);
    }

    private ILuaEngine loadWithoutHost() throws IOException {
        LuaModManager.setInstalledMods(Map.of());
        writeMod(FACTORY_MANIFEST, Map.of(MEKANISM_ENTRY, INTEGRATION_LOADED));
        return load(false);
    }

    private ILuaEngine loadWithVersionMismatchAndFailingEntry() throws IOException {
        LuaModManager.setInstalledMods(Map.of(MEKANISM, OLD_MEKANISM, JEI, JEI_VERSION));
        writeMod("{\"id\":\"factory\",\"environment\":\"universal\",\"integrations\":{"
                        + "\"mekanism\":{\"min_version\":\"10.7.0\"},"
                        + "\"jei\":\"integrations/jei.lua\"}}",
                Map.of(JEI_ENTRY, "error('integration boom')"));
        return load(false);
    }

    private void writeMod(String manifestJson, Map<String, String> extraFiles) throws IOException {
        Path modDir = Files.createDirectories(root.resolve("luamods").resolve(FACTORY_ID));
        Files.writeString(modDir.resolve("manifest.json"), manifestJson);
        Files.writeString(modDir.resolve("main.lua"), FACTORY_MAIN);
        for (Map.Entry<String, String> extra : extraFiles.entrySet()) {
            Path target = modDir.resolve(extra.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, extra.getValue());
        }
    }

    @Nested
    class HostMatches {

        @Test
        void loadsEntryWhenHostMatchesAndExposesLuaStatusFactory() throws IOException {
            loadWithInstalledHost();
            assertTrue(LuaModManager.getLoadedMods().containsKey(FACTORY_ID),
                    "a mod whose integration host is installed must load");
        }

        @Test
        void loadsEntryWhenHostMatchesAndExposesLuaStatusFactory2() throws IOException {
            loadWithInstalledHost();
            assertFalse(LuaModManager.getLoadErrors().containsKey(FACTORY_ID),
                    "a satisfied integration must not record a load error");
        }

        @Test
        void loadsEntryWhenHostMatchesAndExposesLuaStatusFactoryIntegrationLoaded() throws IOException {
            ILuaEngine engine = loadWithInstalledHost();
            assertEquals(Boolean.TRUE,
                    engine.getGlobalEnvironment().rawget("factoryIntegrationLoaded").toJavaObject(),
                    "integration entry must execute in the mod context");
        }

        @Test
        void loadsEntryWhenHostMatchesAndExposesLuaStatusFactory3() throws IOException {
            loadWithInstalledHost();
            Map<String, Map<String, String>> statuses = LuaModManager.getIntegrationStatus();
            assertTrue(statuses.get(FACTORY_ID).get(MEKANISM).startsWith("loaded"),
                    "the recorded integration status must start with loaded: " + statuses);
        }

        @Test
        void loadsEntryWhenHostMatchesAndExposesLuaStatusGetAndClearLastExecutionError() throws IOException {
            ILuaEngine engine = loadWithInstalledHost();
            assertNull(engine.getAndClearLastExecutionError(),
                    "the integration Lua API script must run without errors");
        }
    }

    @Nested
    class HostMissing {

        @Test
        void skipsWithReasonWhenHostMissingButKeepsTheModFactory() throws IOException {
            loadWithoutHost();
            assertTrue(LuaModManager.getLoadedMods().containsKey(FACTORY_ID),
                    "an unmet integration must not fail the mod");
        }

        @Test
        void skipsWithReasonWhenHostMissingButKeepsTheModFactory2() throws IOException {
            loadWithoutHost();
            assertFalse(LuaModManager.getLoadErrors().containsKey(FACTORY_ID),
                    "an unmet optional integration must not record a load error");
        }

        @Test
        void skipsWithReasonWhenHostMissingButKeepsTheModFactoryIntegrationLoaded() throws IOException {
            ILuaEngine engine = loadWithoutHost();
            assertTrue(engine.getGlobalEnvironment().rawget("factoryIntegrationLoaded").isNil(),
                    "skipped entry must not execute");
        }

        @Test
        void skipsWithReasonWhenHostMissingButKeepsTheModSkipped() throws IOException {
            loadWithoutHost();
            String status = LuaModManager.getIntegrationStatus().get(FACTORY_ID).get(MEKANISM);
            assertTrue(status.startsWith("skipped:"),
                    "a missing host must produce a skipped status: " + status);
        }

        @Test
        void skipsWithReasonWhenHostMissingButKeepsTheModMekanism() throws IOException {
            loadWithoutHost();
            String status = LuaModManager.getIntegrationStatus().get(FACTORY_ID).get(MEKANISM);
            assertTrue(status.contains(MEKANISM),
                    "the skip reason must name the missing host mod: " + status);
        }
    }

    @Nested
    class VersionMismatchAndEntryErrors {

        @Test
        void versionMismatchAndEntryErrorsAreRecordedNotFatalFactory() throws IOException {
            loadWithVersionMismatchAndFailingEntry();
            assertTrue(LuaModManager.getLoadedMods().containsKey(FACTORY_ID),
                    "a failed integration entry must not be fatal to the owning mod");
        }

        @Test
        void versionMismatchAndEntryErrorsAreRecordedNotFatalMekanism() throws IOException {
            loadWithVersionMismatchAndFailingEntry();
            Map<String, String> statuses = LuaModManager.getIntegrationStatus().get(FACTORY_ID);
            assertTrue(statuses.get(MEKANISM).startsWith("skipped:"),
                    "an installed but too-old host must be recorded as skipped: " + statuses);
        }

        @Test
        void versionMismatchAndEntryErrorsAreRecordedNotFatalJei() throws IOException {
            loadWithVersionMismatchAndFailingEntry();
            Map<String, String> statuses = LuaModManager.getIntegrationStatus().get(FACTORY_ID);
            assertTrue(statuses.get(JEI).startsWith("error:"),
                    "an integration entry that throws must be recorded as an error: " + statuses);
        }
    }

    @Nested
    class Summaries {

        @Test
        void summariesCarryIntegrationStatuses() throws IOException {
            LuaModManager.setInstalledMods(Map.of());
            writeMod("{\"id\":\"factory\",\"environment\":\"universal\","
                    + "\"integrations\":{\"mekanism\":true}}", Map.of());
            load(false);

            ModSummary summary = LuaModManager.listModSummaries(root.toFile()).stream()
                    .filter(s -> FACTORY_ID.equals(s.id())).findFirst().orElseThrow();
            assertTrue(summary.integrations().get(MEKANISM).startsWith("skipped:"),
                    "the mod summary must carry the skip reason: " + summary.integrations());
        }
    }
}

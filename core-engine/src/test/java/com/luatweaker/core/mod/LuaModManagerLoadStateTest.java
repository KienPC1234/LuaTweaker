package com.luatweaker.core.mod;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LuaModManagerLoadStateTest {

    private static final String UNIVERSAL = "universal";
    private static final String CLIENT = "client";
    private static final String UNIVERSAL_MOD = "universal_mod";
    private static final String CLIENT_MOD = "client_mod";
    private static final String SERVER_MOD = "server_mod";

    private static final String UNVIVERSAL_MOD_MAIN = "universalLoaded = true";
    private static final String CLIENT_MOD_MAIN = "clientLoaded = true";
    private static final String SERVER_MOD_MAIN = "serverLoaded = true";

    private static final String OK_MOD = "ok_mod";
    private static final String MISSING_MAIN_MOD = "missing_main";
    private static final String RUNTIME_ERROR_MOD = "runtime_error";
    private static final String ON_ENABLE_ERROR_MOD = "on_enable_error";
    private static final String OK_MOD_MAIN = "okLoaded = true";
    private static final String RUNTIME_ERROR_MAIN = "error('boom from main')";
    private static final String ON_ENABLE_ERROR_MAIN = "function mod.OnEnable() error('boom from enable') end";
    private static final String MANIFEST_FILE = "manifest.json";
    private static final String MAIN_FILE = "main.lua";
    private static final String DEPENDENT_ID = "dependent";

    @TempDir
    Path root;

    @Nested
    class UniversalHost {

        private ILuaEngine loadAllEnvironments() throws IOException {
            writeMod(UNIVERSAL_MOD, UNIVERSAL, UNVIVERSAL_MOD_MAIN);
            writeMod(CLIENT_MOD, CLIENT, CLIENT_MOD_MAIN);
            writeMod(SERVER_MOD, "server", SERVER_MOD_MAIN);
            ILuaEngine engine = LuaEngineFactory.create(false);
            LuaModManager.loadLuaMods(root.toFile(), engine, UNIVERSAL);
            return engine;
        }

        @Test
        void universalHostLoadsAllModEnvironmentsUniversalMod() throws IOException {
            loadAllEnvironments();
            assertTrue(LuaModManager.getLoadedMods().containsKey(UNIVERSAL_MOD),
                    "a universal host must load universal mods");
        }

        @Test
        void universalHostLoadsAllModEnvironmentsClientMod() throws IOException {
            loadAllEnvironments();
            assertTrue(LuaModManager.getLoadedMods().containsKey(CLIENT_MOD),
                    "a universal host must also load client mods");
        }

        @Test
        void universalHostLoadsAllModEnvironmentsServerMod() throws IOException {
            loadAllEnvironments();
            assertTrue(LuaModManager.getLoadedMods().containsKey(SERVER_MOD),
                    "a universal host must also load server mods");
        }

        @Test
        void universalHostLoadsAllModEnvironmentsUniversalLoaded() throws IOException {
            ILuaEngine engine = loadAllEnvironments();
            assertLuaBoolean(engine, "universalLoaded", true);
        }

        @Test
        void universalHostLoadsAllModEnvironmentsClientLoaded() throws IOException {
            ILuaEngine engine = loadAllEnvironments();
            assertLuaBoolean(engine, "clientLoaded", true);
        }

        @Test
        void universalHostLoadsAllModEnvironmentsServerLoaded() throws IOException {
            ILuaEngine engine = loadAllEnvironments();
            assertLuaBoolean(engine, "serverLoaded", true);
        }
    }

    @Nested
    class ClientHost {

        private ILuaEngine loadOnClient() throws IOException {
            writeMod(UNIVERSAL_MOD, UNIVERSAL, UNVIVERSAL_MOD_MAIN);
            writeMod(CLIENT_MOD, CLIENT, CLIENT_MOD_MAIN);
            writeMod(SERVER_MOD, "server", SERVER_MOD_MAIN);
            ILuaEngine engine = LuaEngineFactory.create(false);
            LuaModManager.loadLuaMods(root.toFile(), engine, CLIENT);
            return engine;
        }

        @Test
        void clientHostSkipsServerOnlyModsUniversalMod() throws IOException {
            loadOnClient();
            assertTrue(LuaModManager.getLoadedMods().containsKey(UNIVERSAL_MOD),
                    "a client host must load universal mods");
        }

        @Test
        void clientHostSkipsServerOnlyModsClientMod() throws IOException {
            loadOnClient();
            assertTrue(LuaModManager.getLoadedMods().containsKey(CLIENT_MOD),
                    "a client host must load client mods");
        }

        @Test
        void clientHostSkipsServerOnlyModsServerMod() throws IOException {
            loadOnClient();
            assertFalse(LuaModManager.getLoadedMods().containsKey(SERVER_MOD),
                    "a client host must skip server-only mods");
        }

        @Test
        void clientHostSkipsServerOnlyModsUniversalLoaded() throws IOException {
            ILuaEngine engine = loadOnClient();
            assertLuaBoolean(engine, "universalLoaded", true);
        }

        @Test
        void clientHostSkipsServerOnlyModsClientLoaded() throws IOException {
            ILuaEngine engine = loadOnClient();
            assertLuaBoolean(engine, "clientLoaded", true);
        }

        @Test
        void clientHostSkipsServerOnlyModsServerLoaded() throws IOException {
            ILuaEngine engine = loadOnClient();
            assertLuaBoolean(engine, "serverLoaded", false);
        }
    }

    @Nested
    class FailedMods {

        private void loadHealthyAndFailingMods() throws IOException {
            writeMod(OK_MOD, UNIVERSAL, OK_MOD_MAIN);
            writeManifestOnly(MISSING_MAIN_MOD, UNIVERSAL);
            writeMod(RUNTIME_ERROR_MOD, UNIVERSAL, RUNTIME_ERROR_MAIN);
            writeMod(ON_ENABLE_ERROR_MOD, UNIVERSAL, ON_ENABLE_ERROR_MAIN);
            ILuaEngine engine = LuaEngineFactory.create(false);
            LuaModManager.loadLuaMods(root.toFile(), engine, UNIVERSAL);
        }

        @Test
        void failedModsAreReportedAndNotMarkedLoadedOkMod() throws IOException {
            loadHealthyAndFailingMods();
            Map<String, LuaMod> loaded = LuaModManager.getLoadedMods();
            assertTrue(loaded.containsKey(OK_MOD),
                    "a healthy mod must stay loaded even when its siblings fail");
        }

        @Test
        void failedModsAreReportedAndNotMarkedLoadedMissingMain() throws IOException {
            loadHealthyAndFailingMods();
            Map<String, LuaMod> loaded = LuaModManager.getLoadedMods();
            assertFalse(loaded.containsKey(MISSING_MAIN_MOD),
                    "a mod without a main entrypoint must not be marked loaded");
        }

        @Test
        void failedModsAreReportedAndNotMarkedLoadedRuntimeError() throws IOException {
            loadHealthyAndFailingMods();
            Map<String, LuaMod> loaded = LuaModManager.getLoadedMods();
            assertFalse(loaded.containsKey(RUNTIME_ERROR_MOD),
                    "a mod whose main script errors must not be marked loaded");
        }

        @Test
        void failedModsAreReportedAndNotMarkedLoadedOnEnableError() throws IOException {
            loadHealthyAndFailingMods();
            Map<String, LuaMod> loaded = LuaModManager.getLoadedMods();
            assertFalse(loaded.containsKey(ON_ENABLE_ERROR_MOD),
                    "a mod whose OnEnable errors must not be marked loaded");
        }

        @Test
        void failedModsAreReportedAndNotMarkedLoadedMissingMain2() throws IOException {
            loadHealthyAndFailingMods();
            Map<String, String> errors = LuaModManager.getLoadErrors();
            assertTrue(errors.get(MISSING_MAIN_MOD).contains("Main entrypoint script not found"),
                    "the missing-main error must name the entrypoint problem: " + errors.get(MISSING_MAIN_MOD));
        }

        @Test
        void failedModsAreReportedAndNotMarkedLoadedRuntimeError2() throws IOException {
            loadHealthyAndFailingMods();
            Map<String, String> errors = LuaModManager.getLoadErrors();
            assertTrue(errors.get(RUNTIME_ERROR_MOD).contains("boom from main"),
                    "the runtime error must surface the script's own message: " + errors.get(RUNTIME_ERROR_MOD));
        }

        @Test
        void failedModsAreReportedAndNotMarkedLoadedOnEnableError2() throws IOException {
            loadHealthyAndFailingMods();
            Map<String, String> errors = LuaModManager.getLoadErrors();
            assertTrue(errors.get(ON_ENABLE_ERROR_MOD).contains("boom from enable"),
                    "the OnEnable error must surface the script's own message: "
                            + errors.get(ON_ENABLE_ERROR_MOD));
        }
    }

    @Nested
    class DependencyFailures {

        private void writeCycle() throws IOException {
            Path a = writeManifestOnly("cycle_a", UNIVERSAL);
            Path b = writeManifestOnly("cycle_b", UNIVERSAL);
            Files.writeString(a.resolve(MANIFEST_FILE),
                    "{\"id\":\"cycle_a\",\"environment\":\"universal\",\"dependencies\":[\"cycle_b\"]}");
            Files.writeString(b.resolve(MANIFEST_FILE),
                    "{\"id\":\"cycle_b\",\"environment\":\"universal\",\"dependencies\":[\"cycle_a\"]}");
            Files.writeString(a.resolve(MAIN_FILE), "cycleA = true");
            Files.writeString(b.resolve(MAIN_FILE), "cycleB = true");
        }

        @Test
        void missingDependencyPreventsDependentFromLoadingDependent() {
            LuaModManager.loadLuaMods(root.toFile(), LuaEngineFactory.create(false), UNIVERSAL);
            assertFalse(LuaModManager.getLoadedMods().containsKey(DEPENDENT_ID),
                    "a mod directory that does not exist must not be reported as loaded");
        }

        @Test
        void missingDependencyPreventsDependentFromLoadingDependent2() throws IOException {
            Path modDir = writeManifestOnly(DEPENDENT_ID, UNIVERSAL);
            Files.writeString(modDir.resolve(MANIFEST_FILE),
                    "{\"id\":\"dependent\",\"environment\":\"universal\",\"dependencies\":[\"missing\"]}");
            Files.writeString(modDir.resolve(MAIN_FILE), "dependentLoaded = true");
            LuaModManager.loadLuaMods(root.toFile(), LuaEngineFactory.create(false), UNIVERSAL);
            assertTrue(LuaModManager.getLoadErrors().get(DEPENDENT_ID).contains("missing"),
                    "the skip reason must name the missing dependency: "
                            + LuaModManager.getLoadErrors().get(DEPENDENT_ID));
        }

        @Test
        void circularDependenciesDoNotPartiallyLoadCycleA() throws IOException {
            writeCycle();
            LuaModManager.loadLuaMods(root.toFile(), LuaEngineFactory.create(false), UNIVERSAL);
            assertFalse(LuaModManager.getLoadedMods().containsKey("cycle_a"),
                    "no member of a dependency cycle may be loaded");
        }

        @Test
        void circularDependenciesDoNotPartiallyLoadCycleB() throws IOException {
            writeCycle();
            LuaModManager.loadLuaMods(root.toFile(), LuaEngineFactory.create(false), UNIVERSAL);
            assertFalse(LuaModManager.getLoadedMods().containsKey("cycle_b"),
                    "the other member of the cycle must not be loaded either");
        }

        @Test
        void circularDependenciesDoNotPartiallyLoadGetLoadErrors() throws IOException {
            writeCycle();
            LuaModManager.loadLuaMods(root.toFile(), LuaEngineFactory.create(false), UNIVERSAL);
            assertEquals(2, LuaModManager.getLoadErrors().size(),
                    "both cycle members must report a load error");
        }
    }

    @Nested
    class LoadObserver {

        @Test
        void loadObserverFollowsTheDependencySortedOrder() throws IOException {
            writeMod("zeta_base", UNIVERSAL, "zetaLoaded = true");
            writeModWithDependencies("alpha_child", UNIVERSAL, "alphaLoaded = true", "zeta_base");
            RecordingObserver observer = new RecordingObserver();

            LuaModManager.loadLuaMods(root.toFile(), LuaEngineFactory.create(false), UNIVERSAL, observer);

            assertEquals(List.of(
                    "1:zeta_base",
                    "2:alpha_child",
                    "done:2/2"), observer.events,
                    "the observer must see the dependency first, then the dependent, then the summary");
        }

        @Test
        void loadObserverCountsOnlyModsThatActuallyLoaded() throws IOException {
            writeMod(OK_MOD, UNIVERSAL, OK_MOD_MAIN);
            writeMod("broken_mod", UNIVERSAL, "error('observer boom')");
            RecordingObserver observer = new RecordingObserver();

            LuaModManager.loadLuaMods(root.toFile(), LuaEngineFactory.create(false), UNIVERSAL, observer);

            assertEquals(List.of(
                    "1:broken_mod",
                    "2:ok_mod",
                    "done:1/2"), observer.events,
                    "the completion callback must count only mods that really loaded");
        }
    }

    private void writeMod(String id, String env, String mainLua) throws IOException {
        Path modDir = writeManifestOnly(id, env);
        Files.writeString(modDir.resolve(MAIN_FILE), mainLua);
    }

    private void writeModWithDependencies(String id, String env, String mainLua, String... dependencies)
            throws IOException {
        Path modDir = Files.createDirectories(root.resolve("luamods").resolve(id));
        String deps = "[\"" + String.join("\",\"", dependencies) + "\"]";
        Files.writeString(modDir.resolve(MANIFEST_FILE),
                "{\"id\":\"" + id + "\",\"version\":\"1\",\"main\":\"main.lua\",\"environment\":\""
                        + env + "\",\"dependencies\":" + deps + "}");
        Files.writeString(modDir.resolve(MAIN_FILE), mainLua);
    }

    private Path writeManifestOnly(String id, String env) throws IOException {
        Path modDir = Files.createDirectories(root.resolve("luamods").resolve(id));
        Files.writeString(modDir.resolve(MANIFEST_FILE),
                "{\"id\":\"" + id + "\",\"version\":\"1\",\"main\":\"main.lua\",\"environment\":\"" + env + "\"}");
        return modDir;
    }

    private static void assertLuaBoolean(ILuaEngine engine, String globalName, boolean expected) {
        boolean actual = Boolean.TRUE.equals(engine.getGlobalEnvironment().rawget(globalName).toJavaObject());
        assertEquals(expected, actual, "the global '" + globalName + "' must be " + expected);
    }

    private static final class RecordingObserver implements LuaModLoadObserver {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onModStart(int index, int total, String modId) {
            events.add(index + ":" + modId);
        }

        @Override
        public void onLoadComplete(int loadedMods, int total) {
            events.add("done:" + loadedMods + "/" + total);
        }
    }
}

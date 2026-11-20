package com.luatweaker.core.mod;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LuaModManagerLoadStateTest {
    @TempDir Path root;

    @Test
    void universalHostLoadsAllModEnvironments() throws Exception {
        writeMod("universal_mod", "universal", "universalLoaded = true");
        writeMod("client_mod", "client", "clientLoaded = true");
        writeMod("server_mod", "server", "serverLoaded = true");

        ILuaEngine engine = LuaEngineFactory.create(false);
        LuaModManager.loadLuaMods(root.toFile(), engine, "universal");

        assertTrue(LuaModManager.getLoadedMods().containsKey("universal_mod"));
        assertTrue(LuaModManager.getLoadedMods().containsKey("client_mod"));
        assertTrue(LuaModManager.getLoadedMods().containsKey("server_mod"));
        assertLuaBoolean(engine, "universalLoaded", true);
        assertLuaBoolean(engine, "clientLoaded", true);
        assertLuaBoolean(engine, "serverLoaded", true);
    }

    @Test
    void clientHostSkipsServerOnlyMods() throws Exception {
        writeMod("universal_mod", "universal", "universalLoaded = true");
        writeMod("client_mod", "client", "clientLoaded = true");
        writeMod("server_mod", "server", "serverLoaded = true");

        ILuaEngine engine = LuaEngineFactory.create(false);
        LuaModManager.loadLuaMods(root.toFile(), engine, "client");

        assertTrue(LuaModManager.getLoadedMods().containsKey("universal_mod"));
        assertTrue(LuaModManager.getLoadedMods().containsKey("client_mod"));
        assertFalse(LuaModManager.getLoadedMods().containsKey("server_mod"));
        assertLuaBoolean(engine, "universalLoaded", true);
        assertLuaBoolean(engine, "clientLoaded", true);
        assertLuaBoolean(engine, "serverLoaded", false);
    }

    @Test
    void failedModsAreReportedAndNotMarkedLoaded() throws Exception {
        writeMod("ok_mod", "universal", "okLoaded = true");
        writeManifestOnly("missing_main", "universal");
        writeMod("runtime_error", "universal", "error('boom from main')");
        writeMod("on_enable_error", "universal", "function mod.OnEnable() error('boom from enable') end");

        ILuaEngine engine = LuaEngineFactory.create(false);
        LuaModManager.loadLuaMods(root.toFile(), engine, "universal");

        Map<String, LuaMod> loaded = LuaModManager.getLoadedMods();
        Map<String, String> errors = LuaModManager.getLoadErrors();

        assertTrue(loaded.containsKey("ok_mod"));
        assertFalse(loaded.containsKey("missing_main"));
        assertFalse(loaded.containsKey("runtime_error"));
        assertFalse(loaded.containsKey("on_enable_error"));
        assertTrue(errors.get("missing_main").contains("Main entrypoint script not found"));
        assertTrue(errors.get("runtime_error").contains("boom from main"));
        assertTrue(errors.get("on_enable_error").contains("boom from enable"));
    }

    @Test
    void missingDependencyPreventsDependentFromLoading() throws Exception {
        Path modDir = writeManifestOnly("dependent", "universal");
        Files.writeString(modDir.resolve("manifest.json"),
                "{\"id\":\"dependent\",\"environment\":\"universal\",\"dependencies\":[\"missing\"]}");
        Files.writeString(modDir.resolve("main.lua"), "dependentLoaded = true");

        ILuaEngine engine = LuaEngineFactory.create(false);
        LuaModManager.loadLuaMods(root.toFile(), engine, "universal");

        assertFalse(LuaModManager.getLoadedMods().containsKey("dependent"));
        assertTrue(LuaModManager.getLoadErrors().get("dependent").contains("missing"));
    }

    @Test
    void circularDependenciesDoNotPartiallyLoad() throws Exception {
        Path a = writeManifestOnly("cycle_a", "universal");
        Path b = writeManifestOnly("cycle_b", "universal");
        Files.writeString(a.resolve("manifest.json"), "{\"id\":\"cycle_a\",\"environment\":\"universal\",\"dependencies\":[\"cycle_b\"]}");
        Files.writeString(b.resolve("manifest.json"), "{\"id\":\"cycle_b\",\"environment\":\"universal\",\"dependencies\":[\"cycle_a\"]}");
        Files.writeString(a.resolve("main.lua"), "cycleA = true");
        Files.writeString(b.resolve("main.lua"), "cycleB = true");

        LuaModManager.loadLuaMods(root.toFile(), LuaEngineFactory.create(false), "universal");

        assertFalse(LuaModManager.getLoadedMods().containsKey("cycle_a"));
        assertFalse(LuaModManager.getLoadedMods().containsKey("cycle_b"));
        assertEquals(2, LuaModManager.getLoadErrors().size());
    }

    @Test
    void loadObserverFollowsTheDependencySortedOrder() throws Exception {
        writeMod("zeta_base", "universal", "zetaLoaded = true");
        writeModWithDependencies("alpha_child", "universal", "alphaLoaded = true", "zeta_base");
        RecordingObserver observer = new RecordingObserver();

        LuaModManager.loadLuaMods(root.toFile(), LuaEngineFactory.create(false), "universal", observer);

        assertEquals(List.of(
                "1:zeta_base",
                "2:alpha_child",
                "done:2/2"), observer.events);
    }

    @Test
    void loadObserverCountsOnlyModsThatActuallyLoaded() throws Exception {
        writeMod("ok_mod", "universal", "okLoaded = true");
        writeMod("broken_mod", "universal", "error('observer boom')");
        RecordingObserver observer = new RecordingObserver();

        LuaModManager.loadLuaMods(root.toFile(), LuaEngineFactory.create(false), "universal", observer);

        assertEquals(List.of(
                "1:broken_mod",
                "2:ok_mod",
                "done:1/2"), observer.events);
    }

    private void writeMod(String id, String env, String mainLua) throws Exception {
        Path modDir = writeManifestOnly(id, env);
        Files.writeString(modDir.resolve("main.lua"), mainLua);
    }

    private void writeModWithDependencies(String id, String env, String mainLua, String... dependencies)
            throws Exception {
        Path modDir = Files.createDirectories(root.resolve("luamods").resolve(id));
        String deps = "[\"" + String.join("\",\"", dependencies) + "\"]";
        Files.writeString(modDir.resolve("manifest.json"),
                "{\"id\":\"" + id + "\",\"version\":\"1\",\"main\":\"main.lua\",\"environment\":\""
                        + env + "\",\"dependencies\":" + deps + "}");
        Files.writeString(modDir.resolve("main.lua"), mainLua);
    }

    private Path writeManifestOnly(String id, String env) throws Exception {
        Path modDir = Files.createDirectories(root.resolve("luamods").resolve(id));
        Files.writeString(modDir.resolve("manifest.json"),
                "{\"id\":\"" + id + "\",\"version\":\"1\",\"main\":\"main.lua\",\"environment\":\"" + env + "\"}");
        return modDir;
    }

    private static void assertLuaBoolean(ILuaEngine engine, String globalName, boolean expected) {
        boolean actual = Boolean.TRUE.equals(engine.getGlobalEnvironment().rawget(globalName).toJavaObject());
        assertEquals(expected, actual, globalName);
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

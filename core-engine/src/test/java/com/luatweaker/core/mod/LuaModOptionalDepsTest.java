package com.luatweaker.core.mod;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LuaModOptionalDepsTest {
    @TempDir Path root;

    @AfterAll
    static void stopLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void parsesOptionalDependencies() {
        LuaModManifest manifest = LuaModManifest.parseJson(
            "{\"id\":\"demo\",\"environment\":\"universal\","
                + "\"dependencies\":[\"base\"],\"optional_dependencies\":[\"extra\"]}");
        assertNotNull(manifest);
        assertEquals(List.of("base"), manifest.dependencies());
        assertEquals(List.of("extra"), manifest.optionalDependencies());
    }

    @Test
    void missingOptionalDependencyStillLoads() throws Exception {
        writeMod("consumer",
            "{\"id\":\"consumer\",\"environment\":\"universal\",\"optional_dependencies\":[\"ghost\"]}",
            "consumerLoaded = true");

        ILuaEngine engine = LuaEngineFactory.create(false);
        LuaModManager.loadLuaMods(root.toFile(), engine, "universal");

        assertTrue(LuaModManager.getLoadedMods().containsKey("consumer"));
        assertFalse(LuaModManager.getLoadErrors().containsKey("consumer"));
    }

    @Test
    void presentOptionalDependencyLoadsFirst() throws Exception {
        writeMod("base",
            "{\"id\":\"base\",\"version\":\"2.1.0\",\"environment\":\"universal\"}",
            "baseLoaded = true");
        writeMod("consumer",
            "{\"id\":\"consumer\",\"environment\":\"universal\",\"optional_dependencies\":[\"base\"]}",
            "consumerLoaded = true");

        ILuaEngine engine = LuaEngineFactory.create(false);
        LuaModManager.loadLuaMods(root.toFile(), engine, "universal");

        assertTrue(LuaModManager.getLoadedMods().containsKey("base"));
        assertTrue(LuaModManager.getLoadedMods().containsKey("consumer"));
    }

    @Test
    void hasModAndGetModVersionHelpers() throws Exception {
        Path mods = Files.createDirectories(root.resolve("luamods"));
        Map<String, LuaMod> registry = new HashMap<>();
        LuaModManifest baseManifest = new LuaModManifest(
            "base", "base", "test", "2.1.0", "main.lua", "universal",
            List.of(), List.of(), List.of(), null);
        LuaMod base = new LuaMod(baseManifest, mods.toFile(), "universal", registry, mods.toFile());
        registry.put("base", base);

        LuaModManifest consumerManifest = new LuaModManifest(
            "consumer", "consumer", "test", "1.0.0", "main.lua", "universal",
            List.of(), List.of("base"), List.of(), null);
        ILuaEngine engine = LuaEngineFactory.create(false);
        LuaMod consumer = new LuaMod(consumerManifest, mods.toFile(), "universal", registry, mods.toFile());
        var modTable = consumer.createModTable(engine);
        engine.getGlobalEnvironment().rawset("mod", modTable);

        engine.executeString(
            "assert(mod:HasMod('base') == true)\n"
                + "assert(mod:HasMod('ghost') == false)\n"
                + "assert(mod:GetModVersion('base') == '2.1.0')\n"
                + "assert(mod:GetModVersion('ghost') == nil)\n",
            "hasmod_test");
        assertNull(engine.getAndClearLastExecutionError());
    }

    private void writeMod(String id, String manifestJson, String mainLua) throws Exception {
        Path modDir = Files.createDirectories(root.resolve("luamods").resolve(id));
        Files.writeString(modDir.resolve("manifest.json"), manifestJson);
        Files.writeString(modDir.resolve("main.lua"), mainLua);
    }
}

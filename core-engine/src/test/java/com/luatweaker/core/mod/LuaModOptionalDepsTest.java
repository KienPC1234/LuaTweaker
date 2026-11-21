package com.luatweaker.core.mod;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class LuaModOptionalDepsTest {

    private static final String UNIVERSAL = "universal";
    private static final String BASE_ID = "base";
    private static final String CONSUMER_ID = "consumer";
    private static final String CONSUMER_MAIN = "consumerLoaded = true";

    private static final String OPTIONAL_FIELDS_MANIFEST = "{\"id\":\"demo\",\"environment\":\"universal\","
            + "\"dependencies\":[\"base\"],\"optional_dependencies\":[\"extra\"]}";

    private static final String GHOST_OPTIONAL_MANIFEST = "{\"id\":\"consumer\",\"environment\":\"universal\","
            + "\"optional_dependencies\":[\"ghost\"]}";

    private static final String BASE_2_1_0_MANIFEST =
            "{\"id\":\"base\",\"version\":\"2.1.0\",\"environment\":\"universal\"}";

    private static final String OPTIONAL_BASE_MANIFEST = "{\"id\":\"consumer\",\"environment\":\"universal\","
            + "\"optional_dependencies\":[\"base\"]}";

    @TempDir
    Path root;

    @AfterAll
    static void stopLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine load() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        LuaModManager.loadLuaMods(root.toFile(), engine, UNIVERSAL);
        return engine;
    }

    private void writeMod(String id, String manifestJson, String mainLua) throws java.io.IOException {
        Path modDir = Files.createDirectories(root.resolve("luamods").resolve(id));
        Files.writeString(modDir.resolve("manifest.json"), manifestJson);
        Files.writeString(modDir.resolve("main.lua"), mainLua);
    }

    @Nested
    class ManifestFields {

        @Test
        void parsesOptionalDependenciesCheck() {
            LuaModManifest manifest = LuaModManifest.parseJson(OPTIONAL_FIELDS_MANIFEST);
            assertNotNull(manifest, "a manifest carrying optional_dependencies must parse");
        }

        @Test
        void parsesOptionalDependenciesBase() {
            LuaModManifest manifest = LuaModManifest.parseJson(OPTIONAL_FIELDS_MANIFEST);
            assertEquals(List.of(new ModDependency("base", null, null, false)), manifest.dependencies(),
                    "a plain hard dependency must be reported as required");
        }

        @Test
        void parsesOptionalDependenciesExtra() {
            LuaModManifest manifest = LuaModManifest.parseJson(OPTIONAL_FIELDS_MANIFEST);
            assertEquals(List.of(new ModDependency("extra", null, null, true)), manifest.optionalDependencies(),
                    "a plain optional dependency must be reported as optional");
        }
    }

    @Nested
    class LoadBehaviour {

        @Test
        void missingOptionalDependencyStillLoadsConsumer() throws java.io.IOException {
            writeMod(CONSUMER_ID, GHOST_OPTIONAL_MANIFEST, CONSUMER_MAIN);
            load();
            assertTrue(LuaModManager.getLoadedMods().containsKey(CONSUMER_ID),
                    "an absent optional dependency must not block the consumer");
        }

        @Test
        void missingOptionalDependencyStillLoadsConsumer2() throws java.io.IOException {
            writeMod(CONSUMER_ID, GHOST_OPTIONAL_MANIFEST, CONSUMER_MAIN);
            load();
            assertFalse(LuaModManager.getLoadErrors().containsKey(CONSUMER_ID),
                    "an absent optional dependency must not record a load error");
        }

        @Test
        void presentOptionalDependencyLoadsFirstBase() throws java.io.IOException {
            writeMod(BASE_ID, BASE_2_1_0_MANIFEST, "baseLoaded = true");
            writeMod(CONSUMER_ID, OPTIONAL_BASE_MANIFEST, CONSUMER_MAIN);
            load();
            assertTrue(LuaModManager.getLoadedMods().containsKey(BASE_ID),
                    "the provider of an optional dependency must load when it is installed");
        }

        @Test
        void presentOptionalDependencyLoadsFirstConsumer() throws java.io.IOException {
            writeMod(BASE_ID, BASE_2_1_0_MANIFEST, "baseLoaded = true");
            writeMod(CONSUMER_ID, OPTIONAL_BASE_MANIFEST, CONSUMER_MAIN);
            load();
            assertTrue(LuaModManager.getLoadedMods().containsKey(CONSUMER_ID),
                    "the consumer of an installed optional dependency must load too");
        }
    }

    @Nested
    class ScriptApi {

        @Test
        void hasModAndGetModVersionHelpers() throws java.io.IOException {
            Path mods = Files.createDirectories(root.resolve("luamods"));
            Map<String, LuaMod> registry = new ConcurrentHashMap<>();
            LuaModManifest baseManifest = new LuaModManifest(
                    BASE_ID, BASE_ID, "test", "2.1.0", "main.lua", UNIVERSAL,
                    List.of(), List.of(), List.of(), null);
            LuaMod base = new LuaMod(baseManifest, mods.toFile(), UNIVERSAL, registry, mods.toFile());
            registry.put(BASE_ID, base);

            LuaModManifest consumerManifest = new LuaModManifest(
                    CONSUMER_ID, CONSUMER_ID, "test", "1.0.0", "main.lua", UNIVERSAL,
                    List.of(), List.of("base"), List.of(), null);
            ILuaEngine engine = LuaEngineFactory.create(false);
            LuaMod consumer = new LuaMod(consumerManifest, mods.toFile(), UNIVERSAL, registry, mods.toFile());
            var modTable = consumer.createModTable(engine);
            engine.getGlobalEnvironment().rawset("mod", modTable);

            engine.executeString(
                    "assert(mod:HasMod('base') == true)\n"
                            + "assert(mod:HasMod('ghost') == false)\n"
                            + "assert(mod:GetModVersion('base') == '2.1.0')\n"
                            + "assert(mod:GetModVersion('ghost') == nil)\n",
                    "hasmod_test");
            assertNull(engine.getAndClearLastExecutionError(),
                    "HasMod/GetModVersion must answer for installed and unknown mod ids");
        }
    }
}

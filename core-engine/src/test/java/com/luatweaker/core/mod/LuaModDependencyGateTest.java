package com.luatweaker.core.mod;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dependency gates: version windows, platform conditions, and installed-mod
 * requirements skip the load with a recorded reason instead of crashing.
 */
class LuaModDependencyGateTest {

    private static final String UNIVERSAL = "universal";
    private static final String BASE_ID = "base";
    private static final String CONSUMER_ID = "consumer";
    private static final String GATED_ID = "gated";
    private static final String NEEDS_JEI_ID = "needs_jei";
    private static final String SOFT_JEI_ID = "soft_jei";
    private static final String ENGINE_VERSION = "1.0.0";

    private static final String BASE_MAIN = "baseLoaded = true";
    private static final String CONSUMER_MAIN = "consumerLoaded = true";
    private static final String GATED_MAIN = "gatedLoaded = true";
    private static final String NEEDS_JEI_MAIN = "needsJeiLoaded = true";
    private static final String SOFT_JEI_MAIN = "softJeiLoaded = true";

    private static final String BASE_NEWER_MANIFEST =
            "{\"id\":\"base\",\"version\":\"2.0.0\",\"environment\":\"universal\"}";

    private static final String CONSUMER_TOO_OLD_MANIFEST = "{\"id\":\"consumer\",\"environment\":\"universal\","
            + "\"dependencies\":[{\"id\":\"base\",\"max_version\":\"1.9.9\"}]}";

    private static final String CONSUMER_RANGE_MANIFEST = "{\"id\":\"consumer\",\"environment\":\"universal\","
            + "\"dependencies\":[{\"id\":\"base\",\"min_version\":\"1.0.0\",\"max_version\":\"2.0.0\"}]}";

    private static final String BASE_RANGE_MANIFEST =
            "{\"id\":\"base\",\"version\":\"1.5.0\",\"environment\":\"universal\"}";

    private static final String GATED_MANIFEST =
            "{\"id\":\"gated\",\"environment\":\"universal\",\"luatweaker\":{\"min_version\":\"9.9.9\"}}";

    private static final String GATED_UNKNOWN_VERSIONS_MANIFEST = "{\"id\":\"gated\",\"environment\":\"universal\","
            + "\"luatweaker\":{\"min_version\":\"1.0.0\"},"
            + "\"minecraft\":{\"min_version\":\"1.21\",\"max_version\":\"1.21.1\"}}";

    private static final String NEEDS_JEI_MANIFEST = "{\"id\":\"needs_jei\",\"environment\":\"universal\","
            + "\"mods\":[{\"id\":\"jei\",\"min_version\":\"20.0.0\"}]}";

    private static final String SOFT_JEI_MANIFEST = "{\"id\":\"soft_jei\",\"environment\":\"universal\","
            + "\"mods\":[{\"id\":\"jei\",\"min_version\":\"20.0.0\",\"optional\":true}]}";

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

    private ILuaEngine load(Path modsRoot) {
        ILuaEngine engine = LuaEngineFactory.create(false);
        LuaModManager.loadLuaMods(modsRoot.toFile(), engine, UNIVERSAL);
        return engine;
    }

    /** Base 2.0.0 plus a consumer that only accepts base up to 1.9.9. */
    private ILuaEngine loadConsumerWithTooOldBaseRange() throws java.io.IOException {
        writeMod(BASE_ID, BASE_NEWER_MANIFEST, BASE_MAIN);
        writeMod(CONSUMER_ID, CONSUMER_TOO_OLD_MANIFEST, CONSUMER_MAIN);
        return load(root);
    }

    /** Consumer asking for base in [1.0.0, 2.0.0] with base 1.5.0 installed. */
    private ILuaEngine loadSatisfiedRange() throws java.io.IOException {
        writeMod(CONSUMER_ID, CONSUMER_RANGE_MANIFEST, CONSUMER_MAIN);
        writeMod(BASE_ID, BASE_RANGE_MANIFEST, BASE_MAIN);
        return load(root);
    }

    /** A single mod gated on a luatweaker version far above the injected 1.0.0. */
    private ILuaEngine loadEngineGatedMod() throws java.io.IOException {
        LuaModManager.setEngineVersion(ENGINE_VERSION);
        writeMod(GATED_ID, GATED_MANIFEST, GATED_MAIN);
        return load(root);
    }

    /** A single mod whose gates cannot be checked because no versions are injected. */
    private ILuaEngine loadGatedModWithUnknownVersions() throws java.io.IOException {
        writeMod(GATED_ID, GATED_UNKNOWN_VERSIONS_MANIFEST, GATED_MAIN);
        return load(root);
    }

    /** One required and one optional platform dependency on jei, installed below their minimum. */
    private ILuaEngine loadPlatformDependenciesOnJei() throws java.io.IOException {
        LuaModManager.setInstalledMods(Map.of("jei", "19.1.0"));
        writeMod(NEEDS_JEI_ID, NEEDS_JEI_MANIFEST, NEEDS_JEI_MAIN);
        writeMod(SOFT_JEI_ID, SOFT_JEI_MANIFEST, SOFT_JEI_MAIN);
        return load(root);
    }

    @Nested
    class DependencyVersionWindows {

        @Test
        void versionMismatchSkipsConsumerButKeepsBaseBase() throws java.io.IOException {
            loadConsumerWithTooOldBaseRange();
            assertTrue(LuaModManager.getLoadedMods().containsKey(BASE_ID),
                    "the base mod itself satisfies every gate and must load");
        }

        @Test
        void versionMismatchSkipsConsumerButKeepsBaseConsumer() throws java.io.IOException {
            loadConsumerWithTooOldBaseRange();
            assertFalse(LuaModManager.getLoadedMods().containsKey(CONSUMER_ID),
                    "a consumer whose base requirement is violated must be skipped");
        }

        @Test
        void versionMismatchSkipsConsumerButKeepsBaseSkipMustRecordAReason() throws java.io.IOException {
            loadConsumerWithTooOldBaseRange();
            assertNotNull(LuaModManager.getLoadErrors().get(CONSUMER_ID),
                    "a skipped consumer must record why it was not loaded");
        }

        @Test
        void versionMismatchSkipsConsumerButKeepsBaseNotSatisfied() throws java.io.IOException {
            loadConsumerWithTooOldBaseRange();
            String reason = LuaModManager.getLoadErrors().get(CONSUMER_ID);
            assertTrue(reason.contains("not satisfied"),
                    "the skip reason must state that the version window is not satisfied: " + reason);
        }

        @Test
        void satisfiedRangeLoadsBothInOrderBase() throws java.io.IOException {
            loadSatisfiedRange();
            assertTrue(LuaModManager.getLoadedMods().containsKey(BASE_ID),
                    "a base inside the requested window must load");
        }

        @Test
        void satisfiedRangeLoadsBothInOrderConsumer() throws java.io.IOException {
            loadSatisfiedRange();
            assertTrue(LuaModManager.getLoadedMods().containsKey(CONSUMER_ID),
                    "a consumer whose window accepts base must load");
        }

        @Test
        void satisfiedRangeLoadsBothInOrderConsumer2() throws java.io.IOException {
            loadSatisfiedRange();
            assertFalse(LuaModManager.getLoadErrors().containsKey(CONSUMER_ID),
                    "a satisfied window must not record any load error for the consumer");
        }
    }

    @Nested
    class PlatformGates {

        @Test
        void engineVersionMismatchSkipsWithClearReasonGated() throws java.io.IOException {
            loadEngineGatedMod();
            assertFalse(LuaModManager.getLoadedMods().containsKey(GATED_ID),
                    "a mod gated on a newer LuaTweaker must be skipped");
        }

        @Test
        void engineVersionMismatchSkipsWithClearReasonCheck() throws java.io.IOException {
            loadEngineGatedMod();
            assertNotNull(LuaModManager.getLoadErrors().get(GATED_ID),
                    "a LuaTweaker-gated skip must record a reason");
        }

        @Test
        void engineVersionMismatchSkipsWithClearReasonLuaTweaker() throws java.io.IOException {
            loadEngineGatedMod();
            String reason = LuaModManager.getLoadErrors().get(GATED_ID);
            assertTrue(reason.contains("LuaTweaker"),
                    "the skip reason must name the LuaTweaker engine gate: " + reason);
        }

        @Test
        void unknownVersionsFailOpenGated() throws java.io.IOException {
            // No environment injected (unit-test default): gated mods must load, not
            // mass-skip, so tests and datagen never trip on version gates.
            loadGatedModWithUnknownVersions();
            assertTrue(LuaModManager.getLoadedMods().containsKey(GATED_ID),
                    "unknown platform versions must fail open and load the gated mod");
        }

        @Test
        void unknownVersionsFailOpenGated2() throws java.io.IOException {
            // No environment injected (unit-test default): gated mods must load, not
            // mass-skip, so tests and datagen never trip on version gates.
            loadGatedModWithUnknownVersions();
            assertFalse(LuaModManager.getLoadErrors().containsKey(GATED_ID),
                    "failing open must not record a version-gate error");
        }

        @Test
        void missingPlatformModSkipsWhileOptionalOnlyWarnsNeedsJei() throws java.io.IOException {
            loadPlatformDependenciesOnJei();
            assertFalse(LuaModManager.getLoadedMods().containsKey(NEEDS_JEI_ID),
                    "a required platform dependency below its minimum must skip the mod");
        }

        @Test
        void missingPlatformModSkipsWhileOptionalOnlyWarnsNeedsJei2() throws java.io.IOException {
            loadPlatformDependenciesOnJei();
            String reason = LuaModManager.getLoadErrors().get(NEEDS_JEI_ID);
            assertTrue(reason.contains("jei"),
                    "the skip reason must name the missing platform mod: " + reason);
        }

        @Test
        void missingPlatformModSkipsWhileOptionalOnlyWarnsSoftJei() throws java.io.IOException {
            loadPlatformDependenciesOnJei();
            assertTrue(LuaModManager.getLoadedMods().containsKey(SOFT_JEI_ID),
                    "an optional platform dependency below its minimum must warn, not skip");
        }
    }

    @Nested
    class CheckDependencyApi {

        @Test
        void checkDependencyApiHonorsVersionWindows() throws java.io.IOException {
            Path mods = Files.createDirectories(root.resolve("luamods"));
            Map<String, LuaMod> registry = new ConcurrentHashMap<>();
            LuaModManifest baseManifest = new LuaModManifest(
                    BASE_ID, BASE_ID, "test", "1.5.0", "main.lua", UNIVERSAL,
                    List.of(), List.of(), List.of(), null);
            LuaMod base = new LuaMod(baseManifest, mods.toFile(), UNIVERSAL, registry, mods.toFile());
            registry.put(BASE_ID, base);

            LuaModManifest consumerManifest = new LuaModManifest(
                    CONSUMER_ID, CONSUMER_ID, "test", "1.0.0", "main.lua", UNIVERSAL,
                    List.of(), List.of(), List.of(), null);
            ILuaEngine engine = LuaEngineFactory.create(false);
            LuaMod consumer = new LuaMod(consumerManifest, mods.toFile(), UNIVERSAL, registry, mods.toFile());
            var modTable = consumer.createModTable(engine);
            engine.getGlobalEnvironment().rawset("mod", modTable);

            engine.executeString(
                    "assert(mod:CheckDependency('base') == true)\n"
                            + "assert(mod:CheckDependency('base', '1.0.0', '2.0.0') == true)\n"
                            + "assert(mod:CheckDependency('base', '2.0.0') == false)\n"
                            + "assert(mod:CheckDependency('base', nil, '1.0.0') == false)\n"
                            + "assert(mod:CheckDependency('ghost', '1.0.0') == false)\n"
                            + "assert(mod:checkDependency('base', '1.0.0') == true, 'lowercase alias')\n",
                    "checkdependency_test");
            assertNull(engine.getAndClearLastExecutionError(),
                    "CheckDependency must honour min/max windows and answer false for unknown ids");
        }
    }

    private void writeMod(String id, String manifestJson, String mainLua) throws java.io.IOException {
        Path modDir = Files.createDirectories(root.resolve("luamods").resolve(id));
        Files.writeString(modDir.resolve("manifest.json"), manifestJson);
        Files.writeString(modDir.resolve("main.lua"), mainLua);
    }
}

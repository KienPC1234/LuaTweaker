package com.luatweaker.core.mod;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LuaModSecurityTest {

    @TempDir
    Path root;

    @AfterAll
    static void stopLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private LuaModManifest manifest(String id, String main) {
        return new LuaModManifest(id, id, "test", "1", main, "universal",
                List.of(), List.of(), List.of(), null);
    }

    @Nested
    class IdSafety {

        @Test
        void rejectsUnsafeIdsBeforeTheyCanWriteConfigRejectsExternal() throws Exception {
            Path sentinel = root.resolve("external.json");
            Files.writeString(sentinel, "{\"keep\":true}");
            for (String id : List.of("../external", "..\\external", "/absolute", "C:\\absolute",
                    "a/b", "a\\b", "a:stream", "a.", "CON", "nul", "COM1", "LPT9", "..", " ")) {
                assertThrows(IllegalArgumentException.class, () -> manifest(id, "main.lua"), id);
            }
        }

        @Test
        void rejectsUnsafeIdsBeforeTheyCanWriteConfigKeepTrue() throws IOException {
            Path sentinel = root.resolve("external.json");
            Files.writeString(sentinel, "{\"keep\":true}");
            assertEquals("{\"keep\":true}", Files.readString(sentinel),
                    "rejected ids must never rewrite a file outside the mod sandbox");
        }
    }

    @Nested
    class EntrypointSafety {

        @Test
        void rejectsUnsafeEntrypointsRejectsMainLua() {
            for (String main : List.of("../main.lua", "src/../../main.lua", "C:/main.lua",
                    "/main.lua", "src\\main.lua", "src//main.lua")) {
                assertThrows(IllegalArgumentException.class, () -> manifest("safe_mod", main), main);
            }
        }

        @Test
        void rejectsUnsafeEntrypointsSrcMainLua() {
            assertEquals("src/main.lua", manifest("safe_mod", "src/main.lua").main(),
                    "a nested entrypoint inside the mod directory must stay accepted");
        }
    }

    @Nested
    class SymlinkEscape {

        @Test
        void rejectsSymlinkEscape() throws IOException {
            Path modRoot = Files.createDirectories(root.resolve("mod"));
            Path outside = Files.createDirectories(root.resolve("outside"));
            Files.writeString(outside.resolve("secret.lua"), "return true");
            Path link = modRoot.resolve("link.lua");
            assumeSymbolicLinksSupported(link, outside.resolve("secret.lua"));
            assertThrows(IllegalArgumentException.class,
                    () -> LuaModPathSecurity.resolveContained(modRoot, "link.lua"),
                    "a symlink pointing outside the mod directory must be rejected");
        }

        private static void assumeSymbolicLinksSupported(Path link, Path target) throws IOException {
            try {
                Files.createSymbolicLink(link, target);
            } catch (UnsupportedOperationException | FileSystemException e) {
                Assumptions.abort("Symbolic links unavailable: " + e.getMessage());
            }
        }
    }

    @Nested
    class ConfigWrite {

        @Test
        void validIdWritesAndMergesOnlyItsConfigValue1() throws IOException {
            Path mods = Files.createDirectories(root.resolve("luamods"));
            LuaMod mod = new LuaMod(manifest("example_mod-2", "main.lua"), mods.toFile(),
                    "universal", new HashMap<>(), mods.toFile());
            mod.ensureConfigFile("{\"value\":1}");
            mod.ensureConfigFile("{\"value\":2,\"newKey\":true}");
            String config = Files.readString(root.resolve("luaconfig/example_mod-2.json"));
            assertTrue(config.contains("\"value\": 1"),
                    "the first written user value must survive the later default merge");
        }

        @Test
        void validIdWritesAndMergesOnlyItsConfigNewKeyTrue() throws IOException {
            Path mods = Files.createDirectories(root.resolve("luamods"));
            LuaMod mod = new LuaMod(manifest("example_mod-2", "main.lua"), mods.toFile(),
                    "universal", new HashMap<>(), mods.toFile());
            mod.ensureConfigFile("{\"value\":1}");
            mod.ensureConfigFile("{\"value\":2,\"newKey\":true}");
            String config = Files.readString(root.resolve("luaconfig/example_mod-2.json"));
            assertTrue(config.contains("\"newKey\": true"),
                    "a key added by the later defaults must be merged into the user config");
        }
    }

    @Nested
    class LoggerSafety {

        @Test
        void loggerRejectsTraversalEvenWithoutAManifestRejectsEscape() {
            assertThrows(IllegalArgumentException.class, () -> AsyncFileLogger.get().initModLog("../escape"),
                    "a traversal mod id must be rejected when opening a per-mod log file");
        }

        @Test
        void loggerRejectsTraversalEvenWithoutAManifestRejectsEscape2() {
            assertThrows(IllegalArgumentException.class,
                    () -> AsyncFileLogger.get().logMod("../escape", "INFO", "test"),
                    "a traversal mod id must be rejected when writing to a per-mod log file");
        }
    }

    @Nested
    class ManifestRejection {

        @Test
        void invalidManifestIsRejectedIdEscapeEnvironmentUniversal() {
            assertNull(LuaModManifest.parseJson("{\"id\":\"../escape\",\"environment\":\"universal\"}"),
                    "a manifest whose id escapes the sandbox must be rejected");
        }

        @Test
        void invalidManifestIsRejectedNotJson() {
            assertNull(LuaModManifest.parseJson("not json"),
                    "malformed JSON must not produce a manifest");
        }
    }
}

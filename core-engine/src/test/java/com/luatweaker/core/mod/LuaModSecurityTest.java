package com.luatweaker.core.mod;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LuaModSecurityTest {
    @TempDir Path root;

    @AfterAll
    static void stopLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private LuaModManifest manifest(String id, String main) {
        return new LuaModManifest(id, id, "test", "1", main, "universal", List.of(), List.of(), List.of(), null);
    }

    @Test
    void rejectsUnsafeIdsBeforeTheyCanWriteConfig() throws Exception {
        Path sentinel = root.resolve("external.json");
        Files.writeString(sentinel, "{\"keep\":true}");
        for (String id : List.of("../external", "..\\external", "/absolute", "C:\\absolute",
                "a/b", "a\\b", "a:stream", "a.", "CON", "nul", "COM1", "LPT9", "..", " ")) {
            assertThrows(IllegalArgumentException.class, () -> manifest(id, "main.lua"), id);
        }
        assertEquals("{\"keep\":true}", Files.readString(sentinel));
    }

    @Test
    void rejectsUnsafeEntrypoints() {
        for (String main : List.of("../main.lua", "src/../../main.lua", "C:/main.lua",
                "/main.lua", "src\\main.lua", "src//main.lua")) {
            assertThrows(IllegalArgumentException.class, () -> manifest("safe_mod", main), main);
        }
        assertEquals("src/main.lua", manifest("safe_mod", "src/main.lua").main());
    }

    @Test
    void rejectsSymlinkEscape() throws Exception {
        Path modRoot = Files.createDirectories(root.resolve("mod"));
        Path outside = Files.createDirectories(root.resolve("outside"));
        Files.writeString(outside.resolve("secret.lua"), "return true");
        Path link = modRoot.resolve("link.lua");
        try {
            Files.createSymbolicLink(link, outside.resolve("secret.lua"));
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable: " + e.getMessage());
        }
        assertThrows(IllegalArgumentException.class,
                () -> LuaModPathSecurity.resolveContained(modRoot, "link.lua"));
    }

    @Test
    void validIdWritesAndMergesOnlyItsConfig() throws Exception {
        Path mods = Files.createDirectories(root.resolve("luamods"));
        LuaMod mod = new LuaMod(manifest("example_mod-2", "main.lua"), mods.toFile(),
                "universal", new HashMap<>(), mods.toFile());
        mod.ensureConfigFile("{\"value\":1}");
        mod.ensureConfigFile("{\"value\":2,\"newKey\":true}");
        String config = Files.readString(root.resolve("luaconfig/example_mod-2.json"));
        assertTrue(config.contains("\"value\": 1"));
        assertTrue(config.contains("\"newKey\": true"));
    }

    @Test
    void loggerRejectsTraversalEvenWithoutAManifest() {
        assertThrows(IllegalArgumentException.class, () -> AsyncFileLogger.get().initModLog("../escape"));
        assertThrows(IllegalArgumentException.class,
                () -> AsyncFileLogger.get().logMod("../escape", "INFO", "test"));
    }

    @Test
    void invalidManifestIsRejected() {
        assertNull(LuaModManifest.parseJson("{\"id\":\"../escape\",\"environment\":\"universal\"}"));
        assertNull(LuaModManifest.parseJson("not json"));
    }
}

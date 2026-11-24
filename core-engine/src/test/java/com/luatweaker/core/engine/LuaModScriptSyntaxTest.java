package com.luatweaker.core.engine;

import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped Lua mod script must at least PARSE (AGENTS.md 0.3: no broken
 * code). The scripts are not executed here (they need the in-game engine
 * services), but a syntax error must fail the build, never reach players.
 */
class LuaModScriptSyntaxTest {

    private static Path findLuaModsDir() {
        // Test working dir is core-engine/ (or repo root in IDE); walk up to locate luamods/.
        Path dir = Path.of(".").toAbsolutePath().normalize();
        for (int i = 0; i < 6; i++) {
            Path candidate = dir.resolve("luamods");
            if (Files.isDirectory(candidate) && Files.isDirectory(candidate.resolve("luatweaker_test"))) {
                return candidate;
            }
            dir = dir.getParent();
            if (dir == null) { break; }
        }
        throw new IllegalStateException("Could not locate luamods/ from working dir");
    }

    private record LuaFileScan(List<Path> files, List<String> failures) {
    }

    private static LuaFileScan scanLuaModScripts() throws IOException {
        Path luaMods = findLuaModsDir();
        List<String> failures = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> stream = Files.walk(luaMods)) {
            files = stream.filter(p -> p.toString().endsWith(".lua")).toList();
            for (Path file : files) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                String error = LuaEngineFactory.checkSyntax(luaMods.relativize(file).toString(), source);
                if (error != null) {
                    failures.add(file.getFileName() + ": " + error);
                }
            }
        }
        return new LuaFileScan(files, failures);
    }

    @Test
    void shippedLuaModScriptsArePresent() throws IOException {
        assertTrue(scanLuaModScripts().files().size() >= 15,
                "shippedLuaModScriptsArePresent: expected the shipped Lua mods");
    }

    @Test
    void shippedLuaModScriptsParseWithoutSyntaxErrors() throws IOException {
        assertEquals(List.of(), scanLuaModScripts().failures(),
                "shippedLuaModScriptsParseWithoutSyntaxErrors: Lua syntax errors in shipped mod scripts");
    }
}

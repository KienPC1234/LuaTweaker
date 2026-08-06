package com.luatweaker.core.engine;

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
public class LuaModScriptSyntaxTest {

    private static Path findLuaModsDir() {
        // Test working dir is core-engine/ (IDE: modules/core-engine); walk up
        // to the repo root and down into neoforge-platform/luamods.
        Path dir = Path.of(".").toAbsolutePath().normalize();
        for (int i = 0; i < 6; i++) {
            Path candidate = dir.resolve("neoforge-platform/luamods");
            if (Files.isDirectory(candidate)) return candidate;
            Path modulesCandidate = dir.resolve("modules/core-engine").resolve("neoforge-platform/luamods");
            if (Files.isDirectory(modulesCandidate)) return modulesCandidate;
            dir = dir.getParent();
            if (dir == null) break;
        }
        throw new IllegalStateException("Could not locate neoforge-platform/luamods from working dir");
    }

    @Test
    public void allLuaModScriptsParseWithoutSyntaxErrors() throws IOException {
        Path luaMods = findLuaModsDir();
        List<String> failures = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(luaMods)) {
            List<Path> files = stream.filter(p -> p.toString().endsWith(".lua")).toList();
            assertTrue(files.size() >= 15, "expected the shipped Lua mods, found " + files.size());
            for (Path file : files) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                String error = LuaEngine.checkSyntax(luaMods.relativize(file).toString(), source);
                if (error != null) {
                    failures.add(file.getFileName() + ": " + error);
                }
            }
        }
        assertEquals(List.of(), failures, "Lua syntax errors in shipped mod scripts");
    }
}

package com.luatweaker.platform.client.luamods;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The log viewer must tail real log files and refuse path escapes. */
class LuamodClientLogTailTest {

    private static Path writeDemoLog(Path gameDir) throws IOException {
        Path logs = Files.createDirectories(gameDir.resolve("logs/luatweaker/mods"));
        List<String> body = new ArrayList<>();
        for (int line = 1; line <= 10; line++) {
            body.add("line " + line);
        }
        Files.writeString(logs.resolve("demo.log"), String.join("\n", body));
        return gameDir;
    }

    @Test
    void logTailReadsTheNewestLines(@TempDir Path gameDir) throws IOException {
        List<String> tail = LuamodClientActions.readLogTail(writeDemoLog(gameDir).toFile(), "demo", 3);

        assertEquals(List.of("line 8", "line 9", "line 10"), tail,
                "the tail must return exactly the newest requested lines");
    }

    @Test
    void logTailOfMissingModExplainsItself(@TempDir Path gameDir) throws IOException {
        assertEquals(1, LuamodClientActions.readLogTail(writeDemoLog(gameDir).toFile(), "missing", 50).size(),
                "a missing log must produce a single explanatory line instead of crashing");
    }

    @Test
    void logTailRejectsPathEscapes(@TempDir Path gameDir) throws IOException {
        assertEquals(1, LuamodClientActions.readLogTail(writeDemoLog(gameDir).toFile(), "../evil", 50).size(),
                "a traversal payload must produce a single explanatory line instead of escaping");
    }
}

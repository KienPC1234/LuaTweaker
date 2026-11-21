package com.luatweaker.recipes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.locks.LockSupport;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that Lua syntax errors produce the fancy traceback log output.
 * The async file logger is polled with parking (no raw threads) until the
 * expected marker lands or the deadline passes.
 */
class SyntaxErrorTracebackTest extends RecipeScriptFixture {

    private static final long LOG_FLUSH_TIMEOUT_MS = 10_000;
    private static final long LOG_FLUSH_POLL_INTERVAL_NANOS = 100_000_000L;
    private static final String LOG_MARKER = "LUA SYNTAX ERROR (COMPILE TIME)";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private String awaitLogFlush(File logFile) throws IOException {
        long deadline = System.currentTimeMillis() + LOG_FLUSH_TIMEOUT_MS;
        String content = "";
        while (System.currentTimeMillis() < deadline) {
            if (logFile.exists()) {
                content = Files.readString(logFile.toPath());
                if (content.contains(LOG_MARKER)) {
                    return content;
                }
            }
            LockSupport.parkNanos(LOG_FLUSH_POLL_INTERVAL_NANOS);
        }
        return content;
    }

    private String runErrorScript() throws IOException {
        ILuaEngine engine = LuaEngineFactory.create(false);

        File logFile = new File("logs/luatweaker/latest.log");
        if (logFile.exists()) {
            logFile.delete();
        }

        File script = findScriptFile("lua/test/syntax_error_test.lua");
        engine.executeScript(script, TEST_SERVER_CHUNK);
        return awaitLogFlush(logFile);
    }

    @Test
    void createsLogFile() throws IOException {
        runErrorScript();

        assertTrue(new File("logs/luatweaker/latest.log").exists(), "Log file should be created");
    }

    @Test
    void logsCompileTimeHeader() throws IOException {
        String logContent = runErrorScript();

        assertTrue(logContent.contains(LOG_MARKER),
                "the log must label the failure as a compile-time syntax error");
    }

    @Test
    void logsScriptName() throws IOException {
        String logContent = runErrorScript();

        assertTrue(logContent.contains("syntax_error_test.lua"),
                "the log must name the offending script");
    }

    @Test
    void logsLineNumber() throws IOException {
        String logContent = runErrorScript();

        assertTrue(logContent.contains("Line Number : 5"),
                "the log must report line 5 as the error site");
    }

    @Test
    void logsTestMessage() throws IOException {
        String logContent = runErrorScript();

        assertTrue(logContent.contains("Testing syntax error traceback representation"),
                "the log must include the script's own error text");
    }

    @Test
    void logsSeparator() throws IOException {
        String logContent = runErrorScript();

        assertTrue(logContent.contains("========"),
                "the log must include the traceback separator banner");
    }
}

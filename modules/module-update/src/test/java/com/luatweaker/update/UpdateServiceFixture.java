package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shared fixture for the {@link UpdateServiceImpl} tests: fresh status registry, a fake engine and
 * helpers to wait for a terminal status / snapshot the Lua status table.
 */
class UpdateServiceFixture {

    @TempDir
    protected Path tempDir;

    protected FakeEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FakeEngine();
        UpdateServiceImpl.clearStatuses();
    }

    @AfterEach
    void tearDown() {
        UpdateServiceImpl.clearStatuses();
    }

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    protected File luamods() throws IOException {
        return ModFixtures.createLuamodsDir(tempDir.toFile());
    }

    /** Polls the status registry until the check for {@code modId} is no longer in flight. */
    protected UpdateStatus waitForTerminal(String modId) throws InterruptedException {
        UpdateStatus status = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            status = UpdateServiceImpl.getUpdateStatuses().get(modId);
            if (status != null && !status.checking()) {
                return status;
            }
            TimeUnit.MILLISECONDS.sleep(50L);
        }
        return status;
    }

    /** Flattens a full status table into strings in a fixed order for one-shot assertions. */
    protected List<String> statusFields(String modId) {
        ILuaTable table = new UpdateServiceImpl(engine).GetStatus(modId).asTable();
        return List.of(
                table.rawget("HasUpdate").asString(),
                table.rawget("Checking").asString(),
                table.rawget("CurrentVersion").asString(),
                table.rawget("LatestVersion").asString(),
                table.rawget("Name").asString(),
                table.rawget("DownloadUrl").asString(),
                table.rawget("Changelog").asString(),
                table.rawget("UpdateUrl").asString());
    }
}

package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.mod.LuaModManifest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class UpdateServiceImplTest {

    @TempDir
    Path tempDir;

    private FakeEngine engine;

    @BeforeEach
    void setup() {
        engine = new FakeEngine();
        UpdateServiceImpl.clearStatuses();
    }

    @AfterEach
    void tearDown() {
        UpdateServiceImpl.clearStatuses();
    }

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private File luamods() throws IOException {
        return TestMods.createLuamodsDir(tempDir.toFile());
    }

    // ---- LuaModManifest.update_url parsing ----

    @Test
    void manifest_ParsesUpdateUrl() {
        LuaModManifest manifest = LuaModManifest.parseJson("""
                {
                  "id": "gt_addon",
                  "version": "1.0.2",
                  "environment": "universal",
                  "update_url": "https://raw.githubusercontent.com/example/gt_addon/main/update.json"
                }
                """);
        assertNotNull(manifest);
        assertEquals("https://raw.githubusercontent.com/example/gt_addon/main/update.json",
                manifest.updateUrl());
    }

    @Test
    void manifest_MissingUpdateUrl_IsNull() {
        LuaModManifest manifest = LuaModManifest.parseJson("""
                {
                  "id": "gt_addon",
                  "version": "1.0.2",
                  "environment": "universal"
                }
                """);
        assertNotNull(manifest);
        assertNull(manifest.updateUrl());
    }

    @Test
    void manifest_BlankUpdateUrl_IsNull() {
        LuaModManifest manifest = LuaModManifest.parseJson("""
                {
                  "id": "gt_addon",
                  "environment": "universal",
                  "update_url": "   "
                }
                """);
        assertNotNull(manifest);
        assertNull(manifest.updateUrl());
    }

    // ---- checkAll async pipeline ----

    @Test
    void checkAll_FetchesAsynchronously_AndStoresTerminalStatus() throws IOException, InterruptedException {
        TestMods.loadModWithUpdateUrl(luamods(), engine, "web_mod",
                "http://localhost:1/update.json", "net.http");

        UpdateServiceImpl.checkAll();

        UpdateStatus terminal = waitForTerminal("web_mod");
        assertNotNull(terminal, "checkAll must produce a terminal status");
        assertFalse(terminal.checking());
        assertNotNull(terminal.error(), "plain http feed must be rejected (https only)");
        assertTrue(terminal.error().contains("https"), "error must explain the https rule: " + terminal.error());
        assertNull(terminal.latestVersion());
    }

    @Test
    void checkAll_DoesNotReschedule_CompletedCheck() throws IOException, InterruptedException {
        TestMods.loadModWithUpdateUrl(luamods(), engine, "web_mod",
                "http://localhost:1/update.json");
        UpdateServiceImpl.checkAll();
        waitForTerminal("web_mod");
        UpdateStatus before = UpdateServiceImpl.getUpdateStatuses().get("web_mod");

        UpdateServiceImpl.checkAll();

        UpdateStatus after = UpdateServiceImpl.getUpdateStatuses().get("web_mod");
        assertSame(before, after, "a completed check must not be rescheduled on the same load cycle");
    }

    @Test
    void checkAll_SkipsMods_WithoutUpdateUrl() throws IOException {
        TestMods.loadMod(luamods(), engine, "plain_mod");
        UpdateServiceImpl.checkAll();
        assertNull(UpdateServiceImpl.getUpdateStatuses().get("plain_mod"),
                "mods without update_url must never be checked");
    }

    // ---- recordResult / GetStatus / GetUpdates ----

    @Test
    void recordResult_WithUpdate_ThenGetStatus_ExposesFields() {
        UpdateServiceImpl.recordResult(new UpdateStatus("gt_addon", "1.0.2",
                "https://example.com/update.json", "1.1.0", "New features", "https://example.com/dl",
                "Fixed everything", false, null));

        UpdateServiceImpl updateService = new UpdateServiceImpl(engine);
        ILuaValue statusVal = updateService.GetStatus("gt_addon");
        assertNotNull(statusVal);
        assertTrue(statusVal.isTable());
        ILuaTable table = statusVal.asTable();

        assertEquals(true, table.rawget("HasUpdate").asBoolean());
        assertEquals(false, table.rawget("Checking").asBoolean());
        assertEquals("1.0.2", table.rawget("CurrentVersion").asString());
        assertEquals("1.1.0", table.rawget("LatestVersion").asString());
        assertEquals("New features", table.rawget("Name").asString());
        assertEquals("https://example.com/dl", table.rawget("DownloadUrl").asString());
        assertEquals("Fixed everything", table.rawget("Changelog").asString());
        assertEquals("https://example.com/update.json", table.rawget("UpdateUrl").asString());
    }

    @Test
    void recordResult_UpToDate_HasUpdateFalse() {
        UpdateServiceImpl.recordResult(new UpdateStatus("gt_addon", "1.1.0",
                "https://example.com/update.json", "1.1.0", null, null, null, false, null));

        UpdateServiceImpl updateService = new UpdateServiceImpl(engine);
        ILuaValue statusVal = updateService.GetStatus("gt_addon");
        assertNotNull(statusVal);
        assertEquals(false, statusVal.asTable().rawget("HasUpdate").asBoolean());
        assertEquals("1.1.0", statusVal.asTable().rawget("LatestVersion").asString());
    }

    @Test
    void recordResult_Failure_ExposesError() {
        UpdateServiceImpl.recordResult(new UpdateStatus("gt_addon", "1.0.2",
                "https://example.com/update.json", null, null, null, null, false,
                "update_url returned HTTP 404"));

        UpdateServiceImpl updateService = new UpdateServiceImpl(engine);
        ILuaTable table = updateService.GetStatus("gt_addon").asTable();
        assertEquals(false, table.rawget("HasUpdate").asBoolean());
        assertEquals(false, table.rawget("Checking").asBoolean());
        assertEquals("update_url returned HTTP 404", table.rawget("Error").asString());
        assertTrue(table.rawget("LatestVersion") == null || table.rawget("LatestVersion").isNil());
    }

    @Test
    void recordResult_Checking_IsFlagged() {
        UpdateServiceImpl.recordResult(new UpdateStatus("gt_addon", "1.0.2",
                "https://example.com/update.json", null, null, null, null, true, null));

        UpdateServiceImpl updateService = new UpdateServiceImpl(engine);
        ILuaTable table = updateService.GetStatus("gt_addon").asTable();
        assertEquals(true, table.rawget("Checking").asBoolean());
        assertEquals(false, table.rawget("HasUpdate").asBoolean());
    }

    @Test
    void getStatus_UnknownMod_ReturnsNil() {
        UpdateServiceImpl updateService = new UpdateServiceImpl(engine);
        ILuaValue statusVal = updateService.GetStatus("does_not_exist");
        assertNull(statusVal, "unknown mod must resolve to nil so Lua sees 'no data'");
    }

    @Test
    void getUpdates_ReturnsOnlyAvailable_SortedByModId() {
        UpdateServiceImpl.recordResult(new UpdateStatus("z_mod", "1.0.0",
                "https://example.com/u", "1.1.0", null, null, null, false, null));
        UpdateServiceImpl.recordResult(new UpdateStatus("a_mod", "1.0.0",
                "https://example.com/u", "1.0.0", null, null, null, false, null));
        UpdateServiceImpl.recordResult(new UpdateStatus("b_mod", "1.0.0",
                "https://example.com/u", null, null, null, null, false, "boom"));

        UpdateServiceImpl updateService = new UpdateServiceImpl(engine);
        ILuaValue updatesVal = updateService.GetUpdates();
        assertTrue(updatesVal.isTable());
        ILuaTable updates = updatesVal.asTable();
        assertNotNull(updates.rawget("z_mod"), "available update must be listed");
        assertNull(updates.rawget("a_mod"), "up-to-date mod must not be listed");
        assertNull(updates.rawget("b_mod"), "failed check must not be listed");

        assertEquals("1.1.0", updates.rawget("z_mod").asTable().rawget("LatestVersion").asString());
    }

    // ---- UpdateChecker.parseFeed ----

    @Test
    void parseFeed_ValidFeed_WithUpdate() {
        UpdateStatus status = UpdateChecker.parseFeed("gt_addon", "1.0.2", "https://example.com/u",
                "{\"version\":\"1.1.0\",\"name\":\"1.1.0 release\",\"download_url\":\"https://example.com/d\",\"changelog\":\"fixes\"}");
        assertFalse(status.checking());
        assertNull(status.error());
        assertEquals("1.1.0", status.latestVersion());
        assertEquals("1.1.0 release", status.updateName());
        assertEquals("https://example.com/d", status.downloadUrl());
        assertEquals("fixes", status.changelog());
        assertTrue(status.hasUpdate(), "1.1.0 must be newer than 1.0.2");
    }

    @Test
    void parseFeed_ValidFeed_UpToDate() {
        UpdateStatus status = UpdateChecker.parseFeed("gt_addon", "1.0.2", "https://example.com/u",
                "{\"version\":\"1.0.2\"}");
        assertNull(status.error());
        assertFalse(status.hasUpdate());
        assertEquals("1.0.2", status.latestVersion());
    }

    @Test
    void parseFeed_MissingVersion_IsError() {
        UpdateStatus status = UpdateChecker.parseFeed("gt_addon", "1.0.2", "https://example.com/u",
                "{\"name\":\"no version here\"}");
        assertNotNull(status.error());
        assertTrue(status.error().contains("version"));
        assertNull(status.latestVersion());
    }

    @Test
    void parseFeed_InvalidJson_IsError() {
        UpdateStatus status = UpdateChecker.parseFeed("gt_addon", "1.0.2", "https://example.com/u",
                "this is not json");
        assertNotNull(status.error());
        assertNull(status.latestVersion());
    }

    @Test
    void parseFeed_NullVersion_IsError() {
        UpdateStatus status = UpdateChecker.parseFeed("gt_addon", "1.0.2", "https://example.com/u",
                "{\"version\": null}");
        assertNotNull(status.error());
    }

    // ---- UpdateChecker.check transport rules ----

    @Test
    void check_PlainHttp_IsRejected() {
        UpdateStatus status = UpdateChecker.check("gt_addon", "1.0.2", "http://example.com/u");
        assertFalse(status.checking());
        assertNotNull(status.error());
        assertTrue(status.error().contains("https"), "error must name the https rule");
    }

    @Test
    void check_BlankUrl_IsError() {
        UpdateStatus status = UpdateChecker.check("gt_addon", "1.0.2", "  ");
        assertNotNull(status.error());
        assertTrue(status.error().contains("update_url"));
    }

    @Test
    void check_UnreachableUrl_IsError() {
        UpdateStatus status = UpdateChecker.check("gt_addon", "1.0.2",
                "https://127.0.0.1:1/update.json");
        assertNotNull(status.error(), "unreachable https feed must fail loudly, not hang forever");
        assertNull(status.latestVersion());
    }

    // ---- readBounded ----

    @Test
    void readBounded_CapsOversizedBody() {
        byte[] big = new byte[UpdateChecker.MAX_BODY_BYTES + 1];
        assertThrows(IOException.class, () ->
                UpdateChecker.readBounded(new ByteArrayInputStream(big), UpdateChecker.MAX_BODY_BYTES));
    }

    @Test
    void readBounded_ReadsSmallBody() throws IOException {
        byte[] small = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] read = UpdateChecker.readBounded(new ByteArrayInputStream(small), UpdateChecker.MAX_BODY_BYTES);
        assertEquals("hello", new String(read, StandardCharsets.UTF_8));
    }

    // ---- helpers ----

    private UpdateStatus waitForTerminal(String modId) throws InterruptedException {
        UpdateStatus status = null;
        for (int i = 0; i < 100; i++) {
            status = UpdateServiceImpl.getUpdateStatuses().get(modId);
            if (status != null && !status.checking()) return status;
            Thread.sleep(50);
        }
        return status;
    }
}

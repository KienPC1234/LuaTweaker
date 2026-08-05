package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class WebServiceImplTest {

    @TempDir
    Path tempDir;

    private FakeEngine engine;
    private WebServiceImpl webService;
    private HttpServer server;

    @BeforeEach
    void setup() throws IOException {
        engine = new FakeEngine();
        webService = new WebServiceImpl(engine);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private void startServer(String path, int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void loadMod(String modId, String... permissions) throws IOException {
        TestMods.loadMod(TestMods.createLuamodsDir(tempDir.toFile()), engine, modId, permissions);
    }

    // ---- permission gate ----

    @Test
    void httpGet_NoModContext_IsDenied() {
        ILuaValue result = webService.HttpGet("http://127.0.0.1:1/x", 1);
        assertTrue(result.isTable());
        ILuaTable table = result.asTable();
        assertEquals(false, table.rawget("Success").asBoolean());
        assertTrue(table.rawget("Error").asString().contains("owning mod context"),
                "denial must name the missing mod context: " + table.rawget("Error").asString());
    }

    @Test
    void httpGet_WithoutPermission_IsDenied() throws IOException {
        loadMod("safe_mod");
        ILuaValue result = webService.HttpGet("http://127.0.0.1:1/x", 1);
        ILuaTable table = result.asTable();
        assertEquals(false, table.rawget("Success").asBoolean());
        String error = table.rawget("Error").asString();
        assertTrue(error.contains("net.http"), "denial must name the missing permission: " + error);
    }

    @Test
    void httpGet_WithPermission_IsAllowed() throws IOException {
        startServer("/feed", 200, "hello world");
        loadMod("web_mod", "net.http");

        ILuaValue result = webService.HttpGet(baseUrl() + "/feed", 5);
        ILuaTable table = result.asTable();
        assertEquals(true, table.rawget("Success").asBoolean());
        assertEquals(200.0, table.rawget("StatusCode").asDouble());
        assertEquals("hello world", table.rawget("Body").asString());
    }

    @Test
    void httpGet_GrantIsPerInstallation_AnyLoadedModUnlocksIt() throws IOException {
        // Granting mod loads FIRST, a non-granting mod loads LAST, so the
        // global 'mod' table points at the non-granting mod at runtime.
        // The grant must still be active: the decision is registry-based,
        // never based on the spoofable global.
        startServer("/feed", 200, "ok");
        loadMod("safe_mod");
        loadMod("web_mod", "net.http");

        ILuaTable table = webService.HttpGet(baseUrl() + "/feed", 5).asTable();
        assertEquals(true, table.rawget("Success").asBoolean(),
                "grant must be per installation, not per runtime mod global");
    }

    @Test
    void httpGet_SpoofedModGlobal_CannotGrantPermission() throws IOException {
        // The script writes a fake id into the mod table - the registry-backed
        // grant check must ignore it (no loaded mod actually has net.http).
        loadMod("safe_mod");
        engine.getGlobalEnvironment().rawget("mod").asTable().rawset("ID", "web_mod");

        ILuaTable table = webService.HttpGet("http://127.0.0.1:1/x", 1).asTable();
        assertEquals(false, table.rawget("Success").asBoolean());
        assertTrue(table.rawget("Error").asString().contains("net.http"),
                "spoofed mod.ID must not unlock HTTP: " + table.rawget("Error").asString());
    }

    @Test
    void httpGet_WithPermission_ParsesJsonBody() throws IOException {
        startServer("/feed", 200, "{\"version\":\"1.2.0\",\"tags\":[\"a\",\"b\"]}");
        loadMod("web_mod", "net.http");

        ILuaTable table = webService.HttpGet(baseUrl() + "/feed", 5).asTable();
        ILuaValue jsonVal = table.rawget("Json");
        assertNotNull(jsonVal, "valid JSON body must be exposed parsed as 'Json'");
        assertTrue(jsonVal.isTable());
        ILuaTable json = jsonVal.asTable();
        assertEquals("1.2.0", json.rawget("version").asString());
        ILuaTable tags = json.rawget("tags").asTable();
        assertEquals("a", tags.rawget(1).asString());
        assertEquals("b", tags.rawget(2).asString());
    }

    @Test
    void httpGet_NonJsonBody_HasNoJsonField() throws IOException {
        startServer("/feed", 200, "<html>not json</html>");
        loadMod("web_mod", "net.http");

        ILuaTable table = webService.HttpGet(baseUrl() + "/feed", 5).asTable();
        assertNull(table.rawget("Json"), "non-JSON body must not expose a Json field");
        assertEquals("<html>not json</html>", table.rawget("Body").asString());
    }

    @Test
    void httpGet_ServerError_ReturnsFailureTable() throws IOException {
        startServer("/missing", 404, "not found");
        loadMod("web_mod", "net.http");

        ILuaTable table = webService.HttpGet(baseUrl() + "/missing", 5).asTable();
        assertEquals(false, table.rawget("Success").asBoolean());
        assertEquals(404.0, table.rawget("StatusCode").asDouble());
        assertEquals("not found", table.rawget("Body").asString());
    }

    @Test
    void httpGet_RejectsNonHttpScheme() throws IOException {
        loadMod("web_mod", "net.http");
        ILuaTable table = webService.HttpGet("file:///C:/secret.txt", 5).asTable();
        assertEquals(false, table.rawget("Success").asBoolean());
        assertTrue(table.rawget("Error").asString().contains("http/https"),
                "scheme rejection must be explicit: " + table.rawget("Error").asString());
    }

    @Test
    void httpGet_RedirectToNonHttpUrl_FailsSafely() throws IOException {
        // A redirect target outside http/https must never be followed.
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hop", exchange -> {
            exchange.getResponseHeaders().add("Location", "file:///C:/secret.txt");
            exchange.sendResponseHeaders(302, -1);
        });
        server.start();
        loadMod("web_mod", "net.http");

        ILuaTable table = webService.HttpGet(baseUrl() + "/hop", 5).asTable();
        assertEquals(false, table.rawget("Success").asBoolean(),
                "a redirect to a non-http target must fail, not follow the location");
    }

    @Test
    void httpGet_RejectsBlankUrl() throws IOException {
        loadMod("web_mod", "net.http");
        ILuaTable table = webService.HttpGet("   ", 5).asTable();
        assertEquals(false, table.rawget("Success").asBoolean());
        assertTrue(table.rawget("Error").asString().contains("url"));
    }

    @Test
    void httpGet_SubSecondTimeout_IsClampedToMinimum() throws IOException {
        startServer("/slow", 200, "ok");
        loadMod("web_mod", "net.http");

        ILuaTable table = webService.HttpGet(baseUrl() + "/slow", 0.0001).asTable();
        assertEquals(true, table.rawget("Success").asBoolean(),
                "timeout below the 1s floor must be clamped up, not reject the call");
    }

    @Test
    void httpGet_UnreachableUrl_FailsLoudly() throws IOException {
        loadMod("web_mod", "net.http");
        ILuaTable table = webService.HttpGet("http://127.0.0.1:1/nope", 1).asTable();
        assertEquals(false, table.rawget("Success").asBoolean());
        assertTrue(table.rawget("Error").asString().length() > 0);
    }

    @Test
    void httpGet_ServerCanCountCalls_ToProveRealRequest() throws IOException {
        AtomicInteger hits = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/count", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(200, 2);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("ok".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        loadMod("web_mod", "net.http");

        webService.HttpGet(baseUrl() + "/count", 5);
        assertEquals(1, hits.get(), "the request must really hit the server");
    }
}

package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared fixture for {@link WebServiceImpl} tests: a fake engine, a local HTTP server on an
 * ephemeral port (SSRF filtering is switched off for it) and helpers for loading mods.
 */
class WebServiceFixture {

    protected static final String WEB_MOD = "web_mod";
    protected static final String SAFE_MOD = "safe_mod";
    protected static final String NET_HTTP = "net.http";
    protected static final String FEED_PATH = "/feed";

    @TempDir
    protected Path tempDir;

    protected FakeEngine engine;
    protected WebServiceImpl webService;
    protected HttpServer server;

    @BeforeEach
    void setUp() {
        // These tests exercise a real local HTTP server; SSRF filtering would reject it.
        System.setProperty(UrlSafety.ALLOW_INTERNAL_PROPERTY, "true");
        engine = new FakeEngine();
        webService = new WebServiceImpl(engine);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(UrlSafety.ALLOW_INTERNAL_PROPERTY);
        if (server != null) {
            server.stop(0);
        }
    }

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    protected void startServer(String path, int status, String body) throws IOException {
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

    protected String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    protected void loadMod(String modId, String... permissions) throws IOException {
        File luamods = ModFixtures.createLuamodsDir(tempDir.toFile());
        ModFixtures.loadMod(luamods, engine, modId, permissions);
    }

    /** Flattens a successful response table into (Success, StatusCode, Body). */
    protected List<String> responseFields(ILuaTable table) {
        return List.of(
                table.rawget("Success").asString(),
                table.rawget("StatusCode").asString(),
                table.rawget("Body").asString());
    }
}

package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaTable;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link WebServiceImpl#HttpGet} response handling: status/body round-trip, JSON parsing,
 * non-JSON bodies, server errors, scheme rejection, redirects and blank URLs.
 */
class WebHttpGetTest extends WebServiceFixture {

    private static final String MISSING_PATH = "/missing";
    private static final String JSON_FEED = "{\"version\":\"1.2.0\",\"tags\":[\"a\",\"b\"]}";

    @Test
    void successResponseCarriesStatusAndBody() throws IOException {
        startServer(FEED_PATH, 200, "hello world");
        loadMod(WEB_MOD, NET_HTTP);
        ILuaTable table = webService.HttpGet(baseUrl() + FEED_PATH, 5).asTable();
        assertEquals(List.of("true", "200.0", "hello world"), responseFields(table),
                "a 200 response must expose Success=true, the status code and the raw body");
    }

    @Test
    void jsonBodyExposesTheVersion() throws IOException {
        startServer(FEED_PATH, 200, JSON_FEED);
        loadMod(WEB_MOD, NET_HTTP);
        ILuaTable json = webService.HttpGet(baseUrl() + FEED_PATH, 5).asTable()
                .rawget("Json").asTable();
        assertEquals("1.2.0", json.rawget("version").asString(),
                "a JSON body must be exposed parsed, keeping string fields intact");
    }

    @Test
    void jsonBodyExposesArrayEntries() throws IOException {
        startServer(FEED_PATH, 200, JSON_FEED);
        loadMod(WEB_MOD, NET_HTTP);
        ILuaTable tags = webService.HttpGet(baseUrl() + FEED_PATH, 5).asTable()
                .rawget("Json").asTable().rawget("tags").asTable();
        assertEquals(List.of("a", "b"),
                List.of(tags.rawget(1).asString(), tags.rawget(2).asString()),
                "a JSON array must become a 1-based Lua table so #tags works");
    }

    @Test
    void nonJsonBodyHasNoJsonField() throws IOException {
        startServer(FEED_PATH, 200, "<html>not json</html>");
        loadMod(WEB_MOD, NET_HTTP);
        ILuaTable table = webService.HttpGet(baseUrl() + FEED_PATH, 5).asTable();
        assertTrue(table.rawget("Json") == null
                        && "<html>not json</html>".equals(table.rawget("Body").asString()),
                "a non-JSON body must stay in Body without a Json field");
    }

    @Test
    void serverErrorReturnsFailureTable() throws IOException {
        startServer(MISSING_PATH, 404, "not found");
        loadMod(WEB_MOD, NET_HTTP);
        ILuaTable table = webService.HttpGet(baseUrl() + MISSING_PATH, 5).asTable();
        assertEquals(List.of("false", "404.0", "not found"), responseFields(table),
                "an HTTP error must still expose the status code and body with Success=false");
    }

    @Test
    void nonHttpSchemeIsRejected() throws IOException {
        loadMod(WEB_MOD, NET_HTTP);
        ILuaTable table = webService.HttpGet("file:///C:/secret.txt", 5).asTable();
        assertTrue(!table.rawget("Success").asBoolean()
                        && table.rawget("Error").asString().contains("http/https"),
                "a non-http(s) scheme must be rejected and named in the error");
    }

    @Test
    void redirectToNonHttpTargetFailsSafely() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hop", exchange -> {
            exchange.getResponseHeaders().add("Location", "file:///C:/secret.txt");
            exchange.sendResponseHeaders(302, -1);
        });
        server.start();
        loadMod(WEB_MOD, NET_HTTP);

        ILuaTable table = webService.HttpGet(baseUrl() + "/hop", 5).asTable();
        assertTrue(!table.rawget("Success").asBoolean(),
                "a redirect to a non-http target must fail, not follow the location");
    }

    @Test
    void blankUrlIsRejected() throws IOException {
        loadMod(WEB_MOD, NET_HTTP);
        ILuaTable table = webService.HttpGet("   ", 5).asTable();
        assertTrue(!table.rawget("Success").asBoolean()
                        && table.rawget("Error").asString().contains("url"),
                "a blank URL must be rejected before any connection is attempted");
    }
}

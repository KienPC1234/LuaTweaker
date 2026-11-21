package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaTable;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link WebServiceImpl} transport behaviour: the timeout floor, loud failure for unreachable
 * hosts, and that the request really hits the server.
 */
class WebTransportTest extends WebServiceFixture {

    @Test
    void subSecondTimeoutIsClampedToMinimum() throws IOException {
        startServer("/slow", 200, "ok");
        loadMod(WEB_MOD, NET_HTTP);
        ILuaTable table = webService.HttpGet(baseUrl() + "/slow", 0.0001).asTable();
        assertEquals(true, table.rawget("Success").asBoolean(),
                "a timeout below the 1s floor must be clamped up, not reject the call");
    }

    @Test
    void unreachableUrlFailsLoudly() throws IOException {
        loadMod(WEB_MOD, NET_HTTP);
        ILuaTable table = webService.HttpGet("http://127.0.0.1:1/nope", 1).asTable();
        assertTrue(!table.rawget("Success").asBoolean()
                        && !table.rawget("Error").asString().isEmpty(),
                "an unreachable host must return a failure table with a non-empty reason");
    }

    @Test
    void serverReceivesTheRequest() throws IOException {
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
        loadMod(WEB_MOD, NET_HTTP);

        webService.HttpGet(baseUrl() + "/count", 5);
        assertEquals(1, hits.get(), "the request must really hit the server");
    }
}

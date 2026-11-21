package com.luatweaker.update;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins bundle transport: strict policy up front, bounded no-redirect moves. */
class ServerPackFetcherTest {

    private static final String PAYLOAD_TEXT = "fake-zip-bytes";
    private static final String PACK_PATH = "/pack.zip";
    private static final byte[] PAYLOAD = PAYLOAD_TEXT.getBytes(StandardCharsets.UTF_8);

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void httpUrlIsRejectedWithoutNetwork() {
        ServerPackFetcher fetcher = new ServerPackFetcher();

        assertThrows(IOException.class, () -> fetcher.fetch("http://127.0.0.1:9/pack.zip"),
                "plain http must be rejected before any connection is opened");
    }

    @Test
    void privateHostIsRejectedWithoutDns() {
        ServerPackFetcher fetcher = new ServerPackFetcher();

        assertThrows(IOException.class, () -> fetcher.fetch("https://10.0.0.5/pack.zip"),
                "a site-local host must be rejected without fetching");
    }

    @Test
    void malformedUrlIsRejected() {
        ServerPackFetcher fetcher = new ServerPackFetcher();

        assertThrows(IOException.class, () -> fetcher.fetch("not a url"),
                "a malformed URL must fail loudly");
    }

    @Test
    void transportDeliversBody() throws IOException {
        startServer(PACK_PATH, 200, PAYLOAD);
        URL target = new URL(baseUrl() + PACK_PATH);

        assertArrayEquals(PAYLOAD, ServerPackFetcher.fetchTransport(target, 1024),
                "a 200 response must come back byte-identical");
    }

    @Test
    void transportCapsBodies() throws IOException {
        startServer("/big.zip", 200, PAYLOAD);
        URL target = new URL(baseUrl() + "/big.zip");

        assertThrows(IOException.class, () -> ServerPackFetcher.fetchTransport(target, 2),
                "a body above the cap must abort instead of buffering it all");
    }

    @Test
    void transportRejectsRedirect() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/old.zip", exchange -> {
            exchange.getResponseHeaders().add("Location", "/pack.zip");
            exchange.sendResponseHeaders(302, 0);
            exchange.close();
        });
        server.start();
        URL target = new URL(baseUrl() + "/old.zip");

        assertThrows(IOException.class, () -> ServerPackFetcher.fetchTransport(target, 1024),
                "redirects must fail: a public URL must not bounce to an internal one");
    }

    @Test
    void transportRejectsMissingFile() throws IOException {
        startServer("/pack.zip", 200, PAYLOAD);
        URL target = new URL(baseUrl() + "/absent.zip");
        IOException failure;
        try {
            ServerPackFetcher.fetchTransport(target, 1024);

            throw new AssertionError("expected an IOException for a 404 but nothing was thrown");
        } catch (IOException expectedFailure) {
            failure = expectedFailure;
        }

        assertTrue(failure.getMessage().contains("404"),
                "a 404 must fail loudly with the status code, got: " + failure.getMessage());
    }

    private void startServer(String path, int code, byte[] body) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            exchange.sendResponseHeaders(code, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }}

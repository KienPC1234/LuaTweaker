package com.luatweaker.update;

import com.luatweaker.core.serverpacks.BundleDownloader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Strict transport for server mod bundles: HTTPS only, public hosts only, no
 * redirects, bounded bodies. Shape and digest validation lives in
 * {@link com.luatweaker.core.serverpacks.ServerBundleInfo}; this class only
 * moves bytes.
 */
public final class ServerPackFetcher implements BundleDownloader.Fetcher {

    /** Time to establish the bundle connection. */
    public static final int CONNECT_TIMEOUT_MS = 10_000;
    /** Time to receive the bundle body. */
    public static final int READ_TIMEOUT_MS = 30_000;
    /** User-Agent identifying engine-side bundle fetches. */
    public static final String USER_AGENT = "LuaTweaker-ServerPack/1.0";

    @Override
    public byte[] fetch(@NotNull String url) throws IOException {
        URL parsed = toUrl(url);
        requireHttps(parsed);
        try {
            UrlSafety.validatePublicHost(parsed);
        } catch (IllegalArgumentException e) {
            throw new IOException("refusing non-public bundle host: " + e.getMessage(), e);
        }
        return fetchTransport(parsed, (int) BundleDownloader.BUNDLE_MAX_BYTES);
    }

    /**
     * Moves bytes for an already-validated URL: no redirects, bounded body,
     * 2xx only. Split out so tests can exercise transport against a local
     * server without touching the scheme/host policy.
     */
    static byte[] fetchTransport(@NotNull URL parsed, int maxBytes) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) parsed.openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setInstanceFollowRedirects(false);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("bundle request failed: HTTP " + code);
            }
            try (InputStream body = conn.getInputStream()) {
                if (body == null) {
                    throw new IOException("bundle request returned no body");
                }
                return UpdateChecker.readBounded(body, maxBytes);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static @NotNull URL toUrl(@NotNull String url) throws IOException {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IOException("malformed bundle URL: " + url, e);
        }
    }

    private static void requireHttps(@NotNull URL parsed) throws IOException {
        if (!"https".equalsIgnoreCase(parsed.getProtocol())) {
            throw new IOException("bundle URL must be https, got: " + parsed.getProtocol());
        }
    }
}

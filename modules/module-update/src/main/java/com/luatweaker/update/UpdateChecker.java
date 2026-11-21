package com.luatweaker.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Fetches and parses the update feed of a LuaMod. Runs entirely on the Java
 * side, outside the Lua sandbox: a script can never trigger or influence an
 * update download itself.
 *
 * <p>Security posture: only {@code https://} feeds are accepted - plain HTTP
 * is rejected because the feed data would be readable and modifiable on the
 * wire. Response bodies are capped to prevent memory abuse.</p>
 */
public final class UpdateChecker {
    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 10_000;
    public static final int MAX_BODY_BYTES = 256 * 1024;
    public static final String USER_AGENT = "LuaTweaker-UpdateChecker/1.0";
    private static final String FIELD_VERSION = "version";

    private UpdateChecker() {}

    /**
     * Performs one synchronous check against {@code updateUrl}.
     *
     * @param modId          mod id (for logging and status identity)
     * @param currentVersion installed version from the manifest
     * @param updateUrl      the https feed URL from the manifest
     * @return a terminal status; never throws (failures are recorded in the status).
     */
    public static @NotNull UpdateStatus check(@NotNull String modId,
                                              @NotNull String currentVersion,
                                              @NotNull String updateUrl) {
        if (updateUrl == null || updateUrl.isBlank()) {
            return failure(modId, currentVersion, null, "No update_url declared in manifest.json");
        }
        try {
            URL url = new URL(updateUrl);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                String msg = "update_url must use https://, found " + url.getProtocol()
                        + ":// (plain http is blocked to protect player traffic)";
                return failure(modId, currentVersion, updateUrl, msg);
            }
            // A manifest is untrusted input: never let update_url reach loopback/private hosts.
            UrlSafety.validatePublicHost(url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setInstanceFollowRedirects(true);
                int code = conn.getResponseCode();
                // Re-validate AFTER redirects: an https feed must not be allowed
                // to downgrade to plain http on the wire (MITM risk).
                String finalProtocol = conn.getURL().getProtocol();
                if (!"https".equalsIgnoreCase(finalProtocol)) {
                    return failure(modId, currentVersion, updateUrl,
                            "update_url redirected to non-https protocol: " + finalProtocol);
                }
                // Re-check the host too: a public feed must not redirect to an internal address.
                UrlSafety.validatePublicHost(conn.getURL());
                if (code != HttpURLConnection.HTTP_OK) {
                    return failure(modId, currentVersion, updateUrl, "update_url returned HTTP " + code);
                }
                String body;
                try (InputStream is = conn.getInputStream()) {
                    body = new String(readBounded(is, MAX_BODY_BYTES), StandardCharsets.UTF_8);
                }
                return parseFeed(modId, currentVersion, updateUrl, body);
            } finally {
                conn.disconnect();
            }
        } catch (IOException | IllegalArgumentException e) {
            return failure(modId, currentVersion, updateUrl,
                    "update_url fetch failed: " + e.getMessage());
        }
    }

    static @NotNull UpdateStatus parseFeed(@NotNull String modId,
                                           @NotNull String currentVersion,
                                           @NotNull String updateUrl,
                                           @NotNull String body) {
        if (body == null) {
            return failure(modId, currentVersion, updateUrl,
                    "update feed is not valid JSON with a 'version' field: null body");
        }
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json == null || !json.has(FIELD_VERSION) || json.get(FIELD_VERSION).isJsonNull()
                    || !json.get(FIELD_VERSION).isJsonPrimitive()) {
                return failure(modId, currentVersion, updateUrl,
                        "update feed is missing the required 'version' field");
            }
            String latest = json.get(FIELD_VERSION).getAsString();
            String name = optionalFeedField(json, "name");
            String downloadUrl = optionalFeedField(json, "download_url");
            String changelog = optionalFeedField(json, "changelog");
            return new UpdateStatus(modId, currentVersion, updateUrl, latest, name, downloadUrl,
                    changelog, false, null);
        } catch (JsonSyntaxException | IllegalStateException e) {
            return failure(modId, currentVersion, updateUrl,
                    "update feed is not valid JSON with a 'version' field: " + e.getMessage());
        }
    }

    @Nullable
    private static String optionalFeedField(@NotNull JsonObject json, @NotNull String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
    }

    private static @NotNull UpdateStatus failure(@NotNull String modId,
                                                 @NotNull String currentVersion,
                                                 String updateUrl,
                                                 @NotNull String message) {
        LuaTweakerLog.get().error(LogStage.SYSTEM, "[Update][" + modId + "] " + message);
        return new UpdateStatus(modId, currentVersion, updateUrl, null, null, null, null,
                false, message);
    }

    /** Reads up to {@code maxBytes} from the stream, aborting when the cap is exceeded. */
    public static byte[] readBounded(@NotNull InputStream is, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int total = 0;
        while (true) {
            int n = is.read(buf);
            if (n == -1) {
                break;
            }
            total += n;
            if (total > maxBytes) {
                throw new IOException("response body exceeds " + maxBytes + " bytes limit");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}

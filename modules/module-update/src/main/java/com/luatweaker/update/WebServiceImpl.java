package com.luatweaker.update;

import com.google.gson.JsonSyntaxException;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.api.web.IWebService;
import com.luatweaker.core.mod.LuaMod;
import com.luatweaker.core.mod.LuaModContext;
import com.luatweaker.core.mod.LuaModManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Permission-gated HTTP GET service.
 *
 * <p>Free HTTP access is locked behind the {@code net.http} manifest
 * permission (Section 3 of the network security architecture):</p>
 * <ul>
 *   <li>Default: no Lua mod can reach the internet. Update checks are the only
 *       engine-managed network path and they run on the Java side (see
 *       {@link UpdateChecker}).</li>
 *   <li>Grant: the permission is <b>per installation</b>. The Lua VM cannot
 *       attribute a runtime call to the mod that made it (the global
 *       {@code mod} table only identifies the mod while its entrypoint runs),
 *       so the decision is made deterministically on the Java side: HTTP is
 *       unlocked when ANY loaded mod declares {@code "net.http"}. The
 *       modpack admin is warned in chat and logs, and every granting mod is
 *       listed on first use. Anonymous calls (no mod context) are still
 *       rejected.</li>
 *   <li>Deny-by-default: with no granting mod loaded, every call is rejected
 *       loudly, regardless of what the script writes into the {@code mod}
 *       global (which is spoofable from Lua and is NOT trusted).</li>
 * </ul>
 *
 * <p>The call is synchronous on the calling thread: scripts must keep
 * {@code timeoutSeconds} low and avoid calling it from hot paths.</p>
 */
public class WebServiceImpl implements IWebService {

    public static final String PERMISSION = "net.http";
    public static final String USER_AGENT = "LuaTweaker-WebService/1.0";
    public static final int MAX_BODY_BYTES = 1024 * 1024;
    public static final double MIN_TIMEOUT_SECONDS = 1.0;
    /** Synchronous on the calling thread, so the cap is deliberately short. */
    public static final double MAX_TIMEOUT_SECONDS = 10.0;

    private final ILuaEngine engine;

    public WebServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public ILuaValue HttpGet(String url, double timeoutSeconds) {
        String modId = LuaModContext.resolveCurrentModId(engine);
        if (modId == null) {
            String msg = "Net:HttpGet() rejected: no owning mod context (call from a mod script).";
            LuaTweakerLog.get().error(LogStage.SYSTEM, "[Net] " + msg);
            return errorTable(msg);
        }
        if (!isHttpPermissionGranted()) {
            String msg = "Net:HttpGet() rejected: no loaded mod declares the 'net.http' permission in its manifest.json. Free HTTP is denied by default.";
            LuaTweakerLog.get().error(LogStage.SYSTEM, "[Net][" + modId + "] " + msg);
            return errorTable(msg);
        }
        logGrantIfFirstUse();
        if (url == null || url.isBlank()) {
            return errorTable("Net:HttpGet() rejected: url must not be empty.");
        }

        int timeoutMs = (int) (Math.max(MIN_TIMEOUT_SECONDS,
                Math.min(MAX_TIMEOUT_SECONDS, timeoutSeconds)) * 1000.0);
        return fetchUrl(modId, url, timeoutMs);
    }

    private ILuaValue fetchUrl(String modId, String url, int timeoutMs) {
        try {
            URL parsed = new URL(url);
            String scheme = parsed.getProtocol().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return errorTable("Net:HttpGet() rejected: only http/https URLs are allowed, found: " + scheme);
            }
            // Reject SSRF targets (loopback, link-local, private, unique-local) after DNS.
            UrlSafety.validatePublicHost(parsed);
            HttpURLConnection conn = (HttpURLConnection) parsed.openConnection();
            try {
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", USER_AGENT);
                // Redirects are not followed: a public URL must not bounce to an internal one.
                conn.setInstanceFollowRedirects(false);

                int code = conn.getResponseCode();
                String body = readBody(conn, code);
                ILuaValue result = buildResultTable(code, body);
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                        "[Net][" + modId + "] HttpGet '" + url + "' -> HTTP " + code
                                + " (" + body.length() + " bytes)");
                return result;
            } finally {
                conn.disconnect();
            }
        } catch (IOException | IllegalArgumentException e) {
            String msg = "Net:HttpGet() failed for '" + url + "': " + e.getMessage();
            LuaTweakerLog.get().error(LogStage.SYSTEM, "[Net][" + modId + "] " + msg);
            return errorTable(msg);
        }
    }

    private ILuaValue buildResultTable(int code, String body) {
        ILuaTable result = engine.createTable();
        result.rawset("Success", code >= 200 && code < 300);
        result.rawset("StatusCode", (double) code);
        result.rawset("Body", body);
        if (code >= 200 && code < 300) {
            Object parsedJson = tryParseJson(body);
            if (parsedJson != null) {
                result.rawset("Json", engine.toLuaValue(parsedJson));
            }
        }
        return result;
    }

    private ILuaValue errorTable(String message) {
        ILuaTable table = engine.createTable();
        table.rawset("Success", false);
        table.rawset("Error", message);
        return table;
    }

    private static String readBody(HttpURLConnection conn, int code) throws IOException {
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) {
            return "";
        }
        try (is) {
            return new String(UpdateChecker.readBounded(is, MAX_BODY_BYTES), StandardCharsets.UTF_8);
        }
    }

    /** @return the parsed JSON value graph (Map/List/String/Double/Boolean), or null. */
    private static Object tryParseJson(String body) {
        try {
            return new com.google.gson.Gson().fromJson(body, Object.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    /**
     * The permission is per installation: HTTP unlocks when ANY loaded mod
     * declares {@code net.http}. The VM cannot attribute a runtime call to the
     * mod that made it (the {@code mod} global is only trustworthy during a
     * mod's entrypoint execution and is spoofable from Lua), so the decision
     * is made deterministically from the Java-side mod registry instead.
     */
    private static boolean isHttpPermissionGranted() {
        for (LuaMod mod : LuaModManager.getLoadedMods().values()) {
            if (mod.getManifest().permissions().contains(PERMISSION)) {
                return true;
            }
        }
        return false;
    }

    private static final AtomicBoolean GRANT_LOGGED = new AtomicBoolean();

    /** Loudly lists every granting mod once per session when HTTP is first used. */
    private static void logGrantIfFirstUse() {
        if (!GRANT_LOGGED.compareAndSet(false, true)) {
            return;
        }
        List<String> holders = new ArrayList<>();
        for (LuaMod mod : LuaModManager.getLoadedMods().values()) {
            if (mod.getManifest().permissions().contains(PERMISSION)) {
                holders.add(mod.getManifest().id());
            }
        }
        LuaTweakerLog.get().warn(LogStage.SYSTEM,
                "[Net] 'net.http' is ACTIVE for this session. Granting mod(s): " + holders
                        + " - review these mods before enabling them on player-facing servers.");
    }
}

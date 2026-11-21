package com.luatweaker.core.serverpacks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Per-bundle download consent: {@code <gameDir>/luatweaker/servers/<bundleId>/consent.json}.
 *
 * <p>Three states, defaulting to prompt: {@code ASK} shows the download screen on every
 * mismatch, {@code ALLOW} downloads without asking, {@code DENY} never offers again.
 * A missing, oversized or corrupt file degrades to {@code ASK} (fail-safe: prompt the
 * human instead of guessing).</p>
 */
public final class ServerConsentStore {

    /** Stored consent decision. */
    public enum Decision {
        ASK,
        ALLOW,
        DENY
    }

    private static final String LOG_PREFIX = "[ServerPack] ";
    private static final String DECISION_KEY = "decision";

    /** Consent file name inside one bundle folder. */
    public static final String CONSENT_FILENAME = "consent.json";
    /** Consent files above this are treated as corrupt (fail-safe to ASK). */
    static final int MAX_CONSENT_BYTES = 4096;

    private ServerConsentStore() {
    }

    /**
     * Reads the stored decision, defaulting to {@code ASK} on any problem.
     *
     * @param serverDir the bundle folder ({@code .../servers/<id>/})
     * @return the stored decision, never {@code null}
     */
    public static @NotNull Decision read(@NotNull Path serverDir) {
        Path file = serverDir.resolve(CONSENT_FILENAME);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            return Decision.ASK;
        }
        if (bytes.length > MAX_CONSENT_BYTES) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    LOG_PREFIX + "Ignoring oversized consent file: " + file);
            return Decision.ASK;
        }
        try {
            JsonObject obj = JsonParser
                    .parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!obj.has(DECISION_KEY) || !obj.get(DECISION_KEY).isJsonPrimitive()
                    || !obj.getAsJsonPrimitive(DECISION_KEY).isString()) {
                return Decision.ASK;
            }
            String raw = obj.getAsJsonPrimitive(DECISION_KEY).getAsString().trim();
            if (Decision.ALLOW.name().equalsIgnoreCase(raw)) {
                return Decision.ALLOW;
            }
            if (Decision.DENY.name().equalsIgnoreCase(raw)) {
                return Decision.DENY;
            }
            return Decision.ASK;
        } catch (IllegalStateException | com.google.gson.JsonSyntaxException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    LOG_PREFIX + "Ignoring corrupt consent file: " + file);
            return Decision.ASK;
        }
    }

    /**
     * Persists a decision atomically (temp file + atomic move), creating the folder.
     *
     * @param serverDir the bundle folder ({@code .../servers/<id>/})
     * @param decision  the decision to remember
     * @param label     display-only server label recorded next to the decision
     * @throws IOException when the folder or file cannot be written
     */
    public static void write(@NotNull Path serverDir, @NotNull Decision decision,
            @NotNull String label) throws IOException {
        Files.createDirectories(serverDir);
        String json = "{\"decision\":\"" + decision.name().toLowerCase(java.util.Locale.ROOT)
                + "\",\"label\":\"" + escape(label) + "\"}";
        Path tmp = serverDir.resolve(CONSENT_FILENAME + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, serverDir.resolve(CONSENT_FILENAME),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static @NotNull String escape(@NotNull String value) {
        String clean = value.length() > ServerBundleInfo.MAX_TEXT_CHARS
                ? value.substring(0, ServerBundleInfo.MAX_TEXT_CHARS)
                : value;
        return clean.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

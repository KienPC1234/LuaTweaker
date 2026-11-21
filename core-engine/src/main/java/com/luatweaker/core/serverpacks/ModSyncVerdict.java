package com.luatweaker.core.serverpacks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the ModSync verdict payload, optionally carrying a server bundle offer.
 *
 * <p>Shape: {@code {"ok":..,"message":".."}} plus a {@code "download":{...}} block
 * only on mismatch when the server advertises a bundle. Old clients ignore the
 * extra block (they only read {@code ok}/{@code message}/{@code mods}).</p>
 */
public final class ModSyncVerdict {

    private ModSyncVerdict() {
    }

    /**
     * Serializes a verdict. The message is escaped for JSON string embedding.
     *
     * @param ok      whether the client is in sync
     * @param message human-readable detail (kick/chat text)
     * @param bundle  advertised bundle, embedded only when not in sync
     * @return the verdict JSON object
     */
    public static @NotNull String verdictJson(boolean ok, @NotNull String message,
            @Nullable ServerBundleInfo bundle) {
        StringBuilder sb = new StringBuilder(message.length() + 32);
        sb.append("{\"ok\":").append(ok).append(",\"message\":\"")
                .append(escape(message)).append('"');
        if (!ok && bundle != null) {
            sb.append(",\"download\":").append(ServerBundleJson.toJson(bundle));
        }
        sb.append('}');
        return sb.toString();
    }

    private static @NotNull String escape(@NotNull String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

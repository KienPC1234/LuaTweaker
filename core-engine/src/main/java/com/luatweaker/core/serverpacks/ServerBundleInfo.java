package com.luatweaker.core.serverpacks;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A server-advertised LuaMod bundle: where to fetch it and how to verify it.
 *
 * <p>Identity is the content hash, never the server address: two servers offering
 * the same bytes share one folder and one consent decision, and a hostile server
 * cannot spoof another server's identity because different bytes hash differently.
 * The human-readable {@code name} is display-only and never trusted.</p>
 *
 * <p>Instances only come from {@link ServerBundleValidation}: the canonical
 * constructor trusts its arguments, the factory does not.</p>
 */
public record ServerBundleInfo(
        @NotNull String url,
        @NotNull String sha256,
        @NotNull String version,
        @NotNull String name,
        @NotNull String filename) {

    /** Longest accepted download URL (fails closed above this). */
    public static final int MAX_URL_CHARS = 2048;
    /** Longest accepted display name or version. */
    public static final int MAX_TEXT_CHARS = 128;
    /** A SHA-256 hex digest is exactly this long. */
    public static final int SHA256_HEX_CHARS = 64;
    /** Folder/consent identity: first bytes of the file digest, traversal-proof hex. */
    public static final int BUNDLE_ID_CHARS = 16;
    /** Longest accepted zip file name. */
    public static final int MAX_FILENAME_CHARS = 128;
    /** Fallback file name when the URL carries no usable segment. */
    public static final String DEFAULT_FILENAME = "server-modpack.zip";

    /**
     * Content-bound identity: the first {@link #BUNDLE_ID_CHARS} hex chars of the
     * file digest. Safe to use as a folder name (hex only, fixed length).
     */
    public @NotNull String bundleId() {
        return sha256.substring(0, BUNDLE_ID_CHARS);
    }

    /** Verifies downloaded bytes against the pinned digest (constant-time compare). */
    public boolean verifySha256(byte[] data) {
        return data != null && MessageDigest.isEqual(sha256(data), decodeHex(sha256));
    }

    /** SHA-256 digest of arbitrary bytes (SHA-256 is mandatory in every JVM). */
    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM without SHA-256", e);
        }
    }

    /** Display label: admin name when set, otherwise the download host. Never trusted. */
    public @NotNull String label() {
        if (!name.isEmpty()) {
            return name;
        }
        try {
            String host = new URI(url).getHost();
            return host == null ? url : host;
        } catch (IllegalArgumentException | java.net.URISyntaxException e) {
            return url;
        }
    }

    private static byte[] decodeHex(@NotNull String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            out[i] = (byte) (hi * 16 + lo);
        }
        return out;
    }
}

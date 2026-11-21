package com.luatweaker.core.serverpacks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Fails a raw {@link ServerBundleSpec} closed into a {@link ServerBundleInfo}.
 *
 * <p>Only HTTPS URLs without userinfo survive; the digest must be 64 lowercase
 * hex chars; display text is trimmed and capped; the file name must be a plain
 * {@code .zip} name (falling back to the URL segment, then the default) so it
 * can never escape the bundle folder.</p>
 */
public final class ServerBundleValidation {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65_535;

    private ServerBundleValidation() {
    }

    /**
     * Validates raw inputs into a bundle, or {@code null} on any problem.
     *
     * @param spec raw config or verdict values
     * @return the validated bundle, never with untrusted shape
     */
    public static @Nullable ServerBundleInfo create(@NotNull ServerBundleSpec spec) {
        String url = validatedUrl(spec.url());
        String sha = validatedSha(spec.sha256());
        if (url == null || sha == null) {
            return null;
        }
        return new ServerBundleInfo(url, sha, validatedText(spec.version()),
                validatedText(spec.name()), validatedFilename(spec.filename(), url));
    }

    private static @Nullable String validatedUrl(@Nullable String url) {
        if (url == null) {
            return null;
        }
        String clean = url.trim();
        if (clean.isEmpty() || clean.length() > ServerBundleInfo.MAX_URL_CHARS) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(clean);
        } catch (URISyntaxException e) {
            return null;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return null;
        }
        if (!validAuthority(uri)) {
            return null;
        }
        return clean;
    }

    private static boolean validAuthority(@NotNull URI uri) {
        return uri.getHost() != null && !uri.getHost().isBlank()
                && uri.getUserInfo() == null && validPort(uri.getPort());
    }

    private static boolean validPort(int port) {
        return port == -1 || port >= MIN_PORT && port <= MAX_PORT;
    }

    private static @Nullable String validatedSha(@Nullable String sha256) {
        if (sha256 == null) {
            return null;
        }
        String clean = sha256.trim().toLowerCase(java.util.Locale.ROOT);
        if (!clean.matches("[0-9a-f]{" + ServerBundleInfo.SHA256_HEX_CHARS + "}")) {
            return null;
        }
        return clean;
    }

    private static @NotNull String validatedText(@Nullable String text) {
        if (text == null) {
            return "";
        }
        String clean = text.trim();
        if (clean.length() > ServerBundleInfo.MAX_TEXT_CHARS) {
            return clean.substring(0, ServerBundleInfo.MAX_TEXT_CHARS);
        }
        return clean;
    }

    private static @NotNull String validatedFilename(@Nullable String filename, @NotNull String url) {
        String clean = sanitizeFilename(filename);
        if (clean != null) {
            return clean;
        }
        String segment = sanitizeFilename(lastUrlSegment(url));
        if (segment != null) {
            return segment;
        }
        return ServerBundleInfo.DEFAULT_FILENAME;
    }

    private static @Nullable String sanitizeFilename(@Nullable String filename) {
        if (filename == null) {
            return null;
        }
        String clean = filename.trim();
        if (clean.isEmpty() || clean.length() > ServerBundleInfo.MAX_FILENAME_CHARS) {
            return null;
        }
        if (clean.contains("..") || clean.indexOf('/') != -1 || clean.indexOf('\\') != -1) {
            return null;
        }
        if (!allNameChars(clean)) {
            return null;
        }
        if (!clean.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            return null;
        }
        return clean;
    }

    private static boolean allNameChars(@NotNull String clean) {
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            boolean allowed = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
                    || c >= '0' && c <= '9' || c == '.' || c == '_' || c == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private static @NotNull String lastUrlSegment(@NotNull String url) {
        try {
            String path = new URI(url).getPath();
            if (path == null || path.isEmpty()) {
                return "";
            }
            int slash = path.lastIndexOf('/');
            return slash == -1 ? path : path.substring(slash + 1);
        } catch (URISyntaxException e) {
            return "";
        }
    }
}

package com.luatweaker.core.serverpacks;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Fetches, verifies and stores a bundle in its per-bundle folder.
 *
 * <p>Layout: {@code <gameDir>/luatweaker/servers/<bundleId>/luamods/<file.zip>}.
 * The folder identity is the content hash, so two servers offering the same
 * bytes share one folder and one download. Verification happens before anything
 * lands: a hash mismatch leaves no file behind, and an existing file with a
 * matching hash is reused without re-fetching.</p>
 *
 * <p>Downloaded zips are data, never code: this class never loads, mounts or
 * executes them. Reviewing and installing stays a human step (phase 2).</p>
 */
public final class BundleDownloader {

    /** Largest bundle accepted (not a gameplay tunable, a memory backstop). */
    public static final long BUNDLE_MAX_BYTES = 64L * 1024 * 1024;
    /** Zip drop folder inside one bundle folder. */
    public static final String PACKS_DIRNAME = "luamods";
    /** Suffix for the atomic-replace temp file. */
    private static final String TMP_SUFFIX = ".part";

    /** Transport hook: fetches raw bytes for a URL (throws on any failure). */
    @FunctionalInterface
    public interface Fetcher {
        byte[] fetch(@NotNull String url) throws IOException;
    }

    /** Outcome of {@link #download}: the verified file and whether it was fetched. */
    public record Outcome(@NotNull Path file, boolean fresh) {
    }

    private BundleDownloader() {
    }

    /**
     * Fetches, verifies and stores a bundle.
     *
     * @param bundle    the advertised bundle (URL + pinned digest)
     * @param serverDir the bundle folder ({@code .../servers/<id>/})
     * @param fetcher   transport (validates scheme/host on its side)
     * @return the verified zip and whether it was downloaded now
     * @throws IOException when fetching, verifying or storing fails
     */
    public static @NotNull Outcome download(@NotNull ServerBundleInfo bundle,
            @NotNull Path serverDir, @NotNull Fetcher fetcher) throws IOException {
        Path target = packsDir(serverDir).resolve(bundle.filename());
        Outcome stored = reuseIfVerified(bundle, target);
        if (stored != null) {
            return stored;
        }
        byte[] data = fetchChecked(bundle, fetcher);
        return storeVerified(bundle, target, data);
    }

    private static @NotNull Path packsDir(@NotNull Path serverDir) throws IOException {
        Path targetDir = serverDir.resolve(PACKS_DIRNAME);
        Files.createDirectories(targetDir);
        return targetDir;
    }

    private static @Nullable Outcome reuseIfVerified(@NotNull ServerBundleInfo bundle,
            @NotNull Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return null;
        }
        byte[] present = Files.readAllBytes(target);
        if (present.length > BUNDLE_MAX_BYTES || !bundle.verifySha256(present)) {
            return null;
        }
        return new Outcome(target, false);
    }

    private static byte[] fetchChecked(@NotNull ServerBundleInfo bundle,
            @NotNull Fetcher fetcher) throws IOException {
        byte[] data = fetcher.fetch(bundle.url());
        if (data == null) {
            throw new IOException("fetcher returned no bytes for " + bundle.url());
        }
        if (data.length > BUNDLE_MAX_BYTES) {
            throw new IOException("bundle is " + data.length + " bytes, above the "
                    + BUNDLE_MAX_BYTES + " byte maximum");
        }
        return data;
    }

    private static @NotNull Outcome storeVerified(@NotNull ServerBundleInfo bundle,
            @NotNull Path target, byte[] data) throws IOException {
        if (!bundle.verifySha256(data)) {
            throw new IOException("bundle SHA-256 mismatch for " + bundle.url()
                    + ": refusing to store unverified bytes");
        }
        Path tmp = target.resolveSibling(target.getFileName() + TMP_SUFFIX);
        Files.write(tmp, data,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, target,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        return new Outcome(target, true);
    }
}

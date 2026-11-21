package com.luatweaker.platform.client.download;

import com.luatweaker.core.serverpacks.BundleDownloader;
import com.luatweaker.core.serverpacks.ServerBundleInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Single-flight background downloader for server mod bundles.
 *
 * <p>One daemon thread per bundle id at most: a second start while the first is
 * still fetching reports "already running" instead of doubling the traffic.
 * Results flow back through the caller-supplied sink (chat on the client).</p>
 */
public final class ServerPackDownload {

    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private ServerPackDownload() {
    }

    /** Claims the in-flight slot for a bundle id; false when already held. */
    static boolean tryAcquire(@NotNull String bundleId) {
        return IN_FLIGHT.add(bundleId);
    }

    /** Releases the in-flight slot for a bundle id. */
    static void release(@NotNull String bundleId) {
        IN_FLIGHT.remove(bundleId);
    }

    /**
     * Downloads in the background, verifying before storing (see
     * {@link BundleDownloader}). Never blocks the calling thread.
     */
    public static void start(@NotNull ServerBundleInfo bundle, @NotNull java.nio.file.Path serverDir,
            @NotNull BundleDownloader.Fetcher fetcher, @NotNull Consumer<String> sink) {
        CompletableFuture.runAsync(() -> runDownload(bundle, serverDir, fetcher, sink))
                .exceptionally(error -> {
                    // Firewall for fetcher bugs: runDownload already sinks every declared
                    // failure, so this only fires for unchecked programming errors, which
                    // must still reach the player instead of dying silently in the pool.
                    sink.accept("Download failed for '" + bundle.label() + "': "
                            + error.getMessage());
                    return null;
                });
    }

    private static void runDownload(@NotNull ServerBundleInfo bundle,
            @NotNull java.nio.file.Path serverDir, @NotNull BundleDownloader.Fetcher fetcher,
            @NotNull Consumer<String> sink) {
        if (!tryAcquire(bundle.bundleId())) {
            sink.accept("Download for '" + bundle.label() + "' is already running.");
            return;
        }
        try {
            BundleDownloader.Outcome outcome = BundleDownloader.download(bundle, serverDir, fetcher);
            if (outcome.fresh()) {
                sink.accept("Downloaded server mod pack '" + bundle.label() + "' to "
                        + outcome.file() + ". It is NOT loaded: review it, then restart to use it.");
            } else {
                sink.accept("Server mod pack '" + bundle.label() + "' is already downloaded at "
                        + outcome.file() + ".");
            }
        } catch (java.io.IOException | IllegalStateException e) {
            sink.accept("Download failed for '" + bundle.label() + "': " + e.getMessage());
        } finally {
            release(bundle.bundleId());
        }
    }
}

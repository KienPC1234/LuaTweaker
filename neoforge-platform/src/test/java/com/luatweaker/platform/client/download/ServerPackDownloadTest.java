package com.luatweaker.platform.client.download;

import com.luatweaker.core.serverpacks.BundleDownloader;
import com.luatweaker.core.serverpacks.ServerBundleInfo;
import com.luatweaker.core.serverpacks.ServerBundleSpec;
import com.luatweaker.core.serverpacks.ServerBundleValidation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Single-flight: two starts, one download, the loser is told it is running. */
class ServerPackDownloadTest {

    private static final String URL = "https://example.com/packs/race.zip";
    private static final String SHA =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    @TempDir
    Path temp;

    @Test
    void secondStartReportsAlreadyRunning() throws InterruptedException {
        ServerBundleInfo bundle = bundleOf();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch releaseFetch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<String> messages = new CopyOnWriteArrayList<>();
        BundleDownloader.Fetcher blocking = url -> {
            entered.countDown();
            try {
                if (!releaseFetch.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("test latch timed out");
                }
            } catch (InterruptedException e) {
                throw new IOException("test interrupted", e);
            }
            return "test".getBytes(StandardCharsets.UTF_8);
        };
        ServerPackDownload.start(bundle, temp.resolve("srv"), blocking,
                message -> {
                    messages.add(message);
                    done.countDown();
                });
        entered.await(10, TimeUnit.SECONDS);
        ServerPackDownload.start(bundle, temp.resolve("srv"), blocking,
                message -> {
                    messages.add(message);
                    done.countDown();
                });
        releaseFetch.countDown();
        done.await(10, TimeUnit.SECONDS);

        assertTrue(messages.stream().anyMatch(message -> message.contains("already running")),
                "the second start must be told a download is running, got: " + messages);
    }

    private static ServerBundleInfo bundleOf() {
        ServerBundleInfo bundle = ServerBundleValidation.create(
                new ServerBundleSpec(URL, SHA, "1.0", "Race", "race.zip"));
        if (bundle == null) {
            throw new AssertionError("the fixture bundle must be valid");
        }
        return bundle;
    }
}

package com.luatweaker.core.logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Drains the bounded log queue into {@code latest.log}.
 *
 * <p>Split from {@link AsyncFileLogger} so queue ownership (the logger) and batch
 * persistence (this class) stay independent: the background timer and the shutdown
 * path both call {@link #drain()}.</p>
 */
final class LogBatchWriter {
    /** Lines written per flush; keeps one slow disk from delaying the next tick. */
    private static final int MAX_BATCH_SIZE = 100;

    private final BlockingQueue<String> queue;
    private final File logFile;

    LogBatchWriter(BlockingQueue<String> queue, File logFile) {
        this.queue = queue;
        this.logFile = logFile;
    }

    /** Flushes whatever is queued; runs on the timer and once more at shutdown. */
    void drain() {
        List<String> batch = new ArrayList<>(MAX_BATCH_SIZE);
        queue.drainTo(batch, MAX_BATCH_SIZE);
        if (!batch.isEmpty()) {
            writeBatch(batch);
        }
    }

    private void writeBatch(List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(logFile.toPath(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND), true)) {
            for (String line : lines) {
                writer.println(line);
            }
        } catch (IOException e) {
            LogLineFormatter.reportWriteFailure("[LuaTweaker] Logger write error", e);
        }
    }
}

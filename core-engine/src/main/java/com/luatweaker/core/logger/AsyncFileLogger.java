package com.luatweaker.core.logger;

import com.luatweaker.api.log.ILuaTweakerLog;
import com.luatweaker.api.log.LogStage;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asynchronous file-backed implementation of {@link ILuaTweakerLog}.
 *
 * <p>Writes are dispatched to a background worker so the game thread is never
 * blocked by I/O. The worker is a daemon {@link Timer} polling the queue (a
 * dedicated thread pool would be heavier than this single periodic flush),
 * and console mirroring goes through {@link Logger} so log levels survive.
 * Log entries are formatted cleanly in latest.log.</p>
 */
public final class AsyncFileLogger implements ILuaTweakerLog {

    /** Console mirror; JUL (not {@code System.out}) so levels stay visible. */
    private static final Logger CONSOLE = Logger.getLogger("LuaTweaker");

    private static final String LEVEL_INFO = "INFO";
    private static final String LEVEL_WARN = "WARN";
    private static final String LEVEL_ERROR = "ERROR";
    private static final String LEVEL_DEBUG = "DEBUG";

    // -- Singleton ---
    private static final ReentrantLock INSTANCE_LOCK = new ReentrantLock();
    private static final AtomicReference<AsyncFileLogger> INSTANCE_HOLDER = new AtomicReference<>();

    /**
     * @return the shared logger, recreating it after {@link #shutdown()}
     */
    public static AsyncFileLogger get() {
        AsyncFileLogger current = INSTANCE_HOLDER.get();
        if (current != null && current.running.get()) {
            return current;
        }
        INSTANCE_LOCK.lock();
        try {
            current = INSTANCE_HOLDER.get();
            if (current == null || !current.running.get()) {
                current = new AsyncFileLogger();
                INSTANCE_HOLDER.set(current);
            }
            return current;
        } finally {
            INSTANCE_LOCK.unlock();
        }
    }

    // -- Internals ---
    /** Bounded so a log flood cannot exhaust the heap; overflow is counted and reported. */
    private static final int MAX_QUEUE_SIZE = 20_000;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final AtomicLong droppedLines = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean debugEnabled = new AtomicBoolean();
    private final Timer flushTimer;

    /** Flush cadence for the background worker. */
    private static final long FLUSH_PERIOD_MILLIS = 50;

    private static final DateTimeFormatter HEADER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SEPARATOR = "=".repeat(72);
    private static final String LOG_DIR = "logs/luatweaker";
    private static final String LATEST_LOG = "latest.log";

    private final File logFile;
    private final LogBatchWriter batchWriter;

    /**
     * Engine-neutral call-site data. Replaces the old Cobalt-only LuaState argument.
     */
    public interface CallSiteProvider {
        String modId();

        String location();
    }

    /** Simple immutable {@link CallSiteProvider} for callers without an engine object. */
    public record CallSite(String modId, String location) implements CallSiteProvider {}

    private AsyncFileLogger() {
        this.logFile = new File(LOG_DIR, LATEST_LOG);
        this.batchWriter = new LogBatchWriter(queue, logFile);
        ensureParentDir();
        try (PrintWriter writer = new PrintWriter(
                Files.newBufferedWriter(logFile.toPath(), StandardCharsets.UTF_8))) {
            writer.println(SEPARATOR);
            writer.println("  LuaTweaker Log - " + HEADER_DATE_FORMAT.format(LocalDateTime.now()));
            writer.println(SEPARATOR);
        } catch (IOException e) {
            CONSOLE.log(Level.SEVERE, "AsyncFileLogger error: " + e.getMessage(), e);
        }

        TimerTask flushTask = new TimerTask() {
            @Override
            public void run() {
                batchWriter.drain();
            }
        };
        this.flushTimer = new Timer("LuaTweaker-AsyncLogger", true);
        this.flushTimer.scheduleAtFixedRate(flushTask, FLUSH_PERIOD_MILLIS, FLUSH_PERIOD_MILLIS);
    }

    private void ensureParentDir() {
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    public void setDebugEnabled(boolean enabled) { this.debugEnabled.set(enabled); }

    public String getLogFile() { return this.logFile.getAbsolutePath(); }

    public void initModLog(String modId) {
        ModLogSink.init(modId);
    }

    public void logMod(String modId, String level, String message) {
        ModLogSink.append(modId, level, message);
    }

    // -- ILuaTweakerLog implementation ---

    @Override
    public void info(LogStage stage, String message) {
        enqueue(stage, LEVEL_INFO, null, message);
    }

    @Override
    public void warn(LogStage stage, String message) {
        enqueue(stage, LEVEL_WARN, null, message);
    }

    @Override
    public void error(LogStage stage, String message) {
        enqueue(stage, LEVEL_ERROR, null, message);
    }

    @Override
    public void debug(LogStage stage, String message) {
        if (!debugEnabled.get()) {
            return;
        }
        enqueue(stage, LEVEL_DEBUG, null, "[DEBUG] " + message);
    }

    @Override
    public void script(LogStage stage, String message, String file, int line) {
        String loc = file + (line >= 0 ? ":" + line : "");
        enqueue(stage, LEVEL_INFO, loc, message);
    }

    @Override
    public void recipe(LogStage stage, String recipeId, String outputItem, String details) {
        StringBuilder sb = new StringBuilder(64);
        if (recipeId != null) {
            sb.append('[').append(recipeId).append("] ");
        }
        if (outputItem != null) {
            sb.append("-> ").append(outputItem).append(' ');
        }
        if (details != null) {
            sb.append("| ").append(details);
        }
        enqueue(stage, LEVEL_INFO, null, sb.toString().trim());
    }

    @Override
    public void section(String title) {
        String line = "=== " + title + " " + "=".repeat(Math.max(0, 56 - title.length()));
        queue.offer(line);
        CONSOLE.info("[LuaTweaker] " + line);
    }

    // -- Engine-neutral call sites ---

    public void log(String context, String message, CallSiteProvider site) {
        enqueueRaw(context, LEVEL_INFO, LogLineFormatter.siteLocation(site), message);
        logModIfPresent(site, LEVEL_INFO, message);
    }

    public void info(String context, String message, CallSiteProvider site) {
        log(context, "[INFO] " + message, site);
    }

    public void warn(String context, String message, CallSiteProvider site) {
        enqueueRaw(context, LEVEL_WARN, LogLineFormatter.siteLocation(site), message);
        logModIfPresent(site, LEVEL_WARN, message);
    }

    public void error(String context, String message, CallSiteProvider site) {
        enqueueRaw(context, LEVEL_ERROR, LogLineFormatter.siteLocation(site), message);
        logModIfPresent(site, LEVEL_ERROR, message);
    }

    // -- Internals ---

    private void enqueue(LogStage stage, String level, String fileLine, String message) {
        enqueueRaw(stage.name(), level, fileLine, message);
    }

    private void enqueueRaw(String stage, String level, String fileLine, String message) {
        String text = message == null ? "" : message;
        String location = (fileLine != null && !"system".equalsIgnoreCase(fileLine)) ? fileLine : null;
        String formatted = LogLineFormatter.formatLine(stage, level, location, text);
        if (!queue.offer(formatted)) {
            long dropped = droppedLines.incrementAndGet();
            if (dropped == 1 || dropped % 1000 == 0) {
                CONSOLE.warning("[LuaTweaker] Logger queue is full; dropped " + dropped
                        + " line(s). Reduce log volume or raise MAX_QUEUE_SIZE.");
            }
        }
        LogLineFormatter.mirrorToConsole(stage, level, location, text);
    }

    private void logModIfPresent(CallSiteProvider site, String level, String message) {
        if (site == null) {
            return;
        }
        String modId = site.modId();
        if (modId == null || modId.isBlank()) {
            return;
        }
        try {
            logMod(modId, level, message);
        } catch (IllegalArgumentException e) {
            // Keep the main log alive when a caller supplies a non-LuaMod id.
            enqueueRaw("LOGGER", LEVEL_WARN, null, "Invalid mod id for per-mod log: " + e.getMessage());
        }
    }

    public void shutdown() {
        running.set(false);
        flushTimer.cancel();
        batchWriter.drain();
        INSTANCE_LOCK.lock();
        try {
            INSTANCE_HOLDER.compareAndSet(this, null);
        } finally {
            INSTANCE_LOCK.unlock();
        }
    }
}

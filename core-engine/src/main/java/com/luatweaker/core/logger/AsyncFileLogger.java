package com.luatweaker.core.logger;

import com.luatweaker.api.log.ILuaTweakerLog;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.core.mod.LuaModPathSecurity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Asynchronous file-backed implementation of {@link ILuaTweakerLog}.
 *
 * <p>Writes are dispatched to a background thread so the game thread is
 * never blocked by I/O. Log entries are formatted cleanly in latest.log and
 * mirrored to standard output without clutter.</p>
 */
public class AsyncFileLogger implements ILuaTweakerLog {

    // -- Singleton ---
    private static AsyncFileLogger instance;

    public static synchronized AsyncFileLogger get() {
        if (instance == null || !instance.running) instance = new AsyncFileLogger();
        return instance;
    }

    // -- Internals ---
    /** Bounded so a log flood cannot exhaust the heap; overflow is counted and reported. */
    private static final int MAX_QUEUE_SIZE = 20_000;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final java.util.concurrent.atomic.AtomicLong droppedLines =
            new java.util.concurrent.atomic.AtomicLong();
    private final Thread workerThread;
    private volatile boolean running = true;
    private volatile boolean debugEnabled = false;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter HEADER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SEPARATOR = "=".repeat(72);
    private static final int MAX_BATCH_SIZE = 100;
    private static final String LOG_DIR = "logs/luatweaker";
    private static final String LATEST_LOG = "latest.log";
    private static final String MODS_LOG_DIR = "logs/luatweaker/mods";

    private final File logFile;

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
        ensureParentDir();
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, false))) {
            writer.println(SEPARATOR);
            writer.println("  LuaTweaker Log - " + HEADER_DATE_FORMAT.format(LocalDateTime.now()));
            writer.println(SEPARATOR);
        } catch (Exception e) { System.err.println("AsyncFileLogger error: " + e.getMessage()); e.printStackTrace(); }

        this.workerThread = new Thread(this::processQueue, "LuaTweaker-AsyncLogger");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    private void ensureParentDir() {
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    public void setDebugEnabled(boolean enabled) { this.debugEnabled = enabled; }

    public String getLogFile() { return this.logFile.getAbsolutePath(); }

    public void initModLog(String modId) {
        if (modId == null || modId.isBlank()) return;
        String safeModId = LuaModPathSecurity.validateModId(modId);
        File modsLogDir = new File(MODS_LOG_DIR);
        if (!modsLogDir.exists()) modsLogDir.mkdirs();
        Path modLogPath = LuaModPathSecurity.resolveContained(modsLogDir.toPath(), safeModId + ".log");
        File modLogFile = modLogPath.toFile();
        try (PrintWriter writer = new PrintWriter(new FileWriter(modLogFile, false))) {
            writer.println("========================================================================");
            writer.println("  LuaMod Dedicated Log: " + safeModId + " - " + HEADER_DATE_FORMAT.format(LocalDateTime.now()));
            writer.println("========================================================================");
        } catch (Exception e) { System.err.println("AsyncFileLogger error: " + e.getMessage()); e.printStackTrace(); }
    }

    public void logMod(String modId, String level, String message) {
        if (modId == null || modId.isBlank()) return;
        String safeModId = LuaModPathSecurity.validateModId(modId);
        File modsLogDir = new File(MODS_LOG_DIR);
        if (!modsLogDir.exists()) modsLogDir.mkdirs();
        Path modLogPath = LuaModPathSecurity.resolveContained(modsLogDir.toPath(), safeModId + ".log");
        File modLogFile = modLogPath.toFile();
        try (PrintWriter writer = new PrintWriter(new FileWriter(modLogFile, true))) {
            String ts = TIME_FORMAT.format(LocalDateTime.now());
            writer.printf("%s [%s] %s%n", ts, level.trim(), message != null ? message : "");
        } catch (Exception e) { System.err.println("AsyncFileLogger error: " + e.getMessage()); e.printStackTrace(); }
    }

    // -- ILuaTweakerLog implementation ---

    @Override
    public void info(LogStage stage, String message) {
        enqueue(stage, "INFO ", null, -1, message);
    }

    @Override
    public void warn(LogStage stage, String message) {
        enqueue(stage, "WARN ", null, -1, message);
    }

    @Override
    public void error(LogStage stage, String message) {
        enqueue(stage, "ERROR", null, -1, message);
    }

    @Override
    public void debug(LogStage stage, String message) {
        if (!debugEnabled) return;
        enqueue(stage, "DEBUG", null, -1, "[DEBUG] " + message);
    }

    @Override
    public void script(LogStage stage, String message, String file, int line) {
        String loc = file + (line >= 0 ? ":" + line : "");
        enqueue(stage, "INFO ", loc, line, message);
    }

    @Override
    public void recipe(LogStage stage, String recipeId, String outputItem, String details) {
        StringBuilder sb = new StringBuilder();
        if (recipeId != null)   sb.append("[").append(recipeId).append("] ");
        if (outputItem != null) sb.append("-> ").append(outputItem).append(" ");
        if (details != null)    sb.append("| ").append(details);
        enqueue(stage, "INFO ", null, -1, sb.toString().trim());
    }

    @Override
    public void section(String title) {
        String line = "=== " + title + " " + "=".repeat(Math.max(0, 56 - title.length()));
        queue.offer(line);
        System.out.println("[LuaTweaker] " + line);
    }

    // -- Engine-neutral call sites --

    public void log(String context, String message, CallSiteProvider site) {
        enqueueRaw(context, "INFO ", siteLocation(site), message);
        logModIfPresent(site, "INFO", message);
    }

    public void info(String context, String message, CallSiteProvider site) {
        log(context, "[INFO] " + message, site);
    }

    public void warn(String context, String message, CallSiteProvider site) {
        enqueueRaw(context, "WARN ", siteLocation(site), message);
        logModIfPresent(site, "WARN", message);
    }

    public void error(String context, String message, CallSiteProvider site) {
        enqueueRaw(context, "ERROR", siteLocation(site), message);
        logModIfPresent(site, "ERROR", message);
    }

    // -- Internals ---

    private void enqueue(LogStage stage, String level, String fileLine, int line, String message) {
        enqueueRaw(stage.name(), level, fileLine, message);
    }

    private void enqueueRaw(String stage, String level, String fileLine, String message) {
        if (message == null) message = "";
        String location = (fileLine != null && !"system".equalsIgnoreCase(fileLine)) ? fileLine : null;
        String formatted = formatLine(stage, level, location, message);
        if (!queue.offer(formatted)) {
            long dropped = droppedLines.incrementAndGet();
            if (dropped == 1 || dropped % 1000 == 0) {
                System.err.println("[LuaTweaker] Logger queue full; dropped " + dropped
                        + " line(s). Reduce log volume or raise MAX_QUEUE_SIZE.");
            }
        }

        // Clean console output without ugly system padding
        String lvl = level.trim();
        String prefix = location != null ? String.format("[LuaTweaker/%s] (%s) ", stage, location)
                                         : String.format("[LuaTweaker/%s] ", stage);

        if (message.isBlank()) {
            System.out.println();
        } else if ("ERROR".equals(lvl)) {
            System.err.println(prefix + message);
        } else {
            System.out.println(prefix + message);
        }
    }

    private String formatLine(String stage, String level, String location, String message) {
        String ts = TIME_FORMAT.format(LocalDateTime.now());
        String stageCol = String.format("%-12s", stage);
        String locStr   = location != null ? String.format("(%s) ", location) : "";
        String lvlStr   = ("INFO ".equals(level) || "INFO".equals(level.trim())) ? "" : "[" + level.trim() + "] ";
        return String.format("%s [%s] %s%s%s", ts, stageCol, locStr, lvlStr, message);
    }

    private static String siteLocation(CallSiteProvider site) {
        if (site == null) return null;
        String location = site.location();
        return (location != null && !"system".equalsIgnoreCase(location)) ? location : null;
    }

    private void logModIfPresent(CallSiteProvider site, String level, String message) {
        if (site == null) return;
        String modId = site.modId();
        if (modId == null || modId.isBlank()) return;
        try {
            logMod(modId, level, message);
        } catch (IllegalArgumentException e) {
            // Keep the main log alive when a caller supplies a non-LuaMod id.
            enqueueRaw("LOGGER", "WARN", null, "Invalid mod id for per-mod log: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        workerThread.interrupt();
        synchronized (AsyncFileLogger.class) {
            if (instance == this) {
                instance = null;
            }
        }
    }

    private void processQueue() {
        ensureParentDir();
        while (running || !queue.isEmpty()) {
            List<String> batch = new ArrayList<>();
            try {
                batch.add(running ? queue.take() : queue.poll());
                if (batch.get(0) == null) {
                    break;
                }
            } catch (InterruptedException e) {
                if (!running) {
                    continue;
                }
            }
            queue.drainTo(batch, MAX_BATCH_SIZE - batch.size());
            writeBatch(batch);
        }
    }

    private void writeBatch(List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true), true)) {
            for (String line : lines) {
                writer.println(line);
            }
        } catch (IOException e) {
            System.err.println("[LuaTweaker] Logger write error: " + e.getMessage());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}
        }
    }
}

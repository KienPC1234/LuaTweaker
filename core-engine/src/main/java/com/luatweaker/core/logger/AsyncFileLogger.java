package com.luatweaker.core.logger;

import com.luatweaker.api.log.ILuaTweakerLog;
import com.luatweaker.api.log.LogStage;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.debug.DebugHelpers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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

    // ── Singleton ─────────────────────────────────────────────────────────
    private static AsyncFileLogger instance;

    public static synchronized AsyncFileLogger get() {
        if (instance == null) instance = new AsyncFileLogger();
        return instance;
    }

    // ── Internals ─────────────────────────────────────────────────────────
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Thread workerThread;
    private volatile boolean running = true;
    private volatile boolean debugEnabled = false;

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final SimpleDateFormat HEADER_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final String SEPARATOR = "=".repeat(72);
    private static final int MAX_BATCH_SIZE = 100;

    private final File logFile;

    private AsyncFileLogger() {
        this.logFile = new File("logs/luatweaker/latest.log");
        ensureParentDir();
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, false))) {
            writer.println(SEPARATOR);
            writer.println("  LuaTweaker Log - " + HEADER_DATE_FORMAT.format(new Date()));
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
        File modsLogDir = new File("logs/luatweaker/mods");
        if (!modsLogDir.exists()) modsLogDir.mkdirs();
        File modLogFile = new File(modsLogDir, modId + ".log");
        try (PrintWriter writer = new PrintWriter(new FileWriter(modLogFile, false))) {
            writer.println("========================================================================");
            writer.println("  LuaMod Dedicated Log: " + modId + " - " + HEADER_DATE_FORMAT.format(new Date()));
            writer.println("========================================================================");
        } catch (Exception e) { System.err.println("AsyncFileLogger error: " + e.getMessage()); e.printStackTrace(); }
    }

    public void logMod(String modId, String level, String message) {
        if (modId == null || modId.isBlank()) return;
        File modsLogDir = new File("logs/luatweaker/mods");
        if (!modsLogDir.exists()) modsLogDir.mkdirs();
        File modLogFile = new File(modsLogDir, modId + ".log");
        try (PrintWriter writer = new PrintWriter(new FileWriter(modLogFile, true))) {
            String ts = TIME_FORMAT.format(new Date());
            writer.printf("%s [%s] %s%n", ts, level.trim(), message != null ? message : "");
        } catch (Exception e) { System.err.println("AsyncFileLogger error: " + e.getMessage()); e.printStackTrace(); }
    }

    // ── ILuaTweakerLog implementation ─────────────────────────────────────

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

    public void info(LogStage stage, String message, LuaState state) {
        String fileLine = resolveFileLine(state);
        enqueue(stage, "INFO ", fileLine, -1, message);
    }

    public void error(LogStage stage, String message, LuaState state) {
        String fileLine = resolveFileLine(state);
        enqueue(stage, "ERROR", fileLine, -1, message);
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

    // ── Legacy raw API (backward-compat with old call sites) ──────────────

    public void log(String context, String message, LuaState state) {
        String fileLine = resolveFileLine(state);
        enqueueRaw(context, "INFO ", fileLine, message);
    }

    public void info(String context, String message, LuaState state) {
        log(context, "[INFO] " + message, state);
    }

    public void warn(String context, String message, LuaState state) {
        String fileLine = resolveFileLine(state);
        enqueueRaw(context, "WARN ", fileLine, message);
    }

    public void error(String context, String message, LuaState state) {
        String fileLine = resolveFileLine(state);
        enqueueRaw(context, "ERROR", fileLine, message);
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private void enqueue(LogStage stage, String level, String fileLine, int line, String message) {
        enqueueRaw(stage.name(), level, fileLine, message);
    }

    private void enqueueRaw(String stage, String level, String fileLine, String message) {
        if (message == null) message = "";
        String location = (fileLine != null && !"system".equalsIgnoreCase(fileLine)) ? fileLine : null;
        String formatted = formatLine(stage, level, location, message);
        queue.offer(formatted);

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
        String ts = TIME_FORMAT.format(new Date());
        String stageCol = String.format("%-12s", stage);
        String locStr   = location != null ? String.format("(%s) ", location) : "";
        String lvlStr   = ("INFO ".equals(level) || "INFO".equals(level.trim())) ? "" : "[" + level.trim() + "] ";
        return String.format("%s [%s] %s%s%s", ts, stageCol, locStr, lvlStr, message);
    }

    private static String resolveFileLine(LuaState state) {
        if (state == null || state.getCurrentThread() == null) return null;
        try {
            String fl = DebugHelpers.fileLine(state.getCurrentThread());
            return (fl != null && !"system".equalsIgnoreCase(fl)) ? fl : null;
        } catch (Exception ignored) { return null; }
    }

    public void shutdown() {
        running = false;
        workerThread.interrupt();
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

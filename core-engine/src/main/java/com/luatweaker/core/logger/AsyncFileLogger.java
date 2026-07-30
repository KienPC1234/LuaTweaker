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
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Asynchronous file-backed implementation of {@link ILuaTweakerLog}.
 *
 * <p>Writes are dispatched to a background thread so the game thread is
 * never blocked by I/O. The file is always recreated fresh on each load
 * ({@code append=false}).</p>
 *
 * <h3>Log format</h3>
 * <pre>
 * 2026-07-30 10:00:01 [RECIPE_ADD  ] (server/mymod.lua:12)  [INFO ] Added shapeless: my_mod:bread
 * 2026-07-30 10:00:01 [RECIPE_APPLY]                        [INFO ] Stage complete in 4 ms
 * </pre>
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

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final String SEPARATOR = "─".repeat(72);

    private final File logFile;

    private AsyncFileLogger() {
        this.logFile = new File("logs/luatweaker/latest.log");
        this.logFile.getParentFile().mkdirs();

        this.workerThread = new Thread(this::processQueue, "LuaTweaker-AsyncLogger");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    public void setDebugEnabled(boolean enabled) { this.debugEnabled = enabled; }

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
        if (outputItem != null) sb.append("→ ").append(outputItem).append(" ");
        if (details != null)    sb.append("| ").append(details);
        enqueue(stage, "INFO ", null, -1, sb.toString().trim());
    }

    @Override
    public void section(String title) {
        String line = "┌─ " + title + " " + "─".repeat(Math.max(0, 60 - title.length()));
        queue.offer(line);
    }

    // ── Legacy raw API (backward-compat with old call sites) ──────────────

    public void log(String context, String message, LuaState state) {
        String fileLine = resolveFileLine(state);
        String formatted = formatLine(context, "INFO ", fileLine, message);
        queue.offer(formatted);
    }

    public void info(String context, String message, LuaState state) {
        log(context, "[INFO] " + message, state);
    }

    public void warn(String context, String message, LuaState state) {
        log(context, "[WARN] " + message, state);
    }

    public void error(String context, String message, LuaState state) {
        log(context, "[ERROR] " + message, state);
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private void enqueue(LogStage stage, String level, String fileLine, int line, String message) {
        String formatted = formatLine(stage.name(), level, fileLine != null ? fileLine : "system", message);
        queue.offer(formatted);
    }

    private String formatLine(String stage, String level, String location, String message) {
        String ts = DATE_FORMAT.format(new Date());
        String stageCol = String.format("%-12s", stage);
        String locCol   = String.format("%-30s", location != null ? location : "system");
        return String.format("%s [%s] (%s) [%s] %s", ts, stageCol, locCol, level, message);
    }

    private static String resolveFileLine(LuaState state) {
        if (state == null || state.getCurrentThread() == null) return "system";
        try {
            String fl = DebugHelpers.fileLine(state.getCurrentThread());
            return fl != null ? fl : "system";
        } catch (Exception ignored) { return "system"; }
    }

    public void shutdown() {
        running = false;
        workerThread.interrupt();
    }

    private void processQueue() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, false), true)) {
            writer.println(SEPARATOR);
            writer.println("  LuaTweaker Log — " + DATE_FORMAT.format(new Date()));
            writer.println(SEPARATOR);
            while (running || !queue.isEmpty()) {
                try {
                    writer.println(queue.take());
                } catch (InterruptedException e) {
                    if (!running) break;
                }
            }
        } catch (IOException e) {
            System.err.println("[LuaTweaker] Logger fatal write error: " + e.getMessage());
        }
    }
}

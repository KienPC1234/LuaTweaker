package com.luatweaker.core.logger;

import com.luatweaker.core.mod.LuaModPathSecurity;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Per-LuaMod dedicated log files ({@code logs/luatweaker/mods/<modId>.log}).
 *
 * <p>Split from {@link AsyncFileLogger} because these writes are synchronous, bounded
 * and independent of the shared queue: a mod's log is created once per load and
 * appended during execution.</p>
 */
final class ModLogSink {
    private ModLogSink() {}

    private static final DateTimeFormatter HEADER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String MODS_LOG_DIR = "logs/luatweaker/mods";
    private static final String HEADER_RULE = "=".repeat(72);

    /** Creates (truncates) the dedicated log for one mod, writing a dated header. */
    static void init(String modId) {
        if (modId == null || modId.isBlank()) {
            return;
        }
        String safeModId = LuaModPathSecurity.validateModId(modId);
        File modLogFile = logFile(safeModId);
        try (PrintWriter writer = new PrintWriter(
                Files.newBufferedWriter(modLogFile.toPath(), StandardCharsets.UTF_8))) {
            writer.println(HEADER_RULE);
            writer.println("  LuaMod Dedicated Log: " + safeModId + " - "
                + HEADER_DATE_FORMAT.format(LocalDateTime.now()));
            writer.println(HEADER_RULE);
        } catch (IOException e) {
            LogLineFormatter.reportWriteFailure("AsyncFileLogger error", e);
        }
    }

    /** Appends one timestamped line to the mod's log. */
    static void append(String modId, String level, String message) {
        if (modId == null || modId.isBlank()) {
            return;
        }
        String safeModId = LuaModPathSecurity.validateModId(modId);
        File modLogFile = logFile(safeModId);
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(modLogFile.toPath(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            String ts = TIME_FORMAT.format(LocalDateTime.now());
            writer.printf("%s [%s] %s%n", ts, level.trim(), message != null ? message : "");
        } catch (IOException e) {
            LogLineFormatter.reportWriteFailure("AsyncFileLogger error", e);
        }
    }

    private static File logFile(String safeModId) {
        File modsLogDir = new File(MODS_LOG_DIR);
        if (!modsLogDir.exists()) {
            modsLogDir.mkdirs();
        }
        Path modLogPath = LuaModPathSecurity.resolveContained(modsLogDir.toPath(), safeModId + ".log");
        return modLogPath.toFile();
    }
}

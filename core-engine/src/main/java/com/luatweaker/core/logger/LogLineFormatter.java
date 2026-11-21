package com.luatweaker.core.logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Renders and mirrors one log record.
 *
 * <p>Pure formatting used by {@link AsyncFileLogger}: the file line layout and the
 * console mirror stay together so the two views never drift, and the logger itself
 * keeps only queue/lifecycle concerns.</p>
 */
final class LogLineFormatter {
    private LogLineFormatter() {}

    /** Console mirror; JUL (not {@code System.out}) so levels stay visible. */
    private static final Logger CONSOLE = Logger.getLogger("LuaTweaker");

    private static final String LEVEL_INFO = "INFO";
    private static final String LEVEL_WARN = "WARN";
    private static final String LEVEL_ERROR = "ERROR";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String STAGE_COLUMN_FORMAT = "%-12s";

    /** Clean console output without ugly system padding. */
    static void mirrorToConsole(String stage, String level, String location, String message) {
        String prefix = location != null ? String.format("[LuaTweaker/%s] (%s) ", stage, location)
            : String.format("[LuaTweaker/%s] ", stage);
        if (LEVEL_ERROR.equals(level.trim())) {
            CONSOLE.severe(prefix + message);
        } else if (LEVEL_WARN.equals(level.trim())) {
            CONSOLE.warning(prefix + message);
        } else {
            CONSOLE.info(prefix + message);
        }
    }

    static String formatLine(String stage, String level, String location, String message) {
        String ts = TIME_FORMAT.format(LocalDateTime.now());
        String stageCol = String.format(STAGE_COLUMN_FORMAT, stage);
        String locStr = location != null ? String.format("(%s) ", location) : "";
        String lvlStr = LEVEL_INFO.equals(level.trim()) ? "" : "[" + level.trim() + "] ";
        return String.format("%s [%s] %s%s%s", ts, stageCol, locStr, lvlStr, message);
    }

    static String siteLocation(AsyncFileLogger.CallSiteProvider site) {
        if (site == null) {
            return null;
        }
        String location = site.location();
        return (location != null && !"system".equalsIgnoreCase(location)) ? location : null;
    }

    /** Logs the failure that broke a log-file write; keeps the logger itself quiet. */
    static void reportWriteFailure(String context, Exception failure) {
        CONSOLE.log(Level.SEVERE, "[LuaTweaker] " + context + ": " + failure.getMessage(), failure);
    }
}

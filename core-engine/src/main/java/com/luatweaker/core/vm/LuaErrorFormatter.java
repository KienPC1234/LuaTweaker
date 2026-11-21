package com.luatweaker.core.vm;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.jetbrains.annotations.NotNull;
import org.luava.runtime.LuaException;
import org.luava.runtime.eval.CallStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders luava failures for the engine: one-line messages used by log records and
 * the multi-line script traceback shown when a LuaMod script fails to load.
 *
 * <p>Kept out of {@link LuavaEngine} so the engine stays focused on VM lifetime and
 * the traceback layout can evolve without touching execution logic.</p>
 */
final class LuaErrorFormatter {
    private LuaErrorFormatter() {}

    /** Rendered banner width, matching the former Cobalt-based linter output. */
    private static final String RULE_LINE = "=".repeat(72);
    /** Capacity covering the banner, the metadata block and the source excerpt. */
    private static final int TRACEBACK_CAPACITY = 512;
    private static final int SUMMARY_CAPACITY = 128;
    private static final int EXCERPT_CONTEXT_LINES = 2;
    private static final Pattern LINE_NUMBER_PATTERN = Pattern.compile(":(\\d+):");
    private static final String STACK_HEADER = "Lua stack:";
    private static final String UNKNOWN_FRAME = "?";

    /**
     * Renders a luava exception as one line (plus an optional Lua stack listing).
     *
     * @param exception failure raised by the VM
     * @return message, line number and Lua frames, never blank
     */
    static @NotNull String describe(@NotNull LuaException exception) {
        StringBuilder builder = new StringBuilder(SUMMARY_CAPACITY);
        String message = exception.getMessage();
        builder.append(message != null && !message.isBlank() ? message : exception.toString());
        int line = exception.getLine();
        if (line > 0) {
            builder.append(" (line ").append(line).append(')');
        }
        appendFrames(builder, exception.getLuaFrames());
        return builder.toString();
    }

    private static void appendFrames(@NotNull StringBuilder builder, List<CallStack.Frame> frames) {
        if (frames == null || frames.isEmpty()) {
            return;
        }
        builder.append(System.lineSeparator()).append(STACK_HEADER);
        for (CallStack.Frame frame : frames) {
            String source = frame.function != null ? frame.function.getSource() : UNKNOWN_FRAME;
            builder.append(System.lineSeparator()).append("  at ")
                    .append(frame.name != null ? frame.name : UNKNOWN_FRAME)
                    .append(" (").append(source != null ? source : UNKNOWN_FRAME)
                    .append(':').append(frame.line).append(')');
        }
    }

    /** Distinguishes parser errors from runtime errors by their message text. */
    static boolean isCompileError(@NotNull String details) {
        return details.contains("syntax error")
                || details.contains("unexpected symbol")
                || details.contains(" expected ")
                || details.contains("near <eof>");
    }

    /** Logs a compiler-style traceback with the offending source excerpt. */
    static void logScriptError(@NotNull String context, @NotNull File file,
                               @NotNull String details, boolean compileTime) {
        int line = firstLineNumber(details);
        StringBuilder builder = new StringBuilder(TRACEBACK_CAPACITY);
        builder.append(scriptHeader(file, line, details, compileTime));
        appendSourceExcerpt(builder, file, line);
        builder.append(RULE_LINE);
        AsyncFileLogger.get().error(LogStage.SCRIPT_LOAD, "[" + context + "] " + builder);
    }

    private static @NotNull String scriptHeader(@NotNull File file, int line, @NotNull String details,
                                                boolean compileTime) {
        return "\n" + RULE_LINE + "\n"
                + "                       "
                + (compileTime ? "LUA SYNTAX ERROR (COMPILE TIME)" : "LUA RUNTIME EXCEPTION")
                + "\n" + RULE_LINE + "\n"
                + "Source File : " + file.getName() + "\n"
                + "Line Number : " + line + "\n"
                + "Details     : " + details + "\n\n";
    }

    private static int firstLineNumber(@NotNull String details) {
        Matcher matcher = LINE_NUMBER_PATTERN.matcher(details);
        if (!matcher.find()) {
            return 1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            AsyncFileLogger.get().warn(LogStage.SYSTEM,
                    "Traceback carries an unparseable line number '" + matcher.group(1) + "': "
                            + e.getMessage());
            return 1;
        }
    }

    private static void appendSourceExcerpt(@NotNull StringBuilder builder, @NotNull File file, int line) {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            int start = Math.max(1, line - EXCERPT_CONTEXT_LINES);
            int end = Math.min(lines.size(), line + EXCERPT_CONTEXT_LINES);
            for (int index = start; index <= end; index++) {
                appendExcerptLine(builder, index, line, lines.get(index - 1));
            }
        } catch (IOException e) {
            AsyncFileLogger.get().warn(LogStage.SYSTEM,
                    "Cannot read source excerpt for " + file.getName() + ": " + e.getMessage());
        }
    }

    private static void appendExcerptLine(@NotNull StringBuilder builder, int index, int line,
                                          @NotNull String content) {
        if (index == line) {
            builder.append(String.format("  > %3d | %s%n", index, content))
                    .append(String.format("        | %s%n", "^".repeat(Math.max(1, content.length()))));
            return;
        }
        builder.append(String.format("    %3d | %s%n", index, content));
    }
}

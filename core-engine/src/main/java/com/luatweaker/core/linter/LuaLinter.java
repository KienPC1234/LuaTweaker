package com.luatweaker.core.linter;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.compiler.CompileException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LuaLinter {
    private static final Pattern COMPILE_ERROR_PATTERN = Pattern.compile("^(?:.*?:)?(\\d+): (.*)$");

    public static void logFancyCompileError(String context, String sourceName, File file, CompileException e) {
        String msg = e.getMessage();
        int errLineNum = 1;
        String errDetail = msg;

        // Parse line number and error detail
        Matcher matcher = COMPILE_ERROR_PATTERN.matcher(msg);
        if (matcher.find()) {
            try {
                errLineNum = Integer.parseInt(matcher.group(1));
                errDetail = matcher.group(2);
            } catch (NumberFormatException ignored) {}
        }

        renderFancyError(context, "LUA SYNTAX ERROR (COMPILE TIME)", sourceName, file, errLineNum, errDetail);
    }

    public static void logFancyRuntimeError(String context, String sourceName, File file, LuaError e) {
        String msg = e.getMessage(); // Traceback or error message
        int errLineNum = 1;
        String errDetail = e.getValue().toString();

        // Extract line info from traceback (e.g. "my_script.lua:10:" or "C:\path\script.lua:10:")
        Pattern runtimePattern = Pattern.compile("(?:[a-zA-Z]:)?[^:\\r\\n]+:(\\d+):");
        Matcher matcher = runtimePattern.matcher(msg != null ? msg : "");
        if (matcher.find()) {
            try {
                errLineNum = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }

        renderFancyError(context, "LUA RUNTIME EXCEPTION", sourceName, file, errLineNum, msg);
    }

    private static void renderFancyError(String context, String errorHeader, String sourceName, File file, int errLineNum, String details) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n");
        builder.append("========================================================================\n");
        builder.append("                       ").append(errorHeader).append("\n");
        builder.append("========================================================================\n");
        builder.append("Source File : ").append(sourceName).append("\n");
        builder.append("Line Number : ").append(errLineNum).append("\n");
        builder.append("Details     : ").append(details).append("\n\n");

        if (file != null && file.exists() && file.isFile()) {
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                int start = Math.max(1, errLineNum - 2);
                int end = Math.min(lines.size(), errLineNum + 2);

                for (int i = start; i <= end; i++) {
                    String lineContent = lines.get(i - 1);
                    if (i == errLineNum) {
                        builder.append(String.format("  > %3d | %s%n", i, lineContent));
                        // Print pointer underneath if we can find approximate location (optional, simple underline is fine)
                        builder.append(String.format("        | %s%n", "^".repeat(Math.max(1, lineContent.length()))));
                    } else {
                        builder.append(String.format("    %3d | %s%n", i, lineContent));
                    }
                }
            } catch (IOException ignored) {}
        }
        builder.append("========================================================================");

        // Stream fancy traceback to latest.log
        AsyncFileLogger.get().error(context, builder.toString(), null);
    }
}

package com.luatweaker.core.mod;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Writable side of a mod's file sandbox: {@code <gameDir>/luatweaker/exports/<mod_id>/}.
 *
 * <p>Writes are atomic (temp file + move) and capped; the export root is
 * created lazily with a single log line.</p>
 */
final class ModFileWriter {
    private final String modId;
    private final Path exportRoot;
    private final ModFileErrors errors;
    private final ReentrantLock exportRootLock = new ReentrantLock();
    private boolean exportRootLogged;

    ModFileWriter(@NotNull String modId, @NotNull Path exportRoot, @NotNull ModFileErrors errors) {
        this.modId = modId;
        this.exportRoot = exportRoot;
        this.errors = errors;
    }

    private Path exportTarget(@NotNull String path, boolean forWrite, @NotNull String op) {
        String rel = errors.relative(path, forWrite, op);
        if (rel.isEmpty()) {
            throw new IllegalArgumentException(errors.label(op, path) + ": path must name a file or directory");
        }
        ensureExportRoot();
        return LuaModPathSecurity.resolveContained(exportRoot, rel);
    }

    void writeExportText(@NotNull String path, String text, @NotNull String op) {
        if (text == null) {
            throw new IllegalArgumentException(errors.label(op, path) + ": text is required");
        }
        // Pre-check before getBytes() so a huge Lua string cannot allocate a second copy first.
        if (text.length() > ModFileSystem.MAX_FILE_BYTES) {
            throw new IllegalArgumentException(errors.label(op, path) + ModFileSystem.PAYLOAD_EXCEEDS
                    + ModFileSystem.MAX_FILE_BYTES + ModFileSystem.BYTES_SUFFIX);
        }
        writeExportBytes(path, text.getBytes(StandardCharsets.UTF_8), op);
    }

    void writeExportBytes(@NotNull String path, @NotNull byte[] bytes, @NotNull String op) {
        if (bytes.length > ModFileSystem.MAX_FILE_BYTES) {
            throw new IllegalArgumentException(errors.label(op, path) + ModFileSystem.PAYLOAD_EXCEEDS
                    + ModFileSystem.MAX_FILE_BYTES + ModFileSystem.BYTES_SUFFIX);
        }
        Path target = exportTarget(path, true, op);
        if (Files.isDirectory(target)) {
            throw new IllegalArgumentException(errors.label(op, path) + ModFileSystem.IS_A_DIRECTORY);
        }
        atomicWrite(target, bytes, op, path);
    }

    void appendExportText(@NotNull String path, String text, @NotNull String op) {
        if (text == null) {
            throw new IllegalArgumentException(errors.label(op, path) + ": text is required");
        }
        if (text.length() > ModFileSystem.MAX_FILE_BYTES) {
            throw new IllegalArgumentException(errors.label(op, path) + ModFileSystem.PAYLOAD_EXCEEDS
                    + ModFileSystem.MAX_FILE_BYTES + ModFileSystem.BYTES_SUFFIX);
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Path target = exportTarget(path, true, op);
        if (Files.isDirectory(target)) {
            throw new IllegalArgumentException(errors.label(op, path) + ModFileSystem.IS_A_DIRECTORY);
        }
        appendBytesChecked(target, bytes, op, path);
    }

    private void appendBytesChecked(@NotNull Path target, @NotNull byte[] bytes,
            @NotNull String op, @NotNull String path) {
        try {
            long existing = Files.exists(target) ? Files.size(target) : 0L;
            if (existing + bytes.length > ModFileSystem.MAX_FILE_BYTES) {
                throw new IllegalArgumentException(errors.label(op, path) + ": append would exceed "
                        + ModFileSystem.MAX_FILE_BYTES + ModFileSystem.BYTES_SUFFIX);
            }
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw errors.fail(op, path, e);
        }
    }

    void deleteExport(@NotNull String path, @NotNull String op) {
        String rel = errors.relative(path, true, op);
        if (rel.isEmpty() || !Files.isDirectory(exportRoot)) {
            throw errors.notFound(op, rel);
        }
        Path target = LuaModPathSecurity.resolveContained(exportRoot, rel);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw errors.notFound(op, rel);
        }
        try {
            Files.delete(target);
        } catch (IOException e) {
            throw errors.fail(op, path, e);
        }
    }

    void mkdirExport(@NotNull String path, @NotNull String op) {
        String rel = errors.relative(path, true, op);
        ensureExportRoot();
        if (rel.isEmpty()) {
            return;
        }
        Path target = LuaModPathSecurity.resolveContained(exportRoot, rel);
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            throw errors.fail(op, path, e);
        }
    }

    private void atomicWrite(@NotNull Path target, @NotNull byte[] bytes,
            @NotNull String op, @NotNull String displayPath) {
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), ".lt-", ".tmp");
            try {
                Files.write(tmp, bytes);
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw errors.fail(op, displayPath, e);
        }
    }

    /** Ensures the export root exists and returns its path string. */
    String exportDirectory() {
        ensureExportRoot();
        return exportRoot.toString();
    }

    private void ensureExportRoot() {
        exportRootLock.lock();
        try {
            if (Files.isDirectory(exportRoot)) {
                return;
            }
            try {
                Files.createDirectories(exportRoot);
                if (!exportRootLogged) {
                    exportRootLogged = true;
                    LuaTweakerLog.get().info(LogStage.SYSTEM,
                            "[" + modId + "] Export directory: " + exportRoot);
                }
            } catch (IOException e) {
                String message = "cannot create export directory " + exportRoot
                        + ": " + e.getMessage();
                LuaTweakerLog.get().error(LogStage.SYSTEM, message);
                throw new UncheckedIOException(message, e);
            }
        } finally {
            exportRootLock.unlock();
        }
    }
}

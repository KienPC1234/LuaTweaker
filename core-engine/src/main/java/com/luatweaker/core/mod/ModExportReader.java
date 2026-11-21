package com.luatweaker.core.mod;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * Read-only side of a mod's export sandbox.
 */
final class ModExportReader {
    private final Path exportRoot;
    private final ModFileErrors errors;

    ModExportReader(@NotNull Path exportRoot, @NotNull ModFileErrors errors) {
        this.exportRoot = exportRoot;
        this.errors = errors;
    }

    private boolean exportRootExists() {
        return Files.isDirectory(exportRoot);
    }

    byte[] readExportBytes(@NotNull String path, @NotNull String op) {
        String rel = errors.relative(path, false, op);
        if (rel.isEmpty()) {
            throw new IllegalArgumentException(errors.label(op, path) + ": path must name a file");
        }
        Path file = requireRegularExportFile(rel, op);
        try (InputStream in = Files.newInputStream(file)) {
            return ModFileSystem.readCapped(in, errors, op, rel);
        } catch (IOException e) {
            throw errors.fail(op, rel, e);
        }
    }

    private Path requireRegularExportFile(@NotNull String rel, @NotNull String op) {
        if (!exportRootExists()) {
            throw errors.notFound(op, rel);
        }
        Path file = LuaModPathSecurity.resolveContained(exportRoot, rel);
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException(errors.label(op, rel) + ModFileSystem.IS_A_DIRECTORY);
        }
        if (!Files.isRegularFile(file)) {
            throw errors.notFound(op, rel);
        }
        return file;
    }

    boolean exportExists(@NotNull String path, @NotNull String op) {
        String rel = errors.relative(path, false, op);
        if (rel.isEmpty()) {
            return exportRootExists();
        }
        return exportRootExists() && Files.exists(LuaModPathSecurity.resolveContained(exportRoot, rel));
    }

    List<String> exportList(@NotNull String path, @NotNull String op) {
        String rel = errors.relative(path, false, op);
        if (!exportRootExists()) {
            return List.of();
        }
        return ModDirLister.listDiskChildren(exportRoot, rel, op, errors);
    }

    ModFileSystem.FileStat exportStat(@NotNull String path, @NotNull String op) {
        String rel = errors.relative(path, false, op);
        if (!exportRootExists()) {
            throw errors.notFound(op, rel);
        }
        Path target = rel.isEmpty() ? exportRoot : LuaModPathSecurity.resolveContained(exportRoot, rel);
        if (!Files.exists(target)) {
            throw errors.notFound(op, rel);
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);
            return new ModFileSystem.FileStat(attrs.size(), attrs.isDirectory(),
                    attrs.lastModifiedTime().toMillis());
        } catch (IOException e) {
            throw errors.fail(op, rel, e);
        }
    }
}

package com.luatweaker.core.mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Read-only side of a mod's file sandbox: the mod's own folder or {@code .zip}.
 *
 * <p>Every path is validated and contained before use; symlinks never leak
 * through listings. Size and entry-count caps fail loudly instead of
 * truncating.</p>
 */
final class ModFileReader {
    private final Path modRoot;
    private final Path modZip;
    private final ModFileErrors errors;

    ModFileReader(@Nullable Path modRoot, @Nullable Path modZip, @NotNull ModFileErrors errors) {
        this.modRoot = modRoot;
        this.modZip = modZip;
        this.errors = errors;
    }

    byte[] readModBytes(@NotNull String path, @NotNull String op) {
        String rel = errors.relative(path, false, op);
        if (rel.isEmpty()) {
            throw new IllegalArgumentException(errors.label(op, path) + ": path must name a file, not the mod root");
        }
        if (modRoot != null) {
            return readDiskFile(modRoot, rel, op);
        }
        return readZipBytes(rel, op);
    }

    private byte[] readDiskFile(@NotNull Path root, @NotNull String rel, @NotNull String op) {
        Path file = LuaModPathSecurity.resolveContained(root, rel);
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException(errors.label(op, rel) + ModFileSystem.IS_A_DIRECTORY);
        }
        if (!Files.isRegularFile(file)) {
            throw errors.notFound(op, rel);
        }
        try (InputStream in = Files.newInputStream(file)) {
            return ModFileSystem.readCapped(in, errors, op, rel);
        } catch (IOException e) {
            throw errors.fail(op, rel, e);
        }
    }

    private byte[] readZipBytes(@NotNull String rel, @NotNull String op) {
        Path zipPath = modZip;
        if (zipPath == null) {
            throw errors.notFound(op, rel);
        }
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zip.getEntry(rel);
            if (entry == null) {
                throw errors.notFound(op, rel);
            }
            if (entry.isDirectory()) {
                throw new IllegalArgumentException(errors.label(op, rel) + ModFileSystem.IS_A_DIRECTORY);
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return ModFileSystem.readCapped(in, errors, op, rel);
            }
        } catch (IOException e) {
            throw errors.fail(op, rel, e);
        }
    }

    boolean modExists(@NotNull String path, @NotNull String op) {
        String rel = errors.relative(path, false, op);
        if (rel.isEmpty()) {
            return true;
        }
        if (modRoot != null) {
            Path target = LuaModPathSecurity.resolveContained(modRoot, rel);
            return Files.exists(target);
        }
        Path zipPath = modZip;
        if (zipPath == null) {
            return false;
        }
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            return zip.getEntry(rel) != null || ModDirLister.hasZipPrefix(zip, rel);
        } catch (IOException e) {
            throw errors.fail(op, rel, e);
        }
    }

    List<String> modList(@NotNull String path, @NotNull String op) {
        String rel = errors.relative(path, false, op);
        if (modRoot != null) {
            return ModDirLister.listDiskChildren(modRoot, rel, op, errors);
        }
        if (modZip == null) {
            throw errors.notFound(op, rel);
        }
        return ModDirLister.listZipChildren(modZip, rel, op, errors);
    }

    ModFileSystem.FileStat modStat(@NotNull String path, @NotNull String op) {
        String rel = errors.relative(path, false, op);
        if (modRoot != null) {
            Path target = rel.isEmpty() ? modRoot : LuaModPathSecurity.resolveContained(modRoot, rel);
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
        return statZipEntry(rel, op);
    }

    private ModFileSystem.FileStat statZipEntry(@NotNull String rel, @NotNull String op) {
        Path zipPath = modZip;
        if (zipPath == null) {
            throw errors.notFound(op, rel);
        }
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = rel.isEmpty() ? null : zip.getEntry(rel);
            if (entry != null) {
                // getSize() is -1 when the central directory omits the size; keep that value honest.
                return new ModFileSystem.FileStat(entry.getSize(), entry.isDirectory(), entry.getTime());
            }
            return missingEntryStat(zip, rel, op);
        } catch (IOException e) {
            throw errors.fail(op, rel, e);
        }
    }

    private ModFileSystem.FileStat missingEntryStat(@NotNull ZipFile zip,
            @NotNull String rel, @NotNull String op) {
        if (rel.isEmpty() || ModDirLister.hasZipPrefix(zip, rel)) {
            // Zip files rarely store explicit directory entries; a non-empty prefix is a directory.
            return new ModFileSystem.FileStat(0L, true, -1L);
        }
        throw errors.notFound(op, rel);
    }
}

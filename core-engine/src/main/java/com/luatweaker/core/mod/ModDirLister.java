package com.luatweaker.core.mod;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Directory listing for both mod roots (directory or zip) and the export root.
 *
 * <p>Symlinks never surface as children; listings fail loudly instead of
 * truncating past the entry cap.</p>
 */
final class ModDirLister {
    private ModDirLister() {}

    static List<String> listDiskChildren(@NotNull Path root, @NotNull String rel,
            @NotNull String op, @NotNull ModFileErrors errors) {
        Path dir = rel.isEmpty() ? root : LuaModPathSecurity.resolveContained(root, rel);
        if (!Files.exists(dir)) {
            throw errors.notFound(op, rel);
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException(errors.label(op, rel) + ": is not a directory");
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> children = Files.list(dir)) {
            children.forEach(child -> {
                // Never expose a link as a readable child: resolveContained rejects
                // links leaving the root, and hiding them keeps listings truthful.
                if (Files.isSymbolicLink(child)) {
                    return;
                }
                names.add(child.getFileName().toString());
                errors.checkEntryCap(names.size(), op, rel);
            });
        } catch (java.io.IOException e) {
            throw errors.fail(op, rel, e);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    static List<String> listZipChildren(@NotNull Path zipPath, @NotNull String rel,
            @NotNull String op, @NotNull ModFileErrors errors) {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            return collectZipChildren(zip, rel, op, errors);
        } catch (java.io.IOException e) {
            throw errors.fail(op, rel, e);
        }
    }

    private static List<String> collectZipChildren(@NotNull ZipFile zip, @NotNull String rel,
            @NotNull String op, @NotNull ModFileErrors errors) {
        requireListableZipEntry(zip, rel, op, errors);
        Set<String> unique = new LinkedHashSet<>();
        String prefix = rel.isEmpty() ? "" : rel + "/";
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            collectZipChild(entries.nextElement().getName(), prefix, unique, op, rel, errors);
        }
        List<String> names = new ArrayList<>(unique);
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private static void requireListableZipEntry(@NotNull ZipFile zip, @NotNull String rel,
            @NotNull String op, @NotNull ModFileErrors errors) {
        ZipEntry self = rel.isEmpty() ? null : zip.getEntry(rel);
        if (self != null && !self.isDirectory()) {
            throw new IllegalArgumentException(errors.label(op, rel) + ": is not a directory");
        }
        if (!rel.isEmpty() && self == null && !hasZipPrefix(zip, rel)) {
            throw errors.notFound(op, rel);
        }
    }

    private static void collectZipChild(@NotNull String name, @NotNull String prefix,
            @NotNull Set<String> unique, @NotNull String op, @NotNull String rel,
            @NotNull ModFileErrors errors) {
        if (!name.startsWith(prefix)) {
            return;
        }
        String remainder = name.substring(prefix.length());
        if (remainder.isEmpty()) {
            return;
        }
        int slash = remainder.indexOf('/');
        String child = slash >= 0 ? remainder.substring(0, slash) : remainder;
        if (child.isEmpty()) {
            return;
        }
        unique.add(child);
        errors.checkEntryCap(unique.size(), op, rel);
    }

    static boolean hasZipPrefix(@NotNull ZipFile zip, @NotNull String rel) {
        String prefix = rel + "/";
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            if (entries.nextElement().getName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}

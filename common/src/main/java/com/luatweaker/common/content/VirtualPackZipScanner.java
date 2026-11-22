package com.luatweaker.common.content;

import com.luatweaker.api.content.IDatapackService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Handles discovery and streaming of assets and data packed into .zip LuaMods.
 */
final class VirtualPackZipScanner {

    private static final String LUAMODS_DIR_NAME = "luamods";
    private static final byte[] EMPTY_BYTES = new byte[0];

    private VirtualPackZipScanner() {
    }

    static List<File> zipFiles(File luaDir) {
        File luamodsDir = LUAMODS_DIR_NAME.equalsIgnoreCase(luaDir.getName())
                ? luaDir
                : new File(luaDir, LUAMODS_DIR_NAME);
        if (!luamodsDir.isDirectory()) {
            return List.of();
        }
        File[] zips = luamodsDir.listFiles(file ->
                file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".zip"));
        if (zips == null) {
            return List.of();
        }
        return Arrays.asList(zips);
    }

    @Nullable
    static IoSupplier<InputStream> openZipResource(File luaDir, String relativePath) {
        for (File zipFile : zipFiles(luaDir)) {
            IoSupplier<InputStream> supplier = findZipSupplier(zipFile, relativePath);
            if (supplier != null) {
                return supplier;
            }
        }
        return null;
    }

    private static IoSupplier<InputStream> findZipSupplier(File zipFile, String relativePath) {
        byte[] bytes = tryReadZipEntry(zipFile, relativePath);
        return bytes.length > 0 ? () -> new ByteArrayInputStream(bytes) : null;
    }

    private static byte[] tryReadZipEntry(File zipFile, String entryName) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry != null && !entry.isDirectory()) {
                try (InputStream in = zip.getInputStream(entry)) {
                    return in.readAllBytes();
                }
            }
        } catch (IOException ignored) {
            // Ignored on purpose: skip corrupt zip entry.
        }
        return EMPTY_BYTES;
    }

    static void collectZipNamespaces(File luaDir, PackType type, Set<String> namespaces) {
        for (File zipFile : zipFiles(luaDir)) {
            processZipNamespaces(zipFile, type, namespaces);
        }
    }

    private static void processZipNamespaces(File zipFile, PackType type, Set<String> namespaces) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            var entries = zip.entries();
            String prefix = type.getDirectory() + "/";
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(prefix)) {
                    String rest = name.substring(prefix.length());
                    int slash = rest.indexOf('/');
                    if (slash > 0) {
                        namespaces.add(rest.substring(0, slash));
                    }
                }
            }
        } catch (IOException ignored) {
            // A corrupt zip must never break pack discovery for every other mod.
        }
    }

    static void collectFromZips(File luaDir, PackType type, String namespace, String prefix,
                                BiConsumer<ResourceLocation, IoSupplier<InputStream>> output,
                                IDatapackService datapackService) {
        String dirPrefix = type.getDirectory() + "/" + namespace + "/";
        for (File zipFile : zipFiles(luaDir)) {
            processZipEntries(zipFile, dirPrefix, namespace, prefix, output, datapackService);
        }
    }

    private static void processZipEntries(File zipFile, String dirPrefix, String namespace, String prefix,
                                          BiConsumer<ResourceLocation, IoSupplier<InputStream>> output,
                                          IDatapackService datapackService) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                handleZipEntry(zipFile, entry, dirPrefix, namespace, prefix, output, datapackService);
            }
        } catch (IOException ignored) {
            // Ignored on purpose: skip corrupt zip.
        }
    }

    private static void handleZipEntry(File zipFile, ZipEntry entry, String dirPrefix, String namespace,
                                       String prefix, BiConsumer<ResourceLocation, IoSupplier<InputStream>> output,
                                       IDatapackService datapackService) {
        if (entry.isDirectory()) {
            return;
        }
        String name = entry.getName();
        if (!name.startsWith(dirPrefix)) {
            return;
        }
        String relativePath = name.substring(dirPrefix.length());
        if (!VirtualPackDirectoryScanner.matchesPrefix(relativePath, prefix)) {
            return;
        }
        if (datapackService != null && datapackService.getVirtualFiles().containsKey(name)) {
            return;
        }
        ResourceLocation rl = VirtualPackDirectoryScanner.safeResourceLocation(namespace, relativePath);
        if (rl != null) {
            output.accept(rl, findZipSupplier(zipFile, name));
        }
    }
}

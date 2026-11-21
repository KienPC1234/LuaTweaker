package com.luatweaker.core.mod;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans directories and zips for manifests. Never touches mod state or the
 * loaded set, so management UIs can call it freely.
 */
final class ModDiscovery {
    private ModDiscovery() {}

    private static final String MANIFEST_FILE = "manifest.json";
    private static final String DEFAULT_CONFIG_FILE = "default_config.json";
    private static final String ZIP_SUFFIX = ".zip";

    static @NotNull List<DiscoveredMod> discoverMods(@NotNull File luamodsDir) {
        List<DiscoveredMod> discovered = new ArrayList<>();
        File[] files = luamodsDir.listFiles();
        if (files == null || files.length == 0) {
            return discovered;
        }
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) {
            DiscoveredMod mod = file.isDirectory() ? readDirectoryMod(file) : readZipMod(file);
            if (mod != null) {
                discovered.add(mod);
            }
        }
        return discovered;
    }

    /** Reads a directory mod, or {@code null} for a missing/invalid manifest. */
    private static DiscoveredMod readDirectoryMod(@NotNull File dir) {
        File manifestFile = new File(dir, MANIFEST_FILE);
        if (!manifestFile.exists()) {
            return null;
        }
        try {
            String json = Files.readString(manifestFile.toPath(), StandardCharsets.UTF_8);
            LuaModManifest manifest = LuaModManifest.parseJson(json);
            if (manifest == null) {
                return null;
            }
            return new DiscoveredMod(manifest, dir, readDirectoryConfig(dir));
        } catch (IOException | IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[LuaModManager] Failed to read manifest from directory: " + dir.getName()
                            + " - " + e.getMessage());
            return null;
        }
    }

    /** Reads the shipped default config, or {@code null} when absent or unreadable. */
    private static String readDirectoryConfig(@NotNull File dir) {
        File configFile = new File(dir, DEFAULT_CONFIG_FILE);
        if (!configFile.exists()) {
            return null;
        }
        try {
            return Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[LuaModManager] Failed to read default config in " + dir.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /** Reads one script file from a directory mod, or {@code null} when absent or unreadable. */
    static String readDirectoryScript(@NotNull File dir, @NotNull String relPath) {
        java.nio.file.Path scriptPath = LuaModPathSecurity.resolveContained(dir.toPath(), relPath);
        File scriptFile = scriptPath.toFile();
        if (!scriptFile.exists()) {
            return null;
        }
        try {
            return Files.readString(scriptFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Failed to read script '" + relPath + "': " + e.getMessage());
            return null;
        }
    }

    /** Reads one zip entry, or {@code null} when absent. I/O failures propagate. */
    static String readZipEntryOrNull(@NotNull ZipFile zip, @NotNull String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return null;
        }
        try (InputStream stream = zip.getInputStream(entry)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Reads one script file from a zip mod, or {@code null} when absent or unreadable. */
    static String readZipScript(@NotNull File zipFile, @NotNull String relPath) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            return readZipEntryOrNull(zip, relPath);
        } catch (IOException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Failed to read script '" + relPath + "' from zip: " + e.getMessage());
            return null;
        }
    }

    /** Reads a zip mod, or {@code null} for non-zips and missing/invalid manifests. */
    private static DiscoveredMod readZipMod(@NotNull File file) {
        if (!file.getName().endsWith(ZIP_SUFFIX)) {
            return null;
        }
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry manifestEntry = zip.getEntry(MANIFEST_FILE);
            if (manifestEntry == null) {
                return null;
            }
            LuaModManifest manifest;
            try (InputStream manifestStream = zip.getInputStream(manifestEntry)) {
                String json = new String(manifestStream.readAllBytes(), StandardCharsets.UTF_8);
                manifest = LuaModManifest.parseJson(json);
            }
            if (manifest == null) {
                return null;
            }
            String defaultConfig = readZipEntryOrNull(zip, DEFAULT_CONFIG_FILE);
            return new DiscoveredMod(manifest, file, defaultConfig);
        } catch (IOException | IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[LuaModManager] Failed to read zip mod: " + file.getName() + " - " + e.getMessage());
            return null;
        }
    }
}

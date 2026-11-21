package com.luatweaker.core.mod;

import com.luatweaker.api.mod.IModExports;
import com.luatweaker.api.mod.IModFiles;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

/**
 * Sandboxed file access for one LuaMod.
 *
 * <p>Two roots, deliberately asymmetric:</p>
 * <ul>
 *   <li><b>read</b> — the mod's own folder or {@code .zip}, read-only;</li>
 *   <li><b>export</b> — {@code <gameDir>/luatweaker/exports/<mod_id>/}, the only
 *       writable location.</li>
 * </ul>
 *
 * <p>Every path goes through {@link LuaModPathSecurity#validateRelativePath} and
 * {@link LuaModPathSecurity#resolveContained}, so {@code ..}, absolute paths,
 * drive letters/ADS and symlinks that leave the root are rejected. This class has
 * no Minecraft/NeoForge imports and can be unit-tested on a plain temp folder.</p>
 *
 * <p>Thin facade: reads run through {@link ModFileReader}, writes through
 * {@link ModFileWriter}, error shapes through {@link ModFileErrors}.</p>
 */
final class ModFileSystem {
    /** Safety cap for a single read or write; not a gameplay tunable. */
    static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    /** Safety cap for one directory listing (fail loudly instead of truncating). */
    static final int MAX_LIST_ENTRIES = 4096;
    static final int COPY_BUFFER_BYTES = 8192;

    static final String IS_A_DIRECTORY = ": is a directory";
    static final String PAYLOAD_EXCEEDS = ": payload exceeds ";
    static final String BYTES_SUFFIX = " bytes";

    private final ILuaEngine engine;
    private final ModFileErrors errors;
    private final ModFileReader reader;
    private final ModExportReader exportReader;
    private final ModFileWriter writer;
    private final File modFileOrDir;

    ModFileSystem(ILuaEngine engine, String modId, File modFileOrDir, File exportDir) {
        this.engine = engine;
        this.modFileOrDir = modFileOrDir.getAbsoluteFile();
        this.errors = new ModFileErrors(modId);
        Path source = this.modFileOrDir.toPath().normalize();
        ModFileReader fileReader;
        if (this.modFileOrDir.isDirectory()) {
            fileReader = new ModFileReader(source, null, errors);
        } else if (this.modFileOrDir.isFile()
                && this.modFileOrDir.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            fileReader = new ModFileReader(null, source, errors);
        } else {
            throw new IllegalArgumentException("[" + modId + "] mod source is neither a directory nor a .zip: "
                    + this.modFileOrDir);
        }
        this.reader = fileReader;
        Path exportRoot = exportDir.toPath().toAbsolutePath().normalize();
        this.exportReader = new ModExportReader(exportRoot, errors);
        this.writer = new ModFileWriter(modId, exportRoot, errors);
    }

    IModFiles readHandle() {
        return new ModReadHandle();
    }

    IModExports exportHandle() {
        return new ModExportHandle();
    }

    static byte[] readCapped(InputStream in, ModFileErrors errors, String op, String rel) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(COPY_BUFFER_BYTES)) {
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            long total = 0L;
            for (;;) {
                int read = in.read(buffer);
                if (read == -1) {
                    break;
                }
                total += read;
                if (total > MAX_FILE_BYTES) {
                    throw new IllegalArgumentException(errors.label(op, rel) + ": file exceeds "
                            + MAX_FILE_BYTES + BYTES_SUFFIX);
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static String decodeUtf8(byte[] bytes, String op, String rel) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            String text = decoder.decode(ByteBuffer.wrap(bytes)).toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("[" + op + "] '" + rel
                    + "' is not valid UTF-8; use readBase64() for binary files", e);
        }
    }

    private ILuaTable listTable(List<String> names) {
        ILuaTable table = engine.createTable();
        for (int i = 0; i < names.size(); i++) {
            table.rawset(i + 1, names.get(i));
        }
        return table;
    }

    private ILuaTable statTable(FileStat stat) {
        ILuaTable table = engine.createTable();
        table.rawset("size", (double) stat.size());
        table.rawset("directory", stat.directory());
        table.rawset("modified", (double) stat.modified());
        return table;
    }

    private @NotNull ILuaValue fallbackOrNil(@Nullable ILuaValue fallback) {
        return fallback != null ? fallback : engine.nilValue();
    }

    private static boolean isNotFound(@NotNull IllegalArgumentException failure) {
        String message = failure.getMessage();
        return message != null && message.contains(": not found");
    }

    /**
     * Shared body for both {@code readJsonOr} handles: the fallback covers a missing
     * file, invalid UTF-8 and malformed JSON only. Path-security violations,
     * directories, oversized files and I/O failures still throw.
     */
    private @NotNull ILuaValue decodeJsonOr(@NotNull String path, @Nullable ILuaValue fallback,
            @NotNull String op, @NotNull BiFunction<String, String, Boolean> exists,
            @NotNull BiFunction<String, String, byte[]> read) {
        if (!exists.apply(path, op)) {
            return fallbackOrNil(fallback);
        }
        byte[] bytes;
        try {
            bytes = read.apply(path, op);
        } catch (IllegalArgumentException e) {
            if (isNotFound(e)) {
                return fallbackOrNil(fallback);
            }
            throw e;
        }
        String text;
        try {
            text = decodeUtf8(bytes, op, path);
        } catch (IllegalArgumentException e) {
            return fallbackOrNil(fallback);
        }
        try {
            return com.luatweaker.core.json.JsonServiceImpl.decodeJson(engine, text);
        } catch (IllegalArgumentException e) {
            return fallbackOrNil(fallback);
        }
    }

    record FileStat(long size, boolean directory, long modified) {
    }

    private final class ModReadHandle implements IModFiles {
        @Override
        public String readText(String path) {
            return decodeUtf8(reader.readModBytes(path, "mod:Files():readText"), "mod:Files():readText", path);
        }

        @Override
        public String readBase64(String path) {
            return Base64.getEncoder().encodeToString(reader.readModBytes(path, "mod:Files():readBase64"));
        }

        @Override
        public ILuaValue readJson(String path) {
            String text = readText(path);
            try {
                return com.luatweaker.core.json.JsonServiceImpl.decodeJson(engine, text);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("[mod:Files():readJson] '" + path + "': " + e.getMessage(), e);
            }
        }

        @Override
        public ILuaValue readJsonOr(String path, ILuaValue fallback) {
            return decodeJsonOr(path, fallback, "mod:Files():readJsonOr",
                    reader::modExists, reader::readModBytes);
        }

        @Override
        public boolean exists(String path) {
            return reader.modExists(path, "mod:Files():exists");
        }

        @Override
        public ILuaTable list(String path) {
            return listTable(reader.modList(path, "mod:Files():list"));
        }

        @Override
        public ILuaTable stat(String path) {
            return statTable(reader.modStat(path, "mod:Files():stat"));
        }

        @Override
        public String directory() {
            return modFileOrDir.getAbsolutePath();
        }
    }

    private final class ModExportHandle implements IModExports {
        @Override
        public String readText(String path) {
            return decodeUtf8(exportReader.readExportBytes(path, "mod:Exports():readText"),
                    "mod:Exports():readText", path);
        }

        @Override
        public String readBase64(String path) {
            return Base64.getEncoder().encodeToString(exportReader.readExportBytes(path, "mod:Exports():readBase64"));
        }

        @Override
        public ILuaValue readJson(String path) {
            String text = readText(path);
            try {
                return com.luatweaker.core.json.JsonServiceImpl.decodeJson(engine, text);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("[mod:Exports():readJson] '" + path + "': " + e.getMessage(), e);
            }
        }

        @Override
        public ILuaValue readJsonOr(String path, ILuaValue fallback) {
            return decodeJsonOr(path, fallback, "mod:Exports():readJsonOr",
                    exportReader::exportExists, exportReader::readExportBytes);
        }

        @Override
        public void writeText(String path, String text) {
            writer.writeExportText(path, text, "mod:Exports():writeText");
        }

        private static final String OP_WRITE_JSON = "mod:Exports():writeJson";

        @Override
        public void writeJson(String path, ILuaValue value, boolean pretty) {
            if (value == null) {
                throw new IllegalArgumentException(errors.label(OP_WRITE_JSON, path) + ": value is required");
            }
            String json;
            try {
                json = com.luatweaker.core.json.JsonServiceImpl.encodeJson(value, pretty);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(errors.label(OP_WRITE_JSON, path) + ": " + e.getMessage(), e);
            }
            writeText(path, json);
        }

        private static final String OP_WRITE_BASE64 = "mod:Exports():writeBase64";

        @Override
        public void writeBase64(String path, String base64) {
            if (base64 == null) {
                throw new IllegalArgumentException(errors.label(OP_WRITE_BASE64, path)
                        + ": base64 payload is required");
            }
            long maxEncodedLength = (MAX_FILE_BYTES / 3L + 1L) * 4L;
            if (base64.length() > maxEncodedLength) {
                throw new IllegalArgumentException(errors.label(OP_WRITE_BASE64, path)
                        + PAYLOAD_EXCEEDS + MAX_FILE_BYTES + BYTES_SUFFIX);
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(errors.label(OP_WRITE_BASE64, path)
                        + ": invalid Base64 payload", e);
            }
            writer.writeExportBytes(path, bytes, OP_WRITE_BASE64);
        }

        @Override
        public void appendText(String path, String text) {
            writer.appendExportText(path, text, "mod:Exports():appendText");
        }

        @Override
        public boolean exists(String path) {
            return exportReader.exportExists(path, "mod:Exports():exists");
        }

        @Override
        public ILuaTable list(String path) {
            return listTable(exportReader.exportList(path, "mod:Exports():list"));
        }

        @Override
        public ILuaTable stat(String path) {
            return statTable(exportReader.exportStat(path, "mod:Exports():stat"));
        }

        @Override
        public void delete(String path) {
            writer.deleteExport(path, "mod:Exports():delete");
        }

        @Override
        public void mkdir(String path) {
            writer.mkdirExport(path, "mod:Exports():mkdir");
        }

        @Override
        public String directory() {
            return writer.exportDirectory();
        }
    }
}

package com.luatweaker.core.mod;

import com.luatweaker.api.mod.IModExports;
import com.luatweaker.api.mod.IModFiles;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuavaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Sandbox guarantees for mod:Files() / mod:Exports() (no Minecraft runtime needed). */
class ModFileSystemTest {

    private static final byte[] BINARY_PAYLOAD = {0, 1, (byte) 0xFF, (byte) 0x80, 'A'};
    private static final byte[] INVALID_UTF8 = {(byte) 0xFF, (byte) 0x80, 'A'};
    private static final byte[] EXPORT_PAYLOAD = {0, 1, 2, (byte) 0xFF};

    private static final String DATA_A_TXT = "data/a.txt";
    private static final String DATA_DIR = "data";
    private static final String REPORTS_DIR = "reports";
    private static final String REPORTS_HELLO = "reports/hello.txt";
    private static final String CACHE_NESTED = "cache/nested";
    private static final String GREETING = "Xin chào";
    private static final String BANG = "!";
    private static final String RECIPES_JSON = "data/recipes.json";
    private static final String RECIPE_PAYLOAD = "{\"target\":\"ruby_sword\",\"damage\":8}";
    private static final String RESULT_JSON = "output/result.json";    private static final String RAW_BIN = "data/raw.bin";
    private static final String BAD_JSON = "data/bad.json";
    private static final String BROKEN_JSON_TEXT = "{broken json";
    private static final String BAD_BIN = "data/bad.bin";
    private static final String FALLBACK_KEY = "fallback";
    private static final String DIRECTORY_KEY = "directory";
    private static final String BIG_TXT = "big.txt";
    private static final String OUT_TXT = "out.txt";
    private static final String NOTHING_THROWN = "expected IllegalArgumentException but nothing was thrown";

    @TempDir
    Path temp;

    private ModFileSystemScenarios scenarios;

    @BeforeEach
    void setUp() throws IOException {
        scenarios = new ModFileSystemScenarios(temp, new LuavaEngine(false));
    }

    @Nested
    class ReadTextGuards {

        private IModFiles filesWithBinaries() throws IOException {
            scenarios.writeModBytes(RAW_BIN, BINARY_PAYLOAD);
            scenarios.writeModBytes(BAD_BIN, INVALID_UTF8);
            return scenarios.readHandle();
        }

        @Test
        void readTextStripsBomAndRejectsMissingFilesDataBomTxt() throws IOException {
            scenarios.writeModFile("data/bom.txt", "\uFEFFhi");
            assertEquals("hi", scenarios.readHandle().readText("data/bom.txt"),
                    "a UTF-8 BOM must be stripped from readText output");
        }

        @Test
        void readTextStripsBomAndRejectsMissingFilesRejectsDataNopeTxt() {
            IModFiles files = scenarios.readHandle();
            assertThrows(IllegalArgumentException.class,
                    () -> files.readText("data/nope.txt"),
                    "reading a file that does not exist must be rejected");
        }

        @Test
        void readTextStripsBomAndRejectsMissingFilesNotFound() {
            IModFiles files = scenarios.readHandle();
            IllegalArgumentException missing;
            try {
                files.readText("data/nope.txt");

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                missing = expectedFailure;
            }
            assertTrue(missing.getMessage().contains("not found"), missing.getMessage());
        }

        @Test
        void readTextRejectsInvalidUtf8AndBase64KeepsBinaryBytesRejectsDataBadBin() throws IOException {
            IModFiles files = filesWithBinaries();
            Base64.getDecoder().decode(files.readBase64(RAW_BIN));
            assertThrows(IllegalArgumentException.class,
                    () -> files.readText(BAD_BIN),
                    "invalid UTF-8 bytes must be rejected by readText");
        }

        @Test
        void readTextRejectsInvalidUtf8AndBase64KeepsBinaryBytesUTF8() throws IOException {
            IModFiles files = filesWithBinaries();
            Base64.getDecoder().decode(files.readBase64(RAW_BIN));
            IllegalArgumentException invalid;
            try {
                files.readText(BAD_BIN);

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                invalid = expectedFailure;
            }
            assertTrue(invalid.getMessage().contains("UTF-8"), invalid.getMessage());
        }

        @Test
        void readTextRejectsInvalidUtf8AndBase64KeepsBinaryBytesCheck() throws IOException {
            IModFiles files = filesWithBinaries();
            byte[] decoded = Base64.getDecoder().decode(files.readBase64(RAW_BIN));
            assertArrayEquals(BINARY_PAYLOAD, decoded,
                    "readBase64 must round-trip arbitrary binary bytes unchanged");
        }
    }

    @Nested
    class PathRejection {

        @Test
        void pathTraversalAndAbsolutePathsAreRejectedRejectsOutsideTxt() throws IOException {
            scenarios.writeModFile(DATA_A_TXT, "x");
            IModFiles files = scenarios.readHandle();
            assertThrows(IllegalArgumentException.class,
                    () -> files.readText("../outside.txt"),
                    "a parent-directory traversal must be rejected");
        }

        @Test
        void pathTraversalAndAbsolutePathsAreRejectedUnsafe() throws IOException {
            scenarios.writeModFile(DATA_A_TXT, "x");
            IModFiles files = scenarios.readHandle();
            IllegalArgumentException traversal;
            try {
                files.readText("../outside.txt");

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                traversal = expectedFailure;
            }
            assertTrue(traversal.getMessage().contains("unsafe"), traversal.getMessage());
        }

        @Test
        void pathTraversalAndAbsolutePathsAreRejectedRejectsEtcPasswd() throws IOException {
            scenarios.writeModFile(DATA_A_TXT, "x");
            IModFiles files = scenarios.readHandle();
            assertThrows(IllegalArgumentException.class,
                    () -> files.readText("/etc/passwd"),
                    "a POSIX absolute path must be rejected");
        }

        @Test
        void pathTraversalAndAbsolutePathsAreRejectedAbsolute() throws IOException {
            scenarios.writeModFile(DATA_A_TXT, "x");
            IModFiles files = scenarios.readHandle();
            IllegalArgumentException absolute;
            try {
                files.readText("/etc/passwd");

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                absolute = expectedFailure;
            }
            assertTrue(absolute.getMessage().contains("absolute"), absolute.getMessage());
        }

        @Test
        void pathTraversalAndAbsolutePathsAreRejectedRejectsCWindowsWinIni() throws IOException {
            scenarios.writeModFile(DATA_A_TXT, "x");
            IModFiles files = scenarios.readHandle();
            assertThrows(IllegalArgumentException.class,
                    () -> files.readText("C:/Windows/win.ini"),
                    "a Windows drive path must be rejected");
        }

        @Test
        void pathTraversalAndAbsolutePathsAreRejectedReservedCharacter() throws IOException {
            scenarios.writeModFile(DATA_A_TXT, "x");
            IModFiles files = scenarios.readHandle();
            IllegalArgumentException drive;
            try {
                files.readText("C:/Windows/win.ini");

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                drive = expectedFailure;
            }
            assertTrue(drive.getMessage().contains("reserved character"), drive.getMessage());
        }

        @Test
        void pathTraversalAndAbsolutePathsAreRejectedRejectsDataATxt() throws IOException {
            scenarios.writeModFile(DATA_A_TXT, "x");
            IModFiles files = scenarios.readHandle();
            assertThrows(IllegalArgumentException.class,
                    () -> files.list(DATA_A_TXT),
                    "listing a regular file must be rejected");
        }

        @Test
        void pathTraversalAndAbsolutePathsAreRejectedNotADirectory() throws IOException {
            scenarios.writeModFile(DATA_A_TXT, "x");
            IModFiles files = scenarios.readHandle();
            IllegalArgumentException notDirectory;
            try {
                files.list(DATA_A_TXT);

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                notDirectory = expectedFailure;
            }
            assertTrue(notDirectory.getMessage().contains("not a directory"), notDirectory.getMessage());
        }
    }

    @Nested
    class ListingAndStat {

        private IModFiles populatedData() throws IOException {
            scenarios.writeModFile(DATA_A_TXT, "12345");
            scenarios.writeModFile("data/sub/b.txt", "x");
            return scenarios.readHandle();
        }

        @Test
        void listAndStatDescribeTheModFolderNames() throws IOException {
            ILuaTable names = populatedData().list(DATA_DIR);
            assertEquals(2, names.length(),
                    "the data folder must list exactly its file and sub-directory");
        }

        @Test
        void listAndStatDescribeTheModFolderATxt() throws IOException {
            ILuaTable names = populatedData().list(DATA_DIR);
            assertEquals("a.txt", names.rawget(1).asString(),
                    "the file in the data folder must be listed first, alphabetically");
        }

        @Test
        void listAndStatDescribeTheModFolderSub() throws IOException {
            ILuaTable names = populatedData().list(DATA_DIR);
            assertEquals("sub", names.rawget(2).asString(),
                    "the sub-directory must be listed after the file");
        }

        @Test
        void listAndStatDescribeTheModFolderSize() throws IOException {
            ILuaTable stat = populatedData().stat(DATA_A_TXT);
            assertEquals(5.0, stat.rawget("size").asDouble(),
                    "stat.size must report the byte size of a regular file");
        }

        @Test
        void listAndStatDescribeTheModFolderDirectory() throws IOException {
            ILuaTable stat = populatedData().stat(DATA_A_TXT);
            assertFalse(stat.rawget(DIRECTORY_KEY).asBoolean(),
                    "stat must flag a regular file as not a directory");
        }

        @Test
        void listAndStatDescribeTheModFolderDirectory2() throws IOException {
            ILuaTable dirStat = populatedData().stat(DATA_DIR);
            assertTrue(dirStat.rawget(DIRECTORY_KEY).asBoolean(),
                    "stat must flag a real directory as a directory");
        }

        @Test
        void listAndStatDescribeTheModFolderIsAbsolute() throws IOException {
            IModFiles files = populatedData();
            assertTrue(Path.of(files.directory()).isAbsolute(),
                    "mod:Files().directory() must be an absolute path");
        }

        @Test
        void exportListOfMissingDirectoryIsEmpty() {
            IModExports exports = scenarios.exportHandle();
            assertEquals(0, exports.list("missing").length(),
                    "listing a missing export directory must be empty, not an error");
        }
    }

    @Nested
    class ExportWriteAppend {

        @Test
        void exportWritesReadsAppendsAndDeletesReportsHelloTxt() throws IOException {
            scenarios.writeReport();
            assertEquals(GREETING,
                    Files.readString(scenarios.exportDir.resolve(REPORTS_HELLO), StandardCharsets.UTF_8),
                    "writeText must store the UTF-8 text at the requested export path");
        }

        @Test
        void exportWritesReadsAppendsAndDeletesReportsHelloTxt2() throws IOException {
            IModExports exports = scenarios.writeReport();
            assertEquals(GREETING, exports.readText(REPORTS_HELLO),
                    "readText must return the text just written through the export handle");
        }

        @Test
        void exportWritesReadsAppendsAndDeletesReportsHelloTxt3() throws IOException {
            IModExports exports = scenarios.appendReport();
            assertEquals(GREETING + BANG, exports.readText(REPORTS_HELLO),
                    "appendText must append to the existing export text");
        }

        @Test
        void exportWritesReadsAppendsAndDeletesList() throws IOException {
            IModExports exports = scenarios.appendReport();
            ILuaTable list = exports.list(REPORTS_DIR);
            assertEquals(1, list.length(),
                    "listing the reports folder must show the single appended file");
        }

        @Test
        void exportWritesReadsAppendsAndDeletesHelloTxt() throws IOException {
            IModExports exports = scenarios.appendReport();
            ILuaTable list = exports.list(REPORTS_DIR);
            assertEquals("hello.txt", list.rawget(1).asString(),
                    "the listed entry must be the file name inside the reports folder");
        }

        @Test
        void exportWritesReadsAppendsAndDeletesReportsHelloTxt4() throws IOException {
            IModExports exports = scenarios.appendReport();
            assertEquals((double) (GREETING + BANG).getBytes(StandardCharsets.UTF_8).length,
                    exports.stat(REPORTS_HELLO).rawget("size").asDouble(),
                    "stat.size must equal the UTF-8 byte count of the appended text");
        }
    }

    @Nested
    class ExportDeleteAndMkdir {

        @Test
        void exportWritesReadsAppendsAndDeletesIsDirectory() {
            scenarios.cleanupReport();
            assertTrue(Files.isDirectory(scenarios.exportDir),
                    "the export root must remain a directory after write/append/delete");
        }

        @Test
        void exportWritesReadsAppendsAndDeletesCacheNested() throws IOException {
            IModExports exports = scenarios.mkdirNested();
            assertTrue(exports.stat(CACHE_NESTED).rawget(DIRECTORY_KEY).asBoolean(),
                    "mkdir must create a nested directory that stat reports as a directory");
        }

        @Test
        void exportWritesReadsAppendsAndDeletesReportsHelloTxt5() {
            IModExports exports = scenarios.cleanupReport();
            assertFalse(exports.exists(REPORTS_HELLO),
                    "a deleted export file must no longer exist");
        }

        @Test
        void exportWritesReadsAppendsAndDeletesRejectsReportsHelloTxt() {
            IModExports exports = scenarios.cleanupReport();
            assertThrows(IllegalArgumentException.class, () -> exports.delete(REPORTS_HELLO),
                    "deleting an already deleted export file must be rejected");
        }
    }

    @Nested
    class ExportBase64 {

        private IModExports writeBinaryPayload() {
            IModExports exports = scenarios.exportHandle();
            exports.writeBase64("bin/raw.bin", Base64.getEncoder().encodeToString(EXPORT_PAYLOAD));
            return exports;
        }

        @Test
        void exportBase64RoundTripsAndRejectsInvalidPayloadBinRawBin() {
            IModExports exports = writeBinaryPayload();
            assertArrayEquals(EXPORT_PAYLOAD,
                    Base64.getDecoder().decode(exports.readBase64("bin/raw.bin")),
                    "writeBase64/readBase64 must round-trip binary bytes unchanged");
        }

        @Test
        void exportBase64RoundTripsAndRejectsInvalidPayloadRejectsBinBadBin() {
            IModExports exports = writeBinaryPayload();
            assertThrows(IllegalArgumentException.class,
                    () -> exports.writeBase64("bin/bad.bin", "not base64!!"),
                    "a non-Base64 payload must be rejected before it reaches disk");
        }

        @Test
        void exportBase64RoundTripsAndRejectsInvalidPayloadInvalidBase64() {
            IModExports exports = writeBinaryPayload();
            IllegalArgumentException invalid;
            try {
                exports.writeBase64("bin/bad.bin", "not base64!!");

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                invalid = expectedFailure;
            }
            assertTrue(invalid.getMessage().contains("invalid Base64"), invalid.getMessage());
        }

        @Test
        void exportBase64RoundTripsAndRejectsInvalidPayloadBinBadBin() {
            IModExports exports = writeBinaryPayload();
            assertFalse(exports.exists("bin/bad.bin"),
                    "a rejected Base64 payload must not create an export file");
        }
    }

    @Nested
    class ExportSandboxEscape {

        /** Performs the escaping write, failing the test if it is accepted; returns the handle. */
        private IModExports rejectEscapingWrite() {
            IModExports exports = scenarios.exportHandle();
            assertThrows(IllegalArgumentException.class,
                    () -> exports.writeText("../evil.txt", "x"),
                    "an escaping export write must be rejected");
            return exports;
        }

        @Test
        void exportWritesCannotEscapeTheSandboxOrExceedTheCapRejectsEvilTxt() {
            IModExports exports = scenarios.exportHandle();
            assertThrows(IllegalArgumentException.class,
                    () -> exports.writeText("../evil.txt", "x"),
                    "writing outside the export sandbox must be rejected");
        }

        @Test
        void exportWritesCannotEscapeTheSandboxOrExceedTheCapUnsafe() {
            IModExports exports = scenarios.exportHandle();
            IllegalArgumentException escape;
            try {
                exports.writeText("../evil.txt", "x");

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                escape = expectedFailure;
            }
            assertTrue(escape.getMessage().contains("unsafe"), escape.getMessage());
        }

        @Test
        void exportWritesCannotEscapeTheSandboxOrExceedTheCapLuatweakerExportsEvilTxt() {
            rejectEscapingWrite();
            assertFalse(Files.exists(temp.resolve("luatweaker/exports/evil.txt")),
                    "a rejected escaping write must not create luatweaker/exports/evil.txt");
        }

        @Test
        void exportWritesCannotEscapeTheSandboxOrExceedTheCapRejectsCONTxt() {
            IModExports exports = scenarios.exportHandle();
            assertThrows(IllegalArgumentException.class,
                    () -> exports.writeText("CON.txt", "x"),
                    "a Windows reserved device name must be rejected");
        }

        @Test
        void exportWritesCannotEscapeTheSandboxOrExceedTheCapReservedFilename() {
            IModExports exports = scenarios.exportHandle();
            IllegalArgumentException reserved;
            try {
                exports.writeText("CON.txt", "x");

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                reserved = expectedFailure;
            }
            assertTrue(reserved.getMessage().contains("reserved filename"), reserved.getMessage());
        }

        @Test
        void exportWritesCannotEscapeTheSandboxOrExceedTheCapRejectsDirXTxt() {
            IModExports exports = scenarios.exportHandle();
            assertThrows(IllegalArgumentException.class,
                    () -> exports.writeText("dir./x.txt", "x"),
                    "a path segment ending in a dot must be rejected");
        }

        @Test
        void exportWritesCannotEscapeTheSandboxOrExceedTheCapMustNotEnd() {
            IModExports exports = scenarios.exportHandle();
            IllegalArgumentException trailingDot;
            try {
                exports.writeText("dir./x.txt", "x");

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                trailingDot = expectedFailure;
            }
            assertTrue(trailingDot.getMessage().contains("must not end"), trailingDot.getMessage());
        }
    }

    @Nested
    class ExportSizeLimits {

        /** Performs the oversized write, failing the test if it is accepted; returns the handle. */
        private IModExports rejectOversizedWrite() {
            IModExports exports = scenarios.exportHandle();
            String tooBig = "x".repeat((int) ModFileSystem.MAX_FILE_BYTES + 1);
            assertThrows(IllegalArgumentException.class,
                    () -> exports.writeText(BIG_TXT, tooBig),
                    "an oversized export write must be rejected");
            return exports;
        }

        @Test
        void exportWritesCannotEscapeTheSandboxOrExceedTheCapRejectsBigTxt() {
            IModExports exports = scenarios.exportHandle();
            String tooBig = "x".repeat((int) ModFileSystem.MAX_FILE_BYTES + 1);
            assertThrows(IllegalArgumentException.class,
                    () -> exports.writeText(BIG_TXT, tooBig),
                    "text above MAX_FILE_BYTES must be rejected");
        }

        @Test
        void exportWritesCannotEscapeTheSandboxOrExceedTheCapExceeds() {
            IModExports exports = scenarios.exportHandle();
            String tooBig = "x".repeat((int) ModFileSystem.MAX_FILE_BYTES + 1);
            IllegalArgumentException capped;
            try {
                exports.writeText(BIG_TXT, tooBig);

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                capped = expectedFailure;
            }
            assertTrue(capped.getMessage().contains("exceeds"), capped.getMessage());
        }

        @Test
        void exportWritesCannotEscapeTheSandboxOrExceedTheCapBigTxt() {
            assertFalse(rejectOversizedWrite().exists(BIG_TXT),
                    "an oversized export write must not create big.txt");
        }
    }

    @Nested
    class ZipMods {

        private ModFileSystem zipSystem() throws IOException {
            Path zip = temp.resolve("packed.zip");
            try (OutputStream out = Files.newOutputStream(zip);
                 ZipOutputStream zos = new ZipOutputStream(out)) {
                zos.putNextEntry(new ZipEntry("main.lua"));
                zos.write("return true".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("data/x.txt"));
                zos.write("zip text".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("data/sub/y.txt"));
                zos.write("y".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return new ModFileSystem(scenarios.engine, "packed", zip.toFile(), scenarios.exportDir.toFile());
        }

        @Test
        void zipModsAreReadableAndExposeVirtualDirectoriesDataXTxt() throws IOException {
            IModFiles files = zipSystem().readHandle();
            assertEquals("zip text", files.readText("data/x.txt"),
                    "a text file inside the mod archive must be readable through mod:Files()");
        }

        @Test
        void zipModsAreReadableAndExposeVirtualDirectoriesData() throws IOException {
            ModFileSystem fs = zipSystem();
            IModFiles files = fs.readHandle();
            // Export still works for zip mods; writes never go into the archive.
            fs.exportHandle().writeText(OUT_TXT, "ok");
            assertTrue(files.exists(DATA_DIR),
                    "a directory implied by zip entries must exist as a virtual directory");
        }

        @Test
        void zipModsAreReadableAndExposeVirtualDirectoriesData2() throws IOException {
            ModFileSystem fs = zipSystem();
            IModFiles files = fs.readHandle();
            // Export still works for zip mods; writes never go into the archive.
            fs.exportHandle().writeText(OUT_TXT, "ok");
            assertTrue(files.stat(DATA_DIR).rawget(DIRECTORY_KEY).asBoolean(),
                    "stat on a virtual zip directory must report directory=true");
        }

        @Test
        void zipModsAreReadableAndExposeVirtualDirectoriesDataXTxt2() throws IOException {
            ModFileSystem fs = zipSystem();
            IModFiles files = fs.readHandle();
            // Export still works for zip mods; writes never go into the archive.
            fs.exportHandle().writeText(OUT_TXT, "ok");
            assertEquals(8.0, files.stat("data/x.txt").rawget("size").asDouble(),
                    "stat.size for a zip entry must be the uncompressed byte size");
        }

        @Test
        void zipModsAreReadableAndExposeVirtualDirectoriesNames() throws IOException {
            ModFileSystem fs = zipSystem();
            IModFiles files = fs.readHandle();
            ILuaTable names = files.list(DATA_DIR);
            // Export still works for zip mods; writes never go into the archive.
            fs.exportHandle().writeText(OUT_TXT, "ok");
            assertEquals(2, names.length(),
                    "listing a virtual zip directory must expose both its entries");
        }

        @Test
        void zipModsAreReadableAndExposeVirtualDirectoriesSub() throws IOException {
            ModFileSystem fs = zipSystem();
            IModFiles files = fs.readHandle();
            ILuaTable names = files.list(DATA_DIR);
            // Export still works for zip mods; writes never go into the archive.
            fs.exportHandle().writeText(OUT_TXT, "ok");
            assertEquals("sub", names.rawget(1).asString(),
                    "a sub-directory must be listed before the file inside a virtual zip directory");
        }

        @Test
        void zipModsAreReadableAndExposeVirtualDirectoriesXTxt() throws IOException {
            ModFileSystem fs = zipSystem();
            IModFiles files = fs.readHandle();
            ILuaTable names = files.list(DATA_DIR);
            // Export still works for zip mods; writes never go into the archive.
            fs.exportHandle().writeText(OUT_TXT, "ok");
            assertEquals("x.txt", names.rawget(2).asString(),
                    "the zip file entry must appear as the second listed name");
        }

        @Test
        void zipModsAreReadableAndExposeVirtualDirectoriesOutTxt() throws IOException {
            ModFileSystem fs = zipSystem();
            fs.readHandle();
            // Export still works for zip mods; writes never go into the archive.
            fs.exportHandle().writeText(OUT_TXT, "ok");
            assertEquals("ok", Files.readString(scenarios.exportDir.resolve(OUT_TXT), StandardCharsets.UTF_8),
                    "exports of a zip mod must still be written to the export directory");
        }
    }

    @Nested
    class JsonRead {

        private ILuaValue readRecipes() throws IOException {
            scenarios.writeModFile(RECIPES_JSON, RECIPE_PAYLOAD);
            return scenarios.readHandle().readJson(RECIPES_JSON);
        }

        @Test
        void readJsonAndWriteJsonRoundtripVal() throws IOException {
            assertTrue(readRecipes().isTable(),
                    "readJson must decode a JSON object into a Lua table");
        }

        @Test
        void readJsonAndWriteJsonRoundtripTarget() throws IOException {
            assertEquals("ruby_sword", readRecipes().asTable().rawget("target").asString(),
                    "readJson must keep the string field of the decoded object");
        }

        @Test
        void readJsonAndWriteJsonRoundtripDamage() throws IOException {
            assertEquals(8.0, readRecipes().asTable().rawget("damage").asDouble(),
                    "readJson must keep the numeric field of the decoded object");
        }

        @Test
        void readJsonMalformedFailsLoudlyRejectsDataBadJson() throws IOException {
            scenarios.writeModFile(BAD_JSON, BROKEN_JSON_TEXT);
            IModFiles files = scenarios.readHandle();
            assertThrows(IllegalArgumentException.class,
                    () -> files.readJson(BAD_JSON),
                    "malformed JSON must be rejected instead of returning nil");
        }

        @Test
        void readJsonMalformedFailsLoudlyReadJson() throws IOException {
            scenarios.writeModFile(BAD_JSON, BROKEN_JSON_TEXT);
            IModFiles files = scenarios.readHandle();
            IllegalArgumentException ex;
            try {
                files.readJson(BAD_JSON);

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                ex = expectedFailure;
            }
            assertTrue(ex.getMessage().contains("readJson"), ex.getMessage());
        }
    }

    private ILuaTable fallbackTable(String marker) {
        ILuaTable fallback = scenarios.engine.createTable();
        fallback.rawset(FALLBACK_KEY, marker);
        return fallback;
    }

    @Nested
    class ReadJsonOrFiles {

        @Test
        void missingFileReturnsFallback() {
            IModFiles files = scenarios.readHandle();
            ILuaValue fallback = fallbackTable("shipped-default");
            ILuaValue result = files.readJsonOr("data/nope.json", fallback);
            assertEquals("shipped-default", result.asTable().rawget(FALLBACK_KEY).asString(),
                    "a missing shipped file must yield the fallback instead of throwing");
        }

        @Test
        void missingFileWithNullFallbackReturnsNil() {
            IModFiles files = scenarios.readHandle();
            assertTrue(files.readJsonOr("data/nope.json", null).isNil(),
                    "a missing file with a nil fallback must yield nil instead of throwing");
        }

        @Test
        void malformedJsonReturnsFallback() throws IOException {
            scenarios.writeModFile(BAD_JSON, BROKEN_JSON_TEXT);
            IModFiles files = scenarios.readHandle();
            ILuaValue result = files.readJsonOr(BAD_JSON, fallbackTable("bad-default"));
            assertEquals("bad-default", result.asTable().rawget(FALLBACK_KEY).asString(),
                    "malformed JSON must yield the fallback instead of throwing");
        }

        @Test
        void invalidUtf8ReturnsFallback() throws IOException {
            scenarios.writeModBytes(BAD_BIN, INVALID_UTF8);
            IModFiles files = scenarios.readHandle();
            ILuaValue result = files.readJsonOr(BAD_BIN, fallbackTable("binary-default"));
            assertEquals("binary-default", result.asTable().rawget(FALLBACK_KEY).asString(),
                    "invalid UTF-8 bytes must yield the fallback instead of throwing");
        }

        @Test
        void validJsonIgnoresFallback() throws IOException {
            scenarios.writeModFile(RECIPES_JSON, RECIPE_PAYLOAD);
            IModFiles files = scenarios.readHandle();
            ILuaValue result = files.readJsonOr(RECIPES_JSON, fallbackTable("unused"));
            assertEquals("ruby_sword", result.asTable().rawget("target").asString(),
                    "a valid JSON file must decode normally and ignore the fallback");
        }

        @Test
        void traversalStillThrowsWithFallback() {
            IModFiles files = scenarios.readHandle();
            assertThrows(IllegalArgumentException.class,
                    () -> files.readJsonOr("../outside.json", fallbackTable("must-not-apply")),
                    "a path-security violation must still throw even when a fallback is given");
        }

        @Test
        void directoryStillThrowsWithFallback() throws IOException {
            scenarios.writeModFile(DATA_A_TXT, "hello");
            IModFiles files = scenarios.readHandle();
            assertThrows(IllegalArgumentException.class,
                    () -> files.readJsonOr(DATA_DIR, fallbackTable("must-not-apply")),
                    "a directory path must still throw instead of yielding the fallback");
        }
    }

    @Nested
    class ReadJsonOrExports {

        @Test
        void exportsMissingReturnsFallback() {
            IModExports exports = scenarios.exportHandle();
            ILuaValue missing = exports.readJsonOr("saves/nope.json", fallbackTable("export-default"));
            assertEquals("export-default", missing.asTable().rawget(FALLBACK_KEY).asString(),
                    "a missing export file must yield the fallback instead of throwing");
        }

        @Test
        void exportsMalformedReturnsFallback() throws IOException {
            IModExports exports = scenarios.exportHandle();
            exports.writeText(BAD_JSON, BROKEN_JSON_TEXT);
            ILuaValue broken = exports.readJsonOr(BAD_JSON, fallbackTable("export-bad-default"));
            assertEquals("export-bad-default", broken.asTable().rawget(FALLBACK_KEY).asString(),
                    "a malformed export file must yield the fallback instead of throwing");
        }

        @Test
        void exportsTraversalStillThrowsWithFallback() {
            IModExports exports = scenarios.exportHandle();
            assertThrows(IllegalArgumentException.class,
                    () -> exports.readJsonOr("../../escape.json", fallbackTable("must-not-apply")),
                    "an export path-security violation must still throw even with a fallback");
        }
    }

    @Nested
    class JsonWrite {

        private IModExports writeResultJson() {
            IModExports exports = scenarios.exportHandle();
            ILuaTable outTable = scenarios.engine.createTable();
            outTable.rawset("status", "saved");
            outTable.rawset("count", 15L);
            exports.writeJson(RESULT_JSON, outTable, true);
            return exports;
        }

        @Test
        void readJsonAndWriteJsonRoundtripOutputResultJson() {
            IModExports exports = writeResultJson();
            assertTrue(exports.exists(RESULT_JSON),
                    "writeJson must create the export file it was asked to write");
        }

        @Test
        void readJsonAndWriteJsonRoundtripReadBack() {
            ILuaValue readBack = writeResultJson().readJson(RESULT_JSON);
            assertTrue(readBack.isTable(),
                    "a written JSON object must read back as a Lua table");
        }

        @Test
        void readJsonAndWriteJsonRoundtripStatus() {
            ILuaValue readBack = writeResultJson().readJson(RESULT_JSON);
            assertEquals("saved", readBack.asTable().rawget("status").asString(),
                    "the written string field must survive the JSON round-trip");
        }

        @Test
        void readJsonAndWriteJsonRoundtripCount() {
            ILuaValue readBack = writeResultJson().readJson(RESULT_JSON);
            assertEquals(15.0, readBack.asTable().rawget("count").asDouble(),
                    "the written integer field must survive the JSON round-trip");
        }
    }
}

/** Shared setup for the sandbox tests: temp tree, handles, and the report/export scenarios. */
final class ModFileSystemScenarios {

    private static final String REPORTS_DIR = "reports";
    private static final String REPORTS_HELLO = "reports/hello.txt";
    private static final String CACHE_NESTED = "cache/nested";
    private static final String GREETING = "Xin chào";
    private static final String BANG = "!";

    final ILuaEngine engine;
    final Path exportDir;

    private final Path modDir;

    ModFileSystemScenarios(Path temp, ILuaEngine engine) throws IOException {
        this.engine = engine;
        this.modDir = Files.createDirectories(temp.resolve("luamods/demo"));
        this.exportDir = temp.resolve("luatweaker/exports/demo");
    }

    IModFiles readHandle() {
        return new ModFileSystem(engine, "demo", modDir.toFile(), exportDir.toFile()).readHandle();
    }

    IModExports exportHandle() {
        return new ModFileSystem(engine, "demo", modDir.toFile(), exportDir.toFile()).exportHandle();
    }

    void writeModFile(String relative, String content) throws IOException {
        Path file = modDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    void writeModBytes(String relative, byte[] content) throws IOException {
        Path file = modDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    IModExports writeReport() throws IOException {
        IModExports exports = exportHandle();
        exports.writeText(REPORTS_HELLO, GREETING);
        return exports;
    }

    IModExports appendReport() throws IOException {
        IModExports exports = writeReport();
        exports.appendText(REPORTS_HELLO, BANG);
        exports.list(REPORTS_DIR);
        return exports;
    }

    IModExports mkdirNested() throws IOException {
        IModExports exports = appendReport();
        exports.mkdir(CACHE_NESTED);
        return exports;
    }

    IModExports cleanupReport() {
        try {
            IModExports exports = mkdirNested();
            exports.delete(REPORTS_HELLO);
            return exports;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to prepare export fixtures", e);
        }
    }
}

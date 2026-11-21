package com.luatweaker.core.mod;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuavaEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end binding of mod:Files() / mod:Exports() through the real VM. */
class LuaModFilesTableTest {

    private static final String DEMO_ID = "demo";
    private static final String SIDE = "server";
    private static final String MOD_DIR_PATH = "luamods/demo";
    private static final String EXPORT_DIR_PATH = "luatweaker/exports/demo";
    private static final String CUSTOM_JSON =
            "{\"title\":\"Master Sword\",\"attack\":12,\"enchants\":[\"sharpness\"]}";

    private static final String MOD_FILES_SCRIPT = """
            local files = mod:Files()
            local out = mod:Exports()
            assert(type(files:Directory()) == 'string', 'mod directory')
            assert(files:Exists('data/a.txt'), 'exists')
            assert(files:ReadText('data/a.txt') == 'hello', 'read')
            local custom = files:ReadJson('data/custom.json')
            assert(type(custom) == 'table', 'custom json is table')
            assert(custom.title == 'Master Sword', 'custom title')
            assert(custom.attack == 12, 'custom attack')
            assert(#custom.enchants == 1 and custom.enchants[1] == 'sharpness', 'custom enchants')
            out:WriteJson('saved/item.json', custom, true)
            assert(out:Exists('saved/item.json'), 'saved item exists')
            local readSaved = out:ReadJson('saved/item.json')
            assert(readSaved.title == 'Master Sword', 'read saved title')
            assert(readSaved.attack == 12, 'read saved attack')
            out:WriteText('reports/r.txt', files:ReadText('data/a.txt') .. '!')
            out:AppendText('reports/r.txt', '?')
            assert(out:ReadText('reports/r.txt') == 'hello!?', 'roundtrip')
            local st = out:Stat('reports/r.txt')
            assert(st.size == 7, 'size=' .. tostring(st.size))
            assert(st.directory == false, 'file is not a directory')
            local names = out:List('reports')
            assert(#names == 1 and names[1] == 'r.txt', 'list')
            out:Mkdir('cache')
            assert(out:Stat('cache').directory == true, 'mkdir')
            assert(type(out:Directory()) == 'string', 'export directory')
            out:WriteText('keep.txt', 'ok')
            local ok, err = pcall(function() files:ReadText('../outside.txt') end)
            assert(not ok, 'read traversal must fail')
            assert(string.find(err, 'unsafe') ~= nil, 'err=' .. tostring(err))
            local ok2, err2 = pcall(function() out:WriteText('../../escape.txt', 'x') end)
            assert(not ok2, 'write traversal must fail')
            assert(string.find(err2, 'unsafe') ~= nil, 'err2=' .. tostring(err2))
            """;

    @TempDir
    Path temp;

    private ILuaEngine runModFilesScript() throws IOException {
        Path luamods = Files.createDirectories(temp.resolve("luamods"));
        Path modDir = Files.createDirectories(luamods.resolve(DEMO_ID));
        writeModFixture(modDir);
        LuaModManifest manifest = new LuaModManifest(DEMO_ID, "Demo", "tester", "1.0.0",
                "main.lua", SIDE, List.of(), List.of(), List.of(), null);
        ILuaEngine engine = new LuavaEngine(false);
        LuaMod mod = new LuaMod(manifest, modDir.toFile(), SIDE, Map.of(), luamods.toFile());
        ILuaTable modTable = mod.createModTable(engine);
        engine.getGlobalEnvironment().rawset("mod", modTable);
        engine.executeString(MOD_FILES_SCRIPT, "mod-files-test");
        return engine;
    }

    private Path exportRoot() {
        return temp.resolve(EXPORT_DIR_PATH);
    }

    @Test
    void filesAndExportsAreBoundAsPerModCapabilitiesGetAndClearLastExecutionError() throws IOException {
        ILuaEngine engine = runModFilesScript();
        assertNull(engine.getAndClearLastExecutionError(),
                "the whole mod:Files()/mod:Exports() script must run without a Lua error");
    }

    @Test
    void filesAndExportsAreBoundAsPerModCapabilitiesReportsRTxt() throws IOException {
        runModFilesScript();
        assertEquals("hello!?", Files.readString(exportRoot().resolve("reports/r.txt"), StandardCharsets.UTF_8),
                "writeText + appendText must land on disk with both pieces of text");
    }

    @Test
    void filesAndExportsAreBoundAsPerModCapabilitiesKeepTxt() throws IOException {
        runModFilesScript();
        assertEquals("ok", Files.readString(exportRoot().resolve("keep.txt"), StandardCharsets.UTF_8),
                "writeText must store a second file next to the reports folder");
    }

    @Test
    void filesAndExportsAreBoundAsPerModCapabilitiesCache() throws IOException {
        runModFilesScript();
        assertTrue(Files.isDirectory(exportRoot().resolve("cache")),
                "Mkdir must create the cache directory under the mod export root");
    }

    @Test
    void filesAndExportsAreBoundAsPerModCapabilitiesLuatweakerExportsEscapeTxt() throws IOException {
        runModFilesScript();
        assertFalse(Files.exists(temp.resolve("luatweaker/exports/escape.txt")),
                "a rejected '../../escape.txt' write must not create a file above the export root");
    }

    @Test
    void filesAndExportsAreBoundAsPerModCapabilitiesReports() throws IOException {
        runModFilesScript();
        assertFalse(Files.exists(temp.resolve(MOD_DIR_PATH).resolve("reports")),
                "exports must never be written inside the read-only mod directory");
    }

    @Test
    void readJsonOrFallsBackThroughTheRealBinder() throws IOException {
        Path luamods = Files.createDirectories(temp.resolve("luamods"));
        Path modDir = Files.createDirectories(luamods.resolve(DEMO_ID));
        writeModFixture(modDir);
        Files.writeString(modDir.resolve("data/bad.json"), "{broken", StandardCharsets.UTF_8);
        LuaModManifest manifest = new LuaModManifest(DEMO_ID, "Demo", "tester", "1.0.0",
                "main.lua", SIDE, List.of(), List.of(), List.of(), null);
        ILuaEngine engine = new LuavaEngine(false);
        LuaMod mod = new LuaMod(manifest, modDir.toFile(), SIDE, Map.of(), luamods.toFile());
        engine.getGlobalEnvironment().rawset("mod", mod.createModTable(engine));
        engine.executeString("""
                local files = mod:Files()
                local fallback = { mode = "default" }
                local missing = files:ReadJsonOr('data/nope.json', fallback)
                assert(missing.mode == 'default', 'missing file yields fallback')
                local broken = files:ReadJsonOr('data/bad.json', fallback)
                assert(broken.mode == 'default', 'malformed file yields fallback')
                local omitted = files:ReadJsonOr('data/nope.json')
                assert(omitted == nil, 'omitted fallback yields nil')
                local ok, err = pcall(function() files:ReadJsonOr('../outside.json', fallback) end)
                assert(not ok, 'traversal with fallback must still fail')
                """, "mod-files-readjsonor-test");
        assertNull(engine.getAndClearLastExecutionError(),
                "ReadJsonOr must work through the real binder with, without and against fallback");
    }

    private static void writeModFixture(Path modDir) throws IOException {
        Path data = Files.createDirectories(modDir.resolve("data"));
        Files.writeString(data.resolve("a.txt"), "hello", StandardCharsets.UTF_8);
        Files.writeString(data.resolve("custom.json"), CUSTOM_JSON, StandardCharsets.UTF_8);
    }
}

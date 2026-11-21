package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Covers dot-relative require, traversal rejection, native aliases, file fallback and caching. */
class LuavaRequireTest {

    private static final String COUNTER_MODULE = """
            _requireLoadCount = (_requireLoadCount or 0) + 1
            return { Value = _requireLoadCount }
            """;

    private static final String NATIVE_ALIAS_SCRIPT = """
            assert(require('LuaTweaker.Content') == Content)
            assert(require('LuaTweaker.World') == World)
            assert(require('LuaTweaker.Task') == Task)
            assert(require('Task') == Task, 'short alias must work too')
            """;

    private static final String CACHE_SCRIPT = """
            local first = require('counter_mod')
            local second = require('counter_mod')
            assert(first == second, 'module table must be cached')
            assert(first.Value == 1, 'module body must run exactly once')
            assert(_requireLoadCount == 1)
            """;

    private static final String CACHE_SCRIPT_AGAIN = """
            local again = require('counter_mod')
            assert(again.Value == 1, 'cache must survive across scripts')
            """;

    @TempDir
    Path tempDir;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private void writeModule(String relativePath, String source) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    @Nested
    class FileResolution {

        @Test
        void dotRelativeRequireResolvesFromTheActiveModRoot() throws IOException {
            writeModule("mymod/src/helper.lua", "return { Value = 42 }");
            ILuaEngine engine = new LuavaEngine(false);
            engine.setLuaDirectory(tempDir.toFile());

            engine.executeString("""
                    local helper = require('.src.helper')
                    assert(helper ~= nil, 'relative require must resolve')
                    assert(helper.Value == 42, 'relative require loaded the wrong module')
                    """, "mymod/src/main.lua");
            assertNull(engine.getAndClearLastExecutionError(),
                    "a dot-relative require from the mod root must resolve without error");
        }

        @Test
        void fileFallbackLoadsModuleFromLuaDirectory() throws IOException {
            writeModule("plain_module.lua", "return { Value = 99 }");
            ILuaEngine engine = new LuavaEngine(false);
            engine.setLuaDirectory(tempDir.toFile());

            engine.executeString("""
                    local mod = require('plain_module')
                    assert(mod ~= nil and mod.Value == 99, 'file fallback must execute the module')
                    """, "file_fallback_check");
            assertNull(engine.getAndClearLastExecutionError(),
                    "require must fall back to a .lua file in the Lua directory");
        }
    }

    @Nested
    class TraversalRejection {

        @Test
        void parentDirectoryTraversalIsRejectedParentTraversalMustFailLoudly() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString("require('..secret')", "traversal_test");
            String error = engine.getAndClearLastExecutionError();
            assertNotNull(error, "parent traversal must fail loudly");
        }

        @Test
        void parentDirectoryTraversalIsRejectedTraversal() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString("require('..secret')", "traversal_test");
            String error = engine.getAndClearLastExecutionError();
            assertTrue(error.toLowerCase(Locale.ROOT).contains("traversal"),
                    "the error must explain why traversal was rejected: " + error);
        }
    }

    @Nested
    class NativeAliases {

        @Test
        void luaTweakerNativeAliasesResolveToGlobalsBootstrapTaskMustExist() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable globals = engine.getGlobalEnvironment();
            globals.rawset("Content", engine.createTable());
            globals.rawset("World", engine.createTable());
            ILuaTable task = globals.rawget("Task").asTable();
            engine.executeString(NATIVE_ALIAS_SCRIPT, "native_alias_check");
            assertNotNull(task, "bootstrap Task must exist");
        }

        @Test
        void luaTweakerNativeAliasesResolveToGlobalsGetAndClearLastExecutionError() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable globals = engine.getGlobalEnvironment();
            globals.rawset("Content", engine.createTable());
            globals.rawset("World", engine.createTable());
            engine.executeString(NATIVE_ALIAS_SCRIPT, "native_alias_check");
            assertNull(engine.getAndClearLastExecutionError(),
                    "the LuaTweaker.* aliases must resolve to the bound globals without error");
        }
    }

    @Nested
    class ModuleCache {

        private ILuaEngine loadCounterModuleTwice() throws IOException {
            writeModule("counter_mod.lua", COUNTER_MODULE);
            ILuaEngine engine = new LuavaEngine(false);
            engine.setLuaDirectory(tempDir.toFile());
            engine.executeString(CACHE_SCRIPT, "cache_check");
            engine.executeString(CACHE_SCRIPT_AGAIN, "cache_check_2");
            return engine;
        }

        @Test
        void modulesAreCachedAcrossRequireCallsAndScriptsGetAndClearLastExecutionError() throws IOException {
            ILuaEngine engine = loadCounterModuleTwice();
            assertNull(engine.getAndClearLastExecutionError(),
                    "repeated require calls across two scripts must not raise a Lua error");
        }

        @Test
        void modulesAreCachedAcrossRequireCallsAndScriptsGetAndClearLastExecutionError2() throws IOException {
            ILuaEngine engine = loadCounterModuleTwice();
            assertNull(engine.getAndClearLastExecutionError(),
                    "the cached module must stay valid for the second script as well");
        }

        @Test
        void modulesAreCachedAcrossRequireCallsAndScriptsRequireLoadCount() throws IOException {
            ILuaEngine engine = loadCounterModuleTwice();
            assertEquals(1.0, engine.getGlobalEnvironment().rawget("_requireLoadCount").asDouble(),
                    "the module body must execute exactly once across all require calls");
        }
    }
}

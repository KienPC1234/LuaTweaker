package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Covers dot-relative require, traversal rejection, native aliases, file fallback and caching. */
public class LuavaRequireTest {

    @TempDir
    Path tempDir;

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private void writeModule(String relativePath, String source) throws Exception {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    @Test
    void dotRelativeRequireResolvesFromTheActiveModRoot() throws Exception {
        writeModule("mymod/src/helper.lua", "return { Value = 42 }");
        ILuaEngine engine = new LuavaEngine(false);
        engine.setLuaDirectory(tempDir.toFile());

        engine.executeString(
            "local helper = require('.src.helper')\n" +
            "assert(helper ~= nil, 'relative require must resolve')\n" +
            "assert(helper.Value == 42, 'relative require loaded the wrong module')",
            "mymod/src/main.lua"
        );
        assertNull(engine.getAndClearLastExecutionError());
    }

    @Test
    void parentDirectoryTraversalIsRejected() {
        ILuaEngine engine = new LuavaEngine(false);
        engine.executeString("require('..secret')", "traversal_test");

        String error = engine.getAndClearLastExecutionError();
        assertNotNull(error, "parent traversal must fail loudly");
        assertTrue(error.toLowerCase().contains("traversal"),
            "the error must explain why traversal was rejected: " + error);
    }

    @Test
    void luaTweakerNativeAliasesResolveToGlobals() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        ILuaTable content = engine.createTable();
        content.rawset("NewItem", args -> engine.wrapString("item"));
        globals.rawset("Content", content);

        ILuaTable world = engine.createTable();
        globals.rawset("World", world);

        ILuaTable task = globals.rawget("Task").asTable();
        assertNotNull(task, "bootstrap Task must exist");

        engine.executeString(
            "assert(require('LuaTweaker.Content') == Content)\n" +
            "assert(require('LuaTweaker.World') == World)\n" +
            "assert(require('LuaTweaker.Task') == Task)\n" +
            "assert(require('Task') == Task, 'short alias must work too')",
            "native_alias_check"
        );
        assertNull(engine.getAndClearLastExecutionError());
    }

    @Test
    void fileFallbackLoadsModuleFromLuaDirectory() throws Exception {
        writeModule("plain_module.lua", "return { Value = 99 }");
        ILuaEngine engine = new LuavaEngine(false);
        engine.setLuaDirectory(tempDir.toFile());

        engine.executeString(
            "local mod = require('plain_module')\n" +
            "assert(mod ~= nil and mod.Value == 99, 'file fallback must execute the module')",
            "file_fallback_check"
        );
        assertNull(engine.getAndClearLastExecutionError());
    }

    @Test
    void modulesAreCachedAcrossRequireCallsAndScripts() throws Exception {
        writeModule("counter_mod.lua",
            "_requireLoadCount = (_requireLoadCount or 0) + 1\n" +
            "return { Value = _requireLoadCount }");
        ILuaEngine engine = new LuavaEngine(false);
        engine.setLuaDirectory(tempDir.toFile());

        engine.executeString(
            "local first = require('counter_mod')\n" +
            "local second = require('counter_mod')\n" +
            "assert(first == second, 'module table must be cached')\n" +
            "assert(first.Value == 1, 'module body must run exactly once')\n" +
            "assert(_requireLoadCount == 1)",
            "cache_check"
        );
        assertNull(engine.getAndClearLastExecutionError());

        engine.executeString(
            "local again = require('counter_mod')\n" +
            "assert(again.Value == 1, 'cache must survive across scripts')",
            "cache_check_2"
        );
        assertNull(engine.getAndClearLastExecutionError());
        assertEquals(1.0, engine.getGlobalEnvironment().rawget("_requireLoadCount").asDouble());
    }
}

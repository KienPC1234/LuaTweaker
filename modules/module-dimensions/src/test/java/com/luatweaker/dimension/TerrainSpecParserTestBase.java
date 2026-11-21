package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;

/** Shared Lua table builder fixture for {@link TerrainSpecParser} tests. */
class TerrainSpecParserTestBase {

    protected static final String DIMENSION_ID = "minecraft:test";

    protected ILuaEngine engine;
    protected DimensionConfig config;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void createEngine() {
        engine = LuaEngineFactory.create(false);
        config = DimensionConfig.defaults(DIMENSION_ID);
    }

    protected ILuaTable layer(String kind) {
        ILuaTable layer = engine.createTable();
        layer.rawset("kind", kind);
        return layer;
    }

    protected ILuaTable array(ILuaTable... entries) {
        ILuaTable table = engine.createTable();
        for (int i = 0; i < entries.length; i++) {
            table.rawset(i + 1, entries[i]);
        }
        return table;
    }

    protected TerrainSpec parse(ILuaTable spec) {
        return TerrainSpecParser.parse(DIMENSION_ID, spec, config);
    }

    protected String rejection(ILuaTable spec) {
        return DimensionExceptions.illegalArgumentMessage(() -> parse(spec));
    }
}

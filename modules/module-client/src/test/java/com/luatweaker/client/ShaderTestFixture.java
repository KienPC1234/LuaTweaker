package com.luatweaker.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

/**
 * Shared fixture for {@link ShaderServiceImpl} tests: a fake datapack, a per-test cache directory,
 * a headless Lua engine for parameter tables, and path helpers for the published virtual files.
 */
class ShaderTestFixture {

    protected static final String BODY = "void main() {\n"
            + "    vec4 base = texture(DiffuseSampler, texCoord);\n"
            + "    float pulse = sin(Time * 3.0) * 0.5 + 0.5;\n"
            + "    fragColor = vec4(base.rgb + pulse * 0.2, base.a);\n"
            + "}\n";

    @TempDir
    protected File cacheDir;

    protected FakeDatapack datapack;
    protected ShaderServiceImpl service;
    protected ILuaEngine engine;

    @BeforeEach
    void setUp() {
        datapack = new FakeDatapack();
        service = new ShaderServiceImpl(datapack, cacheDir);
        engine = LuaEngineFactory.create(false);
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
        service.clearAll();
    }

    /** A fresh Lua table for effect parameters. */
    protected ILuaTable params() {
        return engine.createTable();
    }

    protected String chainJson(String id) {
        return datapack.getVirtualFiles().get("assets/luatweaker/shaders/post/lt_" + id + ".json");
    }

    protected String programJson(String id) {
        return datapack.getVirtualFiles().get("assets/luatweaker/shaders/program/lt_" + id + ".json");
    }

    protected String fragmentSource(String id) {
        return datapack.getVirtualFiles().get("assets/luatweaker/shaders/program/lt_" + id + ".fsh");
    }
}

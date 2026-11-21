package com.luatweaker.storage;

import com.luatweaker.api.pal.IPlatformStorage;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

/**
 * Shared setup for storage tests: a fresh temp directory mounted as the platform
 * storage root and a fresh Lua engine per test.
 */
class StorageTestSupport {

    @TempDir
    File tempDir;

    ILuaEngine engine;

    @BeforeEach
    void openEngine() {
        engine = LuaEngineFactory.create(false);
        IPlatformStorage storage = () -> tempDir;
        Platform.setStorage(storage);
    }

    StorageServiceImpl newService() {
        return new StorageServiceImpl(engine);
    }

    static double numberOf(Object value) {
        if (!(value instanceof Number number)) {
            throw new AssertionError("expected a numeric stored value, got: " + value);
        }
        return number.doubleValue();
    }
}

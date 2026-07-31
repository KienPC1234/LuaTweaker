package com.luatweaker.storage;

import com.luatweaker.api.pal.IPlatformHelper;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.storage.IRobloxStorageService;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class StorageServiceImplTest {

    @TempDir
    static File tempDir;

    @BeforeAll
    static void setup() {
        Platform.set(new IPlatformHelper() {
            @Override
            public File getStorageDirectory() {
                return tempDir;
            }
        });
    }

    @Test
    void testWorldStorageOperations() {
        CobaltLuaEngine engine = new CobaltLuaEngine(false);
        StorageServiceImpl storageService = new StorageServiceImpl(engine);

        IRobloxStorageService.IDataStore worldStore = storageService.GetWorldStorage();
        worldStore.SetAsync("play_count", 42);

        Object val = worldStore.GetAsync("play_count");
        assertNotNull(val);
        assertEquals(42.0, ((Number) val).doubleValue());
    }

    @Test
    void testPlayerStorageIsolation() {
        CobaltLuaEngine engine = new CobaltLuaEngine(false);
        StorageServiceImpl storageService = new StorageServiceImpl(engine);

        IRobloxStorageService.IDataStore player1 = storageService.GetPlayerStorage("uuid-1");
        IRobloxStorageService.IDataStore player2 = storageService.GetPlayerStorage("uuid-2");

        player1.SetAsync("coins", 100);
        player2.SetAsync("coins", 500);

        assertEquals(100.0, ((Number) player1.GetAsync("coins")).doubleValue());
        assertEquals(500.0, ((Number) player2.GetAsync("coins")).doubleValue());
    }

    @Test
    void testSessionStorage() {
        CobaltLuaEngine engine = new CobaltLuaEngine(false);
        StorageServiceImpl storageService = new StorageServiceImpl(engine);

        IRobloxStorageService.IDataStore session = storageService.GetSessionStorage();
        session.SetAsync("temp_key", "temp_val");

        assertEquals("temp_val", session.GetAsync("temp_key"));
    }
}

package com.luatweaker.storage;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuaEngineFactory;

import com.luatweaker.api.pal.IPlatformStorage;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.storage.IRobloxStorageService;
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
        Platform.setStorage(new IPlatformStorage() {
            @Override
            public File getStorageDirectory() {
                return tempDir;
            }
        });
    }

    @Test
    void testWorldStorageOperations() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        StorageServiceImpl storageService = new StorageServiceImpl(engine);

        IRobloxStorageService.IDataStore worldStore = storageService.GetWorldStorage();
        worldStore.SetAsync("play_count", 42);

        Object val = worldStore.GetAsync("play_count");
        assertNotNull(val);
        assertEquals(42.0, ((Number) val).doubleValue());
    }

    @Test
    void testPlayerStorageIsolation() {
        ILuaEngine engine = LuaEngineFactory.create(false);
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
        ILuaEngine engine = LuaEngineFactory.create(false);
        StorageServiceImpl storageService = new StorageServiceImpl(engine);

        IRobloxStorageService.IDataStore session = storageService.GetSessionStorage();
        session.SetAsync("temp_key", "temp_val");

        assertEquals("temp_val", session.GetAsync("temp_key"));
    }

    @Test
    void unsupportedValueIsRejectedWithoutDestroyingExistingData() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        StorageServiceImpl storageService = new StorageServiceImpl(engine);
        IRobloxStorageService.IDataStore world = storageService.GetWorldStorage();

        world.SetAsync("keep", 7);
        // A userdata (function/entity/arbitrary Java object) is not BSON-encodable.
        assertThrows(IllegalArgumentException.class,
                () -> world.SetAsync("bad", engine.wrapUserdata(new Object())),
                "unsupported values must be rejected before the file is touched");

        assertEquals(7.0, ((Number) world.GetAsync("keep")).doubleValue(),
                "a rejected write must not erase previously saved values");
        File file = new File(tempDir, "world_storage.bson");
        assertTrue(file.isFile() && file.length() > 0, "the previous save must remain on disk");
    }

    @Test
    void worldStorageRoundTripsAcrossInstances() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        StorageServiceImpl first = new StorageServiceImpl(engine);
        first.GetWorldStorage().SetAsync("persisted", "value");

        StorageServiceImpl second = new StorageServiceImpl(engine);
        assertEquals("value", second.GetWorldStorage().GetAsync("persisted"),
                "a new instance must load the atomically written file");
    }

    @Test
    void tableAndArrayValuesRoundTrip() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        StorageServiceImpl storageService = new StorageServiceImpl(engine);
        IRobloxStorageService.IDataStore world = storageService.GetWorldStorage();

        ILuaTable table = engine.createTable();
        table.rawset("name", engine.wrapString("crystal"));
        ILuaTable list = engine.createTable();
        list.rawset(1, engine.wrapNumber(1));
        list.rawset(2, engine.wrapNumber(2));
        list.rawset(3, engine.wrapNumber(3));
        table.rawset("levels", list);

        world.SetAsync("profile", table);
        Object readBack = world.GetAsync("profile");
        assertTrue(readBack instanceof ILuaTable, "tables must come back as tables");
        ILuaTable profile = (ILuaTable) readBack;
        assertEquals("crystal", profile.rawget("name").asString());
        ILuaValue levels = profile.rawget("levels");
        assertTrue(levels.isTable());
        assertEquals(3, levels.asTable().length());
        assertEquals(2.0, levels.asTable().rawget(2).asDouble());
    }
}

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
    void removeAsyncDeletesAndPersistsTheRemoval() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        StorageServiceImpl first = new StorageServiceImpl(engine);
        IRobloxStorageService.IDataStore world = first.GetWorldStorage();
        world.SetAsync("keep", 1);
        world.SetAsync("drop", 2);

        assertTrue(world.RemoveAsync("drop"), "removing an existing key must report true");
        assertFalse(world.RemoveAsync("drop"), "removing a missing key must report false");
        assertNull(world.GetAsync("drop"));

        StorageServiceImpl second = new StorageServiceImpl(engine);
        assertNull(second.GetWorldStorage().GetAsync("drop"), "the removal must be written to disk");
        assertEquals(1.0, ((Number) second.GetWorldStorage().GetAsync("keep")).doubleValue());
    }

    @Test
    void getKeysReturnsStoredKeysSorted() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        StorageServiceImpl storageService = new StorageServiceImpl(engine);
        IRobloxStorageService.IDataStore world = storageService.GetWorldStorage();
        world.SetAsync("zeta", 1);
        world.SetAsync("alpha", 2);
        world.SetAsync("mid", 3);

        java.util.List<String> keys = world.GetKeys();
        assertTrue(keys.containsAll(java.util.List.of("alpha", "mid", "zeta")), "keys=" + keys);
        java.util.List<String> sorted = new java.util.ArrayList<>(keys);
        java.util.Collections.sort(sorted);
        assertEquals(sorted, keys, "GetKeys must be sorted");

        IRobloxStorageService.IDataStore player = storageService.GetPlayerStorage("uuid-keys");
        assertTrue(player.GetKeys().isEmpty(), "an untouched player store must have no keys");
        player.SetAsync("coins", 5);
        assertEquals(java.util.List.of("coins"), player.GetKeys());
    }

    @Test
    void incrementAsyncAddsAndCreatesTheKey() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        StorageServiceImpl storageService = new StorageServiceImpl(engine);
        IRobloxStorageService.IDataStore world = storageService.GetWorldStorage();

        assertEquals(1.0, world.IncrementAsync("counter", 1.0), 1e-9, "missing keys start at 0");
        assertEquals(3.5, world.IncrementAsync("counter", 2.5), 1e-9);
        assertEquals(3.5, ((Number) world.GetAsync("counter")).doubleValue(), 1e-9);

        IRobloxStorageService.IDataStore session = storageService.GetSessionStorage();
        assertEquals(-2.0, session.IncrementAsync("balance", -2.0), 1e-9);
    }

    @Test
    void incrementAsyncRejectsNonNumericValues() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        StorageServiceImpl storageService = new StorageServiceImpl(engine);
        IRobloxStorageService.IDataStore world = storageService.GetWorldStorage();
        world.SetAsync("label", "not a number");

        assertThrows(IllegalStateException.class, () -> world.IncrementAsync("label", 1.0),
                "incrementing a string must fail loudly instead of overwriting it");
        assertEquals("not a number", world.GetAsync("label"), "the stored value must be untouched");
    }

    @Test
    void luaBindingExposesRemoveKeysAndIncrement() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        StorageServiceImpl storageService = new StorageServiceImpl(engine);
        StorageLuaBinding.registerBindings(engine, storageService);

        engine.executeStringOrThrow(
                "local s = Storage:GetSessionStorage()\n" +
                "s:SetAsync('a', 1)\n" +
                "s:SetAsync('b', 'x')\n" +
                "assert(s:IncrementAsync('a', 2) == 3, 'IncrementAsync must return the new value')\n" +
                "local keys = s:GetKeys()\n" +
                "assert(type(keys) == 'table' and #keys == 2, 'GetKeys must return a Lua array')\n" +
                "assert(s:RemoveAsync('b') == true, 'RemoveAsync must report the removal')\n" +
                "assert(s:GetAsync('b') == nil, 'the removed key must be gone')\n" +
                "assert(s:RemoveAsync('b') == false, 'removing twice must report false')",
                "storage_binding");
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

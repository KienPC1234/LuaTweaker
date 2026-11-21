package com.luatweaker.storage;

import com.luatweaker.api.storage.IRobloxStorageService;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * End-to-end Lua checks of the Roblox-style Storage API.
 */
class StorageLuaBindingTest extends StorageTestSupport {

    private static final String KEY_ROUND_TRIP = "luatweaker_test.roundtrip";
    private static final String STRING_ROUND_TRIP_SCRIPT =
            "local Storage = require('LuaTweaker.Storage')\n"
            + "local session = Storage:GetSessionStorage()\n"
            + "session:SetAsync('" + KEY_ROUND_TRIP + "', '42')\n"
            + "_G._roundTrip = session:GetAsync('" + KEY_ROUND_TRIP + "')";

    private static final String DATA_STORE_SCRIPT =
            "local s = Storage:GetSessionStorage()\n"
            + "s:SetAsync('a', 1)\n"
            + "s:SetAsync('b', 'x')\n"
            + "_G._incremented = s:IncrementAsync('a', 2)\n"
            + "_G._keyCount = #s:GetKeys()\n"
            + "_G._firstRemove = s:RemoveAsync('b')\n"
            + "_G._removedValue = s:GetAsync('b')\n"
            + "_G._secondRemove = s:RemoveAsync('b')";

    private static final String FLUSH_SCRIPT =
            "local ws = Storage:GetWorldStorage()\n"
            + "ws:SetAsync('lua_key', 999)\n"
            + "ws:Flush()\n"
            + "Storage:Flush()\n"
            + "_G._flushedWorld = ws:GetAsync('lua_key')";

    @Test
    void luaStringValueRoundTripsAsString() {
        try (StorageServiceImpl service = newService()) {
            StorageLuaBinding.registerBindings(engine, service);

            engine.executeStringOrThrow(STRING_ROUND_TRIP_SCRIPT, "storage_lua_string_roundtrip");

            ILuaValue value = globals().rawget("_roundTrip");
            Assertions.assertAll("a Lua string must round-trip as a string, not a number",
                () -> Assertions.assertTrue(value != null && value.isString(),
                    "GetAsync must return a Lua string for a string that was stored"),
                () -> Assertions.assertEquals("42", value.asString(), "the stored string must round-trip"));
        }
    }

    @Test
    void luaBindingExposesRemoveKeysAndIncrement() {
        try (StorageServiceImpl service = newService()) {
            StorageLuaBinding.registerBindings(engine, service);

            engine.executeStringOrThrow(DATA_STORE_SCRIPT, "storage_binding");

            ILuaValue removedValue = globals().rawget("_removedValue");
            Assertions.assertAll("the Lua binding must expose remove, keys and increment",
                () -> Assertions.assertEquals(3.0, globals().rawget("_incremented").asDouble(), 1e-9,
                    "IncrementAsync must return the new value"),
                () -> Assertions.assertEquals(2, globals().rawget("_keyCount").asInt(),
                    "GetKeys must return a Lua array with both keys"),
                () -> Assertions.assertTrue(globals().rawget("_firstRemove").asBoolean(),
                    "RemoveAsync must report the removal of an existing key"),
                () -> Assertions.assertTrue(removedValue != null && removedValue.isNil(),
                    "the removed key must read back as nil"),
                () -> Assertions.assertFalse(globals().rawget("_secondRemove").asBoolean(),
                    "removing the same key twice must report false"));
        }
    }

    @Test
    void luaFlushBindingPersistsTheValue() {
        try (StorageServiceImpl service = newService()) {
            StorageLuaBinding.registerBindings(engine, service);

            engine.executeStringOrThrow(FLUSH_SCRIPT, "test_lua_flush");

            Assertions.assertEquals(999.0, globals().rawget("_flushedWorld").asDouble(), 1e-9,
                    "a value written and flushed from Lua must be readable immediately");
        }
    }

    @Test
    void worldStoreReadableFromLuaSeesJavaWrites() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();
            world.SetAsync("java_key", 12);
            StorageLuaBinding.registerBindings(engine, service);

            engine.executeStringOrThrow(
                    "_G._javaVisible = Storage:GetWorldStorage():GetAsync('java_key')",
                    "storage_java_visibility");

            Assertions.assertEquals(12.0, globals().rawget("_javaVisible").asDouble(), 1e-9,
                    "the Lua view of the world store must see values written through Java");
        }
    }

    private ILuaTable globals() {
        return engine.getGlobalEnvironment();
    }
}

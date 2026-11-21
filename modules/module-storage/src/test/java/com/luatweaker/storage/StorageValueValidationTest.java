package com.luatweaker.storage;

import com.luatweaker.api.storage.IRobloxStorageService;
import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

/**
 * Value validation (unsupported userdata) and structured table round-trips.
 */
class StorageValueValidationTest extends StorageTestSupport {

    private static final String KEY_KEEP = "keep";
    private static final String KEY_BAD = "bad";
    private static final String KEY_PROFILE = "profile";
    private static final String KEY_LEVELS = "levels";
    private static final String WORLD_FILE = "world_storage.bson";

    @Test
    void unsupportedValueIsRejected() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();

            Assertions.assertThrows(IllegalArgumentException.class,
                    () -> world.SetAsync(KEY_BAD, engine.wrapUserdata(new Object())),
                    "a userdata value is not BSON-encodable and must be rejected");
        }
    }

    @Test
    void rejectedWriteKeepsExistingValueAndFile() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();
            world.SetAsync(KEY_KEEP, 7);
            world.Flush();
            File file = new File(tempDir, WORLD_FILE);

            Assertions.assertAll("a rejected write must not destroy previously saved data",
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                    () -> world.SetAsync(KEY_BAD, engine.wrapUserdata(new Object())),
                    "the unsupported value must be rejected before the file is touched"),
                () -> Assertions.assertEquals(7.0, numberOf(world.GetAsync(KEY_KEEP)), 1e-9,
                    "the previously saved value must remain readable"),
                () -> Assertions.assertTrue(file.isFile() && file.length() > 0,
                    "the previous save must remain on disk"));
        }
    }

    @Test
    void tablesAndArraysRoundTripThroughStorage() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();
            world.SetAsync(KEY_PROFILE, profileTable());

            Object readBack = world.GetAsync(KEY_PROFILE);

            Assertions.assertAll("a nested table must survive the BSON round-trip",
                () -> Assertions.assertInstanceOf(ILuaTable.class, readBack, "tables must come back as tables"),
                () -> Assertions.assertEquals("crystal",
                    ((ILuaTable) readBack).rawget("name").asString(), "string fields must round-trip"),
                () -> Assertions.assertTrue(((ILuaTable) readBack).rawget(KEY_LEVELS).isTable(),
                    "nested arrays must come back as tables"),
                () -> Assertions.assertEquals(3,
                    ((ILuaTable) readBack).rawget(KEY_LEVELS).asTable().length(),
                    "the nested array must keep all three elements"),
                () -> Assertions.assertEquals(2.0,
                    ((ILuaTable) readBack).rawget(KEY_LEVELS).asTable().rawget(2).asDouble(), 1e-9,
                    "array elements must keep their index and value"));
        }
    }

    private ILuaTable profileTable() {
        ILuaTable table = engine.createTable();
        table.rawset("name", engine.wrapString("crystal"));
        ILuaTable list = engine.createTable();
        list.rawset(1, engine.wrapNumber(1));
        list.rawset(2, engine.wrapNumber(2));
        list.rawset(3, engine.wrapNumber(3));
        table.rawset(KEY_LEVELS, list);
        return table;
    }
}

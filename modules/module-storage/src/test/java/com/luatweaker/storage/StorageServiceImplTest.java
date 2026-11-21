package com.luatweaker.storage;

import com.luatweaker.api.storage.IRobloxStorageService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

/**
 * World/player/session persistence and explicit flushing.
 */
class StorageServiceImplTest extends StorageTestSupport {

    private static final String KEY_PLAY_COUNT = "play_count";
    private static final String KEY_COINS = "coins";
    private static final String KEY_TEMP = "temp_key";
    private static final String KEY_PERSISTED = "persisted";
    private static final String WORLD_FILE = "world_storage.bson";

    @Test
    void worldStorageStoresAndReadsValues() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();

            world.SetAsync(KEY_PLAY_COUNT, 42);

            Assertions.assertEquals(42.0, numberOf(world.GetAsync(KEY_PLAY_COUNT)), 1e-9,
                    "world storage must return the value that was stored under the key");
        }
    }

    @Test
    void playerStoragesAreIsolatedPerUuid() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore player1 = service.GetPlayerStorage("uuid-1");
            IRobloxStorageService.IDataStore player2 = service.GetPlayerStorage("uuid-2");
            player1.SetAsync(KEY_COINS, 100);
            player2.SetAsync(KEY_COINS, 500);

            Assertions.assertAll("each player store must keep its own values",
                () -> Assertions.assertEquals(100.0, numberOf(player1.GetAsync(KEY_COINS)), 1e-9,
                    "player 1 must read back its own balance"),
                () -> Assertions.assertEquals(500.0, numberOf(player2.GetAsync(KEY_COINS)), 1e-9,
                    "player 2 must read back its own balance"));
        }
    }

    @Test
    void sessionStorageStoresValues() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore session = service.GetSessionStorage();

            session.SetAsync(KEY_TEMP, "temp_val");

            Assertions.assertEquals("temp_val", session.GetAsync(KEY_TEMP),
                    "session storage must return the stored string");
        }
    }

    @Test
    void worldStorageRoundTripsAcrossServiceInstances() {
        try (StorageServiceImpl first = newService()) {
            first.GetWorldStorage().SetAsync(KEY_PERSISTED, "value");
        }

        try (StorageServiceImpl second = newService()) {
            Assertions.assertEquals("value", second.GetWorldStorage().GetAsync(KEY_PERSISTED),
                    "a new service instance must load the value written and flushed by the previous one");
        }
    }

    @Test
    void flushWritesTheWorldFileToDisk() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();
            world.SetAsync(KEY_PERSISTED, "flushed");

            world.Flush();

            File file = new File(tempDir, WORLD_FILE);
            Assertions.assertTrue(file.isFile() && file.length() > 0,
                    "Flush() must commit the world store to disk immediately");
        }
    }

    @Test
    void flushedValueSurvivesReload() {
        try (StorageServiceImpl first = newService()) {
            first.GetWorldStorage().SetAsync("flush_test", "flushed_value");
            first.GetWorldStorage().Flush();
        }

        try (StorageServiceImpl second = newService()) {
            Assertions.assertEquals("flushed_value", second.GetWorldStorage().GetAsync("flush_test"),
                    "a flushed value must be readable by a freshly created service");
        }
    }

    @Test
    void rapidWritesAreCoalescedWithoutLosingKeys() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();
            for (int i = 0; i < 50; i++) {
                world.SetAsync("rapid_key_" + i, i);
            }
            service.Flush();

            for (int i = 0; i < 50; i++) {
                Assertions.assertEquals((double) i, numberOf(world.GetAsync("rapid_key_" + i)), 1e-9,
                        "rapid write " + i + " must survive the debounced flush");
            }
        }
    }
}

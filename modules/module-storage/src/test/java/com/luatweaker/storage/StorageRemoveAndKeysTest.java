package com.luatweaker.storage;

import com.luatweaker.api.storage.IRobloxStorageService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Removal and key-listing semantics of the data stores.
 */
class StorageRemoveAndKeysTest extends StorageTestSupport {

    private static final String KEY_KEEP = "keep";
    private static final String KEY_DROP = "drop";
    private static final String KEY_COINS = "coins";

    @Test
    void removeExistingKeyReportsTrue() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();
            world.SetAsync(KEY_DROP, 2);

            Assertions.assertTrue(world.RemoveAsync(KEY_DROP),
                    "removing an existing key must report true");
        }
    }

    @Test
    void removeMissingKeyReportsFalse() {
        try (StorageServiceImpl service = newService()) {
            Assertions.assertFalse(service.GetWorldStorage().RemoveAsync(KEY_DROP),
                    "removing a key that was never stored must report false");
        }
    }

    @Test
    void removedKeyIsNoLongerReadable() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();
            world.SetAsync(KEY_DROP, 2);
            world.RemoveAsync(KEY_DROP);

            Assertions.assertNull(world.GetAsync(KEY_DROP),
                    "a removed key must read back as absent");
        }
    }

    @Test
    void removalIsPersistedAcrossReload() {
        try (StorageServiceImpl first = newService()) {
            IRobloxStorageService.IDataStore world = first.GetWorldStorage();
            world.SetAsync(KEY_KEEP, 1);
            world.SetAsync(KEY_DROP, 2);
            world.RemoveAsync(KEY_DROP);
        }

        try (StorageServiceImpl second = newService()) {
            Assertions.assertNull(second.GetWorldStorage().GetAsync(KEY_DROP),
                    "the removal must be written to disk so a reload cannot resurrect the key");
        }
    }

    @Test
    void removalKeepsOtherKeys() {
        try (StorageServiceImpl first = newService()) {
            IRobloxStorageService.IDataStore world = first.GetWorldStorage();
            world.SetAsync(KEY_KEEP, 1);
            world.SetAsync(KEY_DROP, 2);
            world.RemoveAsync(KEY_DROP);
        }

        try (StorageServiceImpl second = newService()) {
            Assertions.assertEquals(1.0, numberOf(second.GetWorldStorage().GetAsync(KEY_KEEP)), 1e-9,
                    "removing one key must not delete the remaining keys");
        }
    }

    @Test
    void keysAreReturnedInSortedOrder() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();
            world.SetAsync("zeta", 1);
            world.SetAsync("alpha", 2);
            world.SetAsync("mid", 3);

            Assertions.assertEquals(List.of("alpha", "mid", "zeta"), world.GetKeys(),
                    "GetKeys must return every stored key in sorted order");
        }
    }

    @Test
    void untouchedPlayerStoreHasNoKeys() {
        try (StorageServiceImpl service = newService()) {
            Assertions.assertTrue(service.GetPlayerStorage("uuid-keys").GetKeys().isEmpty(),
                    "a player store that was never written must have no keys");
        }
    }

    @Test
    void playerStoreListsItsStoredKey() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore player = service.GetPlayerStorage("uuid-keys");
            player.SetAsync(KEY_COINS, 5);

            Assertions.assertEquals(List.of(KEY_COINS), player.GetKeys(),
                    "a written player key must be listed by GetKeys");
        }
    }
}

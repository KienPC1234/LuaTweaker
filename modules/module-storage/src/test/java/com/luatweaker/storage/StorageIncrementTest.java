package com.luatweaker.storage;

import com.luatweaker.api.storage.IRobloxStorageService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Numeric increment semantics, including rejection of non-numeric entries.
 */
class StorageIncrementTest extends StorageTestSupport {

    private static final String KEY_COUNTER = "counter";
    private static final String KEY_BALANCE = "balance";
    private static final String KEY_LABEL = "label";

    @Test
    void incrementCreatesAMissingKeyFromZero() {
        try (StorageServiceImpl service = newService()) {
            Assertions.assertEquals(1.0, service.GetWorldStorage().IncrementAsync(KEY_COUNTER, 1.0), 1e-9,
                    "incrementing a missing key must start from zero");
        }
    }

    @Test
    void incrementAccumulatesSuccessiveDeltas() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();
            world.IncrementAsync(KEY_COUNTER, 1.0);

            Assertions.assertEquals(3.5, world.IncrementAsync(KEY_COUNTER, 2.5), 1e-9,
                    "increment must add the delta to the stored value");
        }
    }

    @Test
    void incrementIsVisibleThroughGetAsync() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();
            world.IncrementAsync(KEY_COUNTER, 3.5);

            Assertions.assertEquals(3.5, numberOf(world.GetAsync(KEY_COUNTER)), 1e-9,
                    "the incremented value must be readable with GetAsync");
        }
    }

    @Test
    void sessionIncrementSupportsNegativeDeltas() {
        try (StorageServiceImpl service = newService()) {
            Assertions.assertEquals(-2.0, service.GetSessionStorage().IncrementAsync(KEY_BALANCE, -2.0), 1e-9,
                    "session storage must support negative increments");
        }
    }

    @Test
    void incrementRejectsANonNumericValue() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();
            world.SetAsync(KEY_LABEL, "not a number");

            Assertions.assertThrows(IllegalStateException.class,
                    () -> world.IncrementAsync(KEY_LABEL, 1.0),
                    "incrementing a string must fail loudly instead of overwriting it");
        }
    }

    @Test
    void rejectedIncrementKeepsTheStoredValue() {
        try (StorageServiceImpl service = newService()) {
            IRobloxStorageService.IDataStore world = service.GetWorldStorage();
            world.SetAsync(KEY_LABEL, "not a number");

            Assertions.assertAll("a rejected increment must leave the stored value untouched",
                () -> Assertions.assertThrows(IllegalStateException.class,
                    () -> world.IncrementAsync(KEY_LABEL, 1.0),
                    "incrementing a string must fail loudly"),
                () -> Assertions.assertEquals("not a number", world.GetAsync(KEY_LABEL),
                    "the stored string must remain unchanged"));
        }
    }
}

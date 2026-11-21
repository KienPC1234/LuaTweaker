package com.luatweaker.core.stub;

import com.luatweaker.api.client.ICameraService;
import com.luatweaker.api.loot.ILootService;
import com.luatweaker.core.lsp.LtvmStubGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the static stub catalog bridging bound services to autocomplete:
 * registration idempotency, snapshot contents, and generator merge coverage.
 */
class StubCatalogTest {

    private static final String LOOT = "Loot";
    private static final String CAMERA = "Camera";
    private static final String LOOT_REQUIRE = "'LuaTweaker.Loot'";
    private static final String CAMERA_REQUIRE = "'LuaTweaker.Camera'";
    private static final String ADD_MOB_DROP = "function Loot:AddMobDrop(";
    private static final String ADD_MOB_DROP_ALIAS = "function Loot:addMobDrop(";

    @BeforeEach
    void clearCatalog() {
        StubCatalog.clearForTests();
    }

    @Nested
    class Registration {

        @Test
        void registerIsIdempotentLoot() {
            StubCatalog.register(LOOT, ILootService.class);
            StubCatalog.register(LOOT, ILootService.class);
            Map<String, Class<?>> snap = StubCatalog.snapshot();
            assertEquals(ILootService.class, snap.get(LOOT),
                    "registering the same service twice must keep the original class");
        }

        @Test
        void registerIsIdempotentDuplicateRegisterMustNotDuplicateEntries() {
            StubCatalog.register(LOOT, ILootService.class);
            StubCatalog.register(LOOT, ILootService.class);
            Map<String, Class<?>> snap = StubCatalog.snapshot();
            assertEquals(1, snap.size(), "Duplicate register must not duplicate entries");
        }
    }

    @Nested
    class Snapshot {

        @Test
        void snapshotContainsRegisteredEntriesLoot() {
            StubCatalog.register(LOOT, ILootService.class);
            StubCatalog.register(CAMERA, ICameraService.class);
            Map<String, Class<?>> snap = StubCatalog.snapshot();
            assertEquals(ILootService.class, snap.get(LOOT),
                    "the snapshot must carry the Loot service class");
        }

        @Test
        void snapshotContainsRegisteredEntriesCamera() {
            StubCatalog.register(LOOT, ILootService.class);
            StubCatalog.register(CAMERA, ICameraService.class);
            Map<String, Class<?>> snap = StubCatalog.snapshot();
            assertEquals(ICameraService.class, snap.get(CAMERA),
                    "the snapshot must carry the Camera service class");
        }

        @Test
        void snapshotContainsRegisteredEntriesSnap() {
            StubCatalog.register(LOOT, ILootService.class);
            StubCatalog.register(CAMERA, ICameraService.class);
            Map<String, Class<?>> snap = StubCatalog.snapshot();
            assertEquals(2, snap.size(),
                    "the snapshot must contain exactly the two registered entries");
        }
    }

    @Nested
    class GeneratorMerge {

        @Test
        void generatorMergeIncludesCatalogEntriesLuaTweakerLoot() {
            StubCatalog.register(LOOT, ILootService.class);
            LtvmStubGenerator generator = new LtvmStubGenerator();
            String stubs = generator.getResult();
            assertTrue(stubs.contains(LOOT_REQUIRE),
                    "Merged stubs must include the catalog require overload:\n" + stubs);
        }

        @Test
        void generatorMergeIncludesCatalogEntriesFunctionLootAddMobDrop() {
            StubCatalog.register(LOOT, ILootService.class);
            LtvmStubGenerator generator = new LtvmStubGenerator();
            String stubs = generator.getResult();
            assertTrue(stubs.contains(ADD_MOB_DROP) || stubs.contains(ADD_MOB_DROP_ALIAS),
                    "Merged stubs must include catalog service methods:\n" + stubs);
        }

        @Test
        void generatorMergeIsIdempotentNoDupesLuaTweakerCamera() {
            StubCatalog.register(CAMERA, ICameraService.class);
            LtvmStubGenerator generator = new LtvmStubGenerator();
            String first = generator.getResult();
            assertEquals(1, countOccurrences(first, CAMERA_REQUIRE),
                    "the first getResult() must emit the catalog overload exactly once");
        }

        @Test
        void generatorMergeIsIdempotentNoDupesLuaTweakerCamera2() {
            StubCatalog.register(CAMERA, ICameraService.class);
            LtvmStubGenerator generator = new LtvmStubGenerator();
            String second = generator.getResult();
            assertEquals(1, countOccurrences(second, CAMERA_REQUIRE),
                    "Second getResult() must not duplicate catalog overloads");
        }

        @Test
        void instanceRegistrationWinsOverCatalogLuaTweakerLoot() {
            StubCatalog.register(LOOT, ICameraService.class);
            LtvmStubGenerator generator = new LtvmStubGenerator();
            generator.registerService(LOOT, ILootService.class);
            String stubs = generator.getResult();
            assertEquals(1, countOccurrences(stubs, LOOT_REQUIRE),
                    "Same name in instance + catalog must emit exactly one overload:\n" + stubs);
        }

        @Test
        void instanceRegistrationWinsOverCatalogFunctionLootAddMobDrop() {
            StubCatalog.register(LOOT, ICameraService.class);
            LtvmStubGenerator generator = new LtvmStubGenerator();
            generator.registerService(LOOT, ILootService.class);
            String stubs = generator.getResult();
            assertTrue(stubs.contains(ADD_MOB_DROP) || stubs.contains(ADD_MOB_DROP_ALIAS),
                    "Instance registration must win over catalogue entry:\n" + stubs);
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int found = haystack.indexOf(needle, from);
            if (found == -1) {
                break;
            }
            count++;
            from = found + needle.length();
        }
        return count;
    }
}

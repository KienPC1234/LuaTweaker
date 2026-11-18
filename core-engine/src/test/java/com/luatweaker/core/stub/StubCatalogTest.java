package com.luatweaker.core.stub;

import com.luatweaker.api.client.ICameraService;
import com.luatweaker.api.loot.ILootService;
import com.luatweaker.core.lsp.LtvmStubGenerator;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the static stub catalog bridging bound services to autocomplete:
 * registration idempotency, snapshot contents, and generator merge coverage.
 */
public class StubCatalogTest {

    @BeforeEach
    public void clearCatalog() {
        StubCatalog.clearForTests();
    }

    @Test
    public void registerIsIdempotent() {
        StubCatalog.register("Loot", ILootService.class);
        StubCatalog.register("Loot", ILootService.class);

        Map<String, Class<?>> snap = StubCatalog.snapshot();
        assertEquals(ILootService.class, snap.get("Loot"));
        assertEquals(1, snap.size(), "Duplicate register must not duplicate entries");
    }

    @Test
    public void snapshotContainsRegisteredEntries() {
        StubCatalog.register("Loot", ILootService.class);
        StubCatalog.register("Camera", ICameraService.class);

        Map<String, Class<?>> snap = StubCatalog.snapshot();
        assertEquals(ILootService.class, snap.get("Loot"));
        assertEquals(ICameraService.class, snap.get("Camera"));
        assertEquals(2, snap.size());
    }

    @Test
    public void generatorMergeIncludesCatalogEntries() {
        StubCatalog.register("Loot", ILootService.class);

        LtvmStubGenerator generator = new LtvmStubGenerator();
        String stubs = generator.getResult();

        assertTrue(stubs.contains("'LuaTweaker.Loot'"),
                "Merged stubs must include catalog require overload:\n" + stubs);
        assertTrue(stubs.contains("function Loot:AddMobDrop(") || stubs.contains("function Loot:addMobDrop("),
                "Merged stubs must include catalog service methods:\n" + stubs);
    }

    @Test
    public void generatorMergeIsIdempotentNoDupes() {
        StubCatalog.register("Camera", ICameraService.class);

        LtvmStubGenerator generator = new LtvmStubGenerator();
        String first = generator.getResult();
        String second = generator.getResult();

        assertEquals(countOccurrences(first, "'LuaTweaker.Camera'"), 1);
        assertEquals(countOccurrences(second, "'LuaTweaker.Camera'"), 1,
                "Second getResult() must not duplicate catalog overloads");
    }

    @Test
    public void instanceRegistrationWinsOverCatalog() {
        StubCatalog.register("Loot", ICameraService.class);

        LtvmStubGenerator generator = new LtvmStubGenerator();
        generator.registerService("Loot", ILootService.class);
        String stubs = generator.getResult();

        assertEquals(1, countOccurrences(stubs, "'LuaTweaker.Loot'"),
                "Same name in instance + catalog must emit exactly one overload:\n" + stubs);
        assertTrue(stubs.contains("function Loot:AddMobDrop(") || stubs.contains("function Loot:addMobDrop("),
                "Instance registration must win over catalog entry:\n" + stubs);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while ((from = haystack.indexOf(needle, from)) != -1) {
            count++;
            from += needle.length();
        }
        return count;
    }
}

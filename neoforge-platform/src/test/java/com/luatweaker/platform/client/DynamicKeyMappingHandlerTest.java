package com.luatweaker.platform.client;

import com.luatweaker.api.client.IKeyBindService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reload-diff for keybinds added after {@code RegisterKeyMappingsEvent}.
 * The mapping construction needs Minecraft classes; the "which entries lack a
 * live mapping" diff is pure and covered here.
 */
public class DynamicKeyMappingHandlerTest {

    private static IKeyBindService.KeyBindEntry entry(String id) {
        return new IKeyBindService.KeyBindEntry(id, id, "LuaTweaker", 32, "payload:" + id, "key", "none");
    }

    @Test
    void collectUnsynced_allLiveYieldsNothing() {
        List<IKeyBindService.KeyBindEntry> result = DynamicKeyMappingHandler.collectUnsynced(
                List.of(entry("a"), entry("b")), Set.of("a", "b"));
        assertTrue(result.isEmpty());
    }

    @Test
    void collectUnsynced_returnsOnlyMissingIdsInOrder() {
        List<IKeyBindService.KeyBindEntry> result = DynamicKeyMappingHandler.collectUnsynced(
                List.of(entry("a"), entry("b"), entry("c")), Set.of("b"));
        assertEquals(List.of("a", "c"),
                result.stream().map(IKeyBindService.KeyBindEntry::id).toList());
    }

    @Test
    void collectUnsynced_skipsNullEntriesAndNullIds() {
        List<IKeyBindService.KeyBindEntry> withNulls = new java.util.ArrayList<>();
        withNulls.add(entry("a"));
        withNulls.add(null);
        withNulls.add(new IKeyBindService.KeyBindEntry(null, "x", "y", 32, "p", "key", "none"));
        List<IKeyBindService.KeyBindEntry> result = DynamicKeyMappingHandler.collectUnsynced(
                withNulls, Set.of());
        assertEquals(List.of("a"), result.stream().map(IKeyBindService.KeyBindEntry::id).toList());
    }
}

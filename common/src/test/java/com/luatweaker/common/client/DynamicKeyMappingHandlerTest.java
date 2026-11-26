package com.luatweaker.common.client;

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
class DynamicKeyMappingHandlerTest {

    private static final String PAYLOAD_ONE = "cast:1";
    private static final String PAYLOAD_TWO = "cast:2";

    private static IKeyBindService.KeyBindEntry entry(String id) {
        return new IKeyBindService.KeyBindEntry(id, id, "LuaTweaker", 32, "payload:" + id, "key", "none");
    }

    @Test
    void collectUnsyncedAllLiveYieldsNothing() {
        List<IKeyBindService.KeyBindEntry> result = DynamicKeyMappingHandler.collectUnsynced(
                List.of(entry("a"), entry("b")), Set.of("a", "b"));
        assertTrue(result.isEmpty(),
                "keybinds that all have live mappings need no resync");
    }

    @Test
    void collectUnsyncedReturnsOnlyMissingIdsInOrder() {
        List<IKeyBindService.KeyBindEntry> result = DynamicKeyMappingHandler.collectUnsynced(
                List.of(entry("a"), entry("b"), entry("c")), Set.of("b"));
        assertEquals(List.of("a", "c"),
                result.stream().map(IKeyBindService.KeyBindEntry::id).toList(),
                "only keybinds without a live mapping must resync, keeping registration order");
    }

    @Test
    void collectUnsyncedSkipsNullEntriesAndNullIds() {
        List<IKeyBindService.KeyBindEntry> withNulls = new java.util.ArrayList<>();
        withNulls.add(entry("a"));
        withNulls.add(null);
        withNulls.add(new IKeyBindService.KeyBindEntry(null, "x", "y", 32, "p", "key", "none"));
        List<IKeyBindService.KeyBindEntry> result = DynamicKeyMappingHandler.collectUnsynced(
                withNulls, Set.of());
        assertEquals(List.of("a"), result.stream().map(IKeyBindService.KeyBindEntry::id).toList(),
                "null entries and null ids must never reach the mapping sync");
    }

    @Test
    void parseModifierResolvesExpectedEnumVariants() {
        assertEquals(List.of(
                        DynamicKeyMappingHandler.KeyModifier.NONE,
                        DynamicKeyMappingHandler.KeyModifier.NONE,
                        DynamicKeyMappingHandler.KeyModifier.SHIFT,
                        DynamicKeyMappingHandler.KeyModifier.CTRL,
                        DynamicKeyMappingHandler.KeyModifier.ALT),
                List.of(
                        DynamicKeyMappingHandler.parseModifier(null),
                        DynamicKeyMappingHandler.parseModifier("none"),
                        DynamicKeyMappingHandler.parseModifier(" Shift "),
                        DynamicKeyMappingHandler.parseModifier("CTRL"),
                        DynamicKeyMappingHandler.parseModifier("alt")),
                "parseModifier must normalize whitespace and casing into enum values");
    }

    @Test
    void shouldRefreshExistingDetectsPayloadAndModifierChanges() {
        assertEquals(
                List.of(false, true, true, true),
                List.of(
                        DynamicKeyMappingHandler.shouldRefreshExisting(
                                PAYLOAD_ONE, DynamicKeyMappingHandler.KeyModifier.SHIFT,
                                PAYLOAD_ONE, DynamicKeyMappingHandler.KeyModifier.SHIFT),
                        DynamicKeyMappingHandler.shouldRefreshExisting(
                                PAYLOAD_ONE, DynamicKeyMappingHandler.KeyModifier.NONE,
                                PAYLOAD_TWO, DynamicKeyMappingHandler.KeyModifier.NONE),
                        DynamicKeyMappingHandler.shouldRefreshExisting(
                                PAYLOAD_ONE, DynamicKeyMappingHandler.KeyModifier.NONE,
                                PAYLOAD_ONE, DynamicKeyMappingHandler.KeyModifier.CTRL),
                        DynamicKeyMappingHandler.shouldRefreshExisting(
                                null, DynamicKeyMappingHandler.KeyModifier.NONE,
                                PAYLOAD_ONE, DynamicKeyMappingHandler.KeyModifier.NONE)),
                "hot-reload must refresh existing keybind states whenever payload or modifier changes");
    }
}

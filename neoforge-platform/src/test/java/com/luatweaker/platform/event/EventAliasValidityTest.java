package com.luatweaker.platform.event;

import net.neoforged.bus.api.Event;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every registered event alias must point at a real NeoForge event class. */
class EventAliasValidityTest {

    private static Map<String, Class<? extends Event>> aliases() {
        return EventAliasRegistry.getAllAliases();
    }

    @Test
    void aliasMapIsNotEmpty() {
        assertFalse(aliases().isEmpty(), "Aliases map should not be empty");
    }

    @Test
    void everyAliasKeyIsNotNull() {
        for (Map.Entry<String, Class<? extends Event>> entry : aliases().entrySet()) {
            assertNotNull(entry.getKey(), "Alias key must not be null");
        }
    }

    @Test
    void everyAliasedClassIsNotNull() {
        for (Map.Entry<String, Class<? extends Event>> entry : aliases().entrySet()) {
            assertNotNull(entry.getValue(),
                    "Event class for '" + entry.getKey() + "' must not be null");
        }
    }

    @Test
    void everyAliasedClassExtendsEvent() {
        for (Map.Entry<String, Class<? extends Event>> entry : aliases().entrySet()) {
            assertTrue(Event.class.isAssignableFrom(entry.getValue()),
                    "Event class for '" + entry.getKey() + "' must extend net.neoforged.bus.api.Event");
        }
    }
}

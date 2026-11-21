package com.luatweaker.core.service;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class AppliedEventHelperTest {

    private static final String EVENTS_SERVICE = "Events";
    private static final String PORTAL_EVENT = "PortalUsed";
    private static final String TEST_EVENT = "TestEvent";
    private static final String BLOCK_ID = "blockId";
    private static final String ORES = "ores";
    private static final String OK = "ok";
    private static final String DIMENSIONS = "dimensions";
    private static final String ARCANE_PORTAL = "arcane:portal";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String ARCANE_REALM = "arcane:realm";

    @AfterAll
    static void stopLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @AfterEach
    void clearRegistry() {
        LuaServiceRegistry.clear();
    }

    private static final class CapturingEvents implements IEventService {
        final ILuaEngine engine = LuaEngineFactory.create(false);
        final List<String> names = new ArrayList<>();
        final List<ILuaTable> payloads = new ArrayList<>();

        @Override public void listen(String eventName, Object callback) {}

        @Override public void post(String eventName, ILuaTable payload) {}

        @Override public void fireEvent(String eventName, ILuaTable payload) {
            names.add(eventName);
            payloads.add(payload);
        }

        @Override public boolean fireCancellable(String eventName, ILuaTable payload) { return true; }

        @Override public ILuaEngine getEngine() { return engine; }
    }

    /** Fires {@code eventName} with {@code fields} against a bus registered as the Events service. */
    private static CapturingEvents fireWith(String eventName, Map<String, Object> fields) {
        CapturingEvents events = new CapturingEvents();
        LuaServiceRegistry.register(EVENTS_SERVICE, events);
        AppliedEventHelper.fire(eventName, fields);
        return events;
    }

    private static Map<String, Object> portalFields() {
        Map<String, Object> fields = new ConcurrentHashMap<>();
        fields.put(BLOCK_ID, ARCANE_PORTAL);
        fields.put(ORES, 3);
        fields.put(OK, true);
        fields.put(DIMENSIONS, List.of(OVERWORLD, ARCANE_REALM));
        return fields;
    }

    private static Map<String, Object> mixedTypeFields() {
        Map<String, Object> fields = new ConcurrentHashMap<>();
        fields.put(OK, true);
        fields.put("raw", new Object());
        return fields;
    }

    @Nested
    class FlatPayload {

        @Test
        void fireBuildsFlatPayloadPortalUsed() {
            CapturingEvents events = fireWith(PORTAL_EVENT, portalFields());
            assertEquals(List.of(PORTAL_EVENT), events.names,
                    "the event must reach the bus under the requested name");
        }

        @Test
        void fireBuildsFlatPayloadBlockId() {
            CapturingEvents events = fireWith(PORTAL_EVENT, portalFields());
            ILuaTable payload = events.payloads.get(0);
            assertEquals(ARCANE_PORTAL, payload.rawget(BLOCK_ID).asString(),
                    "a String field must be lifted onto the Lua payload as a string");
        }

        @Test
        void fireBuildsFlatPayloadOres() {
            CapturingEvents events = fireWith(PORTAL_EVENT, portalFields());
            ILuaTable payload = events.payloads.get(0);
            assertEquals(3, payload.rawget(ORES).asInt(),
                    "an Integer field must be lifted onto the Lua payload as a number");
        }

        @Test
        void fireBuildsFlatPayloadOk() {
            CapturingEvents events = fireWith(PORTAL_EVENT, portalFields());
            ILuaTable payload = events.payloads.get(0);
            assertTrue(payload.rawget(OK).asBoolean(),
                    "a Boolean field must be lifted onto the Lua payload as a boolean");
        }

        @Test
        void fireBuildsFlatPayloadMinecraftOverworld() {
            CapturingEvents events = fireWith(PORTAL_EVENT, portalFields());
            ILuaTable payload = events.payloads.get(0);
            ILuaTable dims = payload.rawget(DIMENSIONS).asTable();
            assertEquals(OVERWORLD, dims.rawget(1).asString(),
                    "a List field must become a 1-based Lua table with its first element intact");
        }

        @Test
        void fireBuildsFlatPayloadArcaneRealm() {
            CapturingEvents events = fireWith(PORTAL_EVENT, portalFields());
            ILuaTable payload = events.payloads.get(0);
            ILuaTable dims = payload.rawget(DIMENSIONS).asTable();
            assertEquals(ARCANE_REALM, dims.rawget(2).asString(),
                    "a List field must keep its second element at index 2");
        }
    }

    @Nested
    class UnsupportedTypes {

        @Test
        void fireSkipsUnsupportedTypesButKeepsTheRestTestEvent() {
            CapturingEvents events = fireWith(TEST_EVENT, mixedTypeFields());
            assertEquals(List.of(TEST_EVENT), events.names,
                    "an unsupported value must not prevent the event from being fired");
        }

        @Test
        void fireSkipsUnsupportedTypesButKeepsTheRestOk() {
            CapturingEvents events = fireWith(TEST_EVENT, mixedTypeFields());
            assertTrue(events.payloads.get(0).rawget(OK).asBoolean(),
                    "supported fields next to an unsupported value must survive");
        }

        @Test
        void fireSkipsUnsupportedTypesButKeepsTheRestRaw() {
            CapturingEvents events = fireWith(TEST_EVENT, mixedTypeFields());
            assertTrue(events.payloads.get(0).rawget("raw").isNil(),
                    "an unsupported value must be dropped instead of crashing the bridge");
        }
    }

    @Nested
    class DefensiveCalls {

        @Test
        void fireWithoutBusOrBlankNameNeverThrowsNoBus() {
            CapturingEvents events = new CapturingEvents();
            LuaServiceRegistry.register(EVENTS_SERVICE, events);
            assertDoesNotThrow(() -> AppliedEventHelper.fire("NoBus", Map.of("a", 1)),
                    "firing an event nobody listens to must not throw");
        }

        @Test
        void fireWithoutBusOrBlankNameNeverThrowsA() {
            CapturingEvents events = new CapturingEvents();
            LuaServiceRegistry.register(EVENTS_SERVICE, events);
            assertDoesNotThrow(() -> AppliedEventHelper.fire("  ", Map.of("a", 1)),
                    "firing a blank event name must not throw");
        }

        @Test
        void fireWithoutBusOrBlankNameNeverThrowsA2() {
            CapturingEvents events = new CapturingEvents();
            LuaServiceRegistry.register(EVENTS_SERVICE, events);
            assertDoesNotThrow(() -> AppliedEventHelper.fire(null, Map.of("a", 1)),
                    "firing a null event name must not throw");
        }

        @Test
        void fireWithoutBusOrBlankNameNeverThrowsNames() {
            CapturingEvents events = new CapturingEvents();
            LuaServiceRegistry.register(EVENTS_SERVICE, events);
            assertTrue(events.names.isEmpty(),
                    "registering the service alone must not fire any event");
        }
    }
}

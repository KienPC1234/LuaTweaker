package com.luatweaker.core.service;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AppliedEventHelperTest {
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

    @Test
    void fireBuildsFlatPayload() {
        CapturingEvents events = new CapturingEvents();
        LuaServiceRegistry.register("Events", events);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("blockId", "arcane:portal");
        fields.put("ores", 3);
        fields.put("ok", true);
        fields.put("dimensions", List.of("minecraft:overworld", "arcane:realm"));
        AppliedEventHelper.fire("PortalUsed", fields);

        assertEquals(List.of("PortalUsed"), events.names);
        ILuaTable payload = events.payloads.get(0);
        assertEquals("arcane:portal", payload.rawget("blockId").asString());
        assertEquals(3, payload.rawget("ores").asInt());
        assertTrue(payload.rawget("ok").asBoolean());
        ILuaTable dims = payload.rawget("dimensions").asTable();
        assertEquals("minecraft:overworld", dims.rawget(1).asString());
        assertEquals("arcane:realm", dims.rawget(2).asString());
    }

    @Test
    void fireSkipsUnsupportedTypesButKeepsTheRest() {
        CapturingEvents events = new CapturingEvents();
        LuaServiceRegistry.register("Events", events);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ok", true);
        fields.put("raw", new Object());
        AppliedEventHelper.fire("TestEvent", fields);

        assertEquals(List.of("TestEvent"), events.names);
        assertTrue(events.payloads.get(0).rawget("ok").asBoolean());
        assertTrue(events.payloads.get(0).rawget("raw").isNil());
    }

    @Test
    void fireWithoutBusOrBlankNameNeverThrows() {
        assertDoesNotThrow(() -> AppliedEventHelper.fire("NoBus", Map.of("a", 1)));
        CapturingEvents events = new CapturingEvents();
        LuaServiceRegistry.register("Events", events);
        assertDoesNotThrow(() -> AppliedEventHelper.fire("  ", Map.of("a", 1)));
        assertDoesNotThrow(() -> AppliedEventHelper.fire(null, Map.of("a", 1)));
        assertTrue(events.names.isEmpty());
    }
}

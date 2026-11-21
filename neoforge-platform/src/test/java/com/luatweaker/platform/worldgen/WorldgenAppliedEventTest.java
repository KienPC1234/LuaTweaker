package com.luatweaker.platform.worldgen;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Applying worldgen must fire WorldgenApplied with ore/removal counts. */
class WorldgenAppliedEventTest {

    private static final String APPLIED_EVENT = "WorldgenApplied";

    private record EventCapture(List<String> fired, List<ILuaTable> payloads) {
    }

    private static EventCapture captureApply() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        List<String> fired = new ArrayList<>();
        List<ILuaTable> payloads = new ArrayList<>();
        LuaServiceRegistry.register("Events", new IEventService() {
            @Override
            public void listen(String eventName, Object callback) {
            }

            @Override
            public void post(String eventName, ILuaTable payload) {
            }

            @Override
            public void fireEvent(String eventName, ILuaTable payload) {
                fired.add(eventName);
                payloads.add(payload);
            }

            @Override
            public boolean fireCancellable(String eventName, ILuaTable payload) {
                return true;
            }

            @Override
            public ILuaEngine getEngine() {
                return engine;
            }
        });
        try {
            WorldgenServiceImpl service = new WorldgenServiceImpl();
            service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 10);
            service.removeOre("minecraft:coal_ore", "minecraft:overworld");
            WorldgenFixtures.apply(service);
        } finally {
            LuaServiceRegistry.clear();
        }
        return new EventCapture(fired, payloads);
    }

    private static ILuaTable appliedPayload(EventCapture capture) {
        return capture.payloads().get(capture.fired().indexOf(APPLIED_EVENT));
    }

    @Test
    void worldgenAppliedFires() {
        assertTrue(captureApply().fired().contains(APPLIED_EVENT),
                "WorldgenApplied must fire: " + captureApply().fired());
    }

    @Test
    void payloadCountsOres() {
        assertEquals(1, appliedPayload(captureApply()).rawget("ores").asInt(),
                "the payload must count the registered ore");
    }

    @Test
    void payloadCountsRemovals() {
        assertEquals(1, appliedPayload(captureApply()).rawget("removals").asInt(),
                "the payload must count the registered removal");
    }

    @AfterEach
    void clearEventBus() {
        LuaServiceRegistry.clear();
    }
}

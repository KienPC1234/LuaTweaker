package com.luatweaker.platform.structures;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.service.LuaServiceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Applying structures must fire StructuresApplied with applied/skipped counts. */
class StructureAppliedEventTest {

    private static final String APPLIED_EVENT = "StructuresApplied";

    private record EventCapture(List<String> fired, List<ILuaTable> payloads) {
    }

    private static EventCapture captureApply(Path tempDir) throws IOException {
        StructureEmissionFixture.EmissionContext context =
                StructureEmissionFixture.prepare("mymod", tempDir);
        StructureEmissionFixture.writeNbt(context.dataRoot(), "mymod", "structures/kept");
        context.service().register("mymod:ghost",
                StructureEmissionFixture.config(context.engine(), "mymod:structures/ghost",
                        new String[] {"minecraft:plains"}, 32, 8));
        context.service().register("mymod:kept",
                StructureEmissionFixture.config(context.engine(), "mymod:structures/kept",
                        new String[] {"minecraft:plains"}, 32, 8));

        List<String> fired = new ArrayList<>();
        List<ILuaTable> payloads = new ArrayList<>();
        ILuaEngine engine = context.engine();
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
            StructureEmissionFixture.apply(context);
        } finally {
            LuaServiceRegistry.clear();
        }
        return new EventCapture(fired, payloads);
    }

    private static ILuaTable appliedPayload(EventCapture capture) {
        return capture.payloads().get(capture.fired().indexOf(APPLIED_EVENT));
    }

    @Test
    void structuresAppliedFires(@TempDir Path tempDir) throws IOException {
        assertTrue(captureApply(tempDir).fired().contains(APPLIED_EVENT),
                "StructuresApplied must fire: " + captureApply(tempDir).fired());
    }

    @Test
    void payloadCountsApplied(@TempDir Path tempDir) throws IOException {
        assertEquals(1, appliedPayload(captureApply(tempDir)).rawget("applied").asInt(),
                "exactly one registration must count as applied");
    }

    @Test
    void payloadCountsSkipped(@TempDir Path tempDir) throws IOException {
        assertEquals(1, appliedPayload(captureApply(tempDir)).rawget("skipped").asInt(),
                "exactly one registration must count as skipped");
    }
}
